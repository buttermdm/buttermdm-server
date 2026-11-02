#!/usr/bin/env python3
# ButterMDM server regression test: Device Profile plugin (device profiles and reports, capability resolution,
# catalogs, configuration check, application version selection, enrollment scripts, permissions).
# Runs against a live server with a disposable database, after server-regression.sh (it uses the devices
# DRTEST1/DRTEST2 and the user drlimited created there). It changes the configuration of DRTEST2 temporarily and
# adds a test application, and removes both at the end.
#
# Usage: BASE=http://127.0.0.1:8090 SECRET=<hash.secret> PSQL="psql -h 127.0.0.1 -p 5432 -U buttermdm buttermdm" \
#        ADMIN_USER=admin ADMIN_PASS=admin python3 buttermdm/deviceprofile-regression.py
import hashlib, json, os, shlex, subprocess, sys, urllib.error, urllib.request

BASE = os.environ.get("BASE", "http://127.0.0.1:8090")
SECRET = os.environ["SECRET"]
PSQL = shlex.split(os.environ["PSQL"])
ADMIN_USER, ADMIN_PASS = os.environ.get("ADMIN_USER", "admin"), os.environ.get("ADMIN_PASS", "admin")
DEVICE = "DRTEST2"
TEST_PKG = "com.buttermdm.regression.selection"
fails = 0


def check(name, actual, expected):
    global fails
    if actual == expected:
        print("PASS  " + name)
    else:
        fails += 1
        print(f"FAIL  {name} (expected {expected!r}, got {actual!r})")


def sql(query):
    return subprocess.run(PSQL + ["-At", "-c", query], check=True, capture_output=True, text=True).stdout.strip()


def http(method, path, body=None, headers=None, raw=False):
    data = None
    if body is not None:
        data = body.encode() if isinstance(body, str) else json.dumps(body).encode()
    request = urllib.request.Request(BASE + path, method=method, data=data,
                                     headers={"Content-Type": "application/json", **(headers or {})})
    try:
        with urllib.request.urlopen(request) as response:
            text = response.read().decode()
            return response.status, (text if raw else (json.loads(text) if text else None)), dict(response.headers)
    except urllib.error.HTTPError as e:
        return e.code, None, {}


def signature(number):
    return hashlib.sha1((SECRET + number).encode()).hexdigest()


def device(method, path, body=None, number=DEVICE, signed=True, raw=False):
    return http(method, path, body, {"X-Request-Signature": signature(number)} if signed else {}, raw)


def token(login, password):
    md5 = hashlib.md5(password.encode()).hexdigest().upper()
    return http("POST", "/rest/public/jwt/login", {"login": login, "password": md5})[1]["id_token"]


def private(tok, method, path, body=None):
    status, data, _ = http(method, "/rest/plugins/deviceprofile/private" + path, body,
                           {"Authorization": "Bearer " + tok})
    return data


def capability(details, cap_id):
    for item in details["data"]["capabilities"]:
        if item["resolved"]["id"] == cap_id:
            return item["resolved"]
    return None


ADMIN = token(ADMIN_USER, ADMIN_PASS)
device_id = int(sql(f"SELECT id FROM devices WHERE number = '{DEVICE}'"))
config_id = int(sql(f"SELECT configurationId FROM devices WHERE id = {device_id}"))
config_restrictions = sql(f"SELECT COALESCE(restrictions, '<null>') FROM configurations WHERE id = {config_id}")

profile = {"profileVersion": 1, "sdkInt": 29, "release": "10", "securityPatch": "2023-05-01",
           "manufacturer": "Elo Touch Solutions", "brand": "Elo", "model": "Test", "fingerprint": "test/elo/29:1",
           "supportedAbis": ["arm64-v8a", "armeabi-v7a"], "deviceOwner": True, "writeSecureSettings": False,
           "accessibilityServiceEnabled": False, "screenCaptureDisabled": False, "resetPasswordTokenActive": True,
           "platformSigned": False, "bluetoothConnectGranted": True, "agentVersionCode": 15392, "firstReport": True}

print("== device endpoints")
check("unsigned profile rejected", device("POST", f"/rest/plugins/deviceprofile/public/{DEVICE}/profile", profile,
                                          signed=False)[0], 403)
check("unknown device rejected", device("POST", "/rest/plugins/deviceprofile/public/NOSUCHDEVICE/profile", profile,
                                        number="NOSUCHDEVICE")[0], 403)
check("profile without sdkInt rejected", device("POST", f"/rest/plugins/deviceprofile/public/{DEVICE}/profile",
                                                {"release": "10"})[0], 400)
pushes_before = int(sql(f"SELECT COUNT(*) FROM pushmessages WHERE deviceId = {device_id}"))
status, data, _ = device("POST", f"/rest/plugins/deviceprofile/public/{DEVICE}/profile", profile)
check("first profile accepted", status, 200)
catalog_version = data["data"]["catalogVersion"]
check("configuration update pushed after the first profile",
      int(sql(f"SELECT COUNT(*) FROM pushmessages WHERE deviceId = {device_id}")) > pushes_before, True)
check("first profile in the history", sql(f"SELECT eventType FROM plugin_deviceprofile_history WHERE deviceId = "
                                          f"{device_id} ORDER BY id DESC LIMIT 1"), "firstProfile")

details = private(ADMIN, "GET", f"/devices/{device_id}")
check("device details", details["status"], "OK")
check("API 29: location needs the user", capability(details, "location.toggle")["missing"], ["user"])
check("API 29: kiosk features supported", capability(details, "kiosk.lockTaskFeatures")["strategy"],
      "LockTaskFeaturesApi28")
check("API 29: remote control needs ADB", capability(details, "remote.input.accessibility")["state"], "requires")

status, body, headers = device("GET", f"/rest/plugins/deviceprofile/public/{DEVICE}/catalog/{catalog_version}",
                               raw=True)
check("catalog downloaded", status, 200)
check("catalog signed", headers.get("X-Response-Signature", "").lower(),
      hashlib.sha1((SECRET + "".join(body.split())).encode()).hexdigest())
check("unknown catalog version", device("GET", f"/rest/plugins/deviceprofile/public/{DEVICE}/catalog/1999.01.1")[0],
      404)
check("unsigned catalog request rejected",
      device("GET", f"/rest/plugins/deviceprofile/public/{DEVICE}/catalog/{catalog_version}", signed=False)[0], 403)

report = {"catalogVersion": catalog_version,
          "results": [{"capabilityId": "kiosk.lockTask", "outcome": "applied", "strategyId": "LockTaskBasicApi26"},
                      {"capabilityId": "wifi.toggle", "outcome": "failed", "detail": "regression"},
                      {"capabilityId": "x", "outcome": "invalid-outcome"}],
          "setup": [{"stepId": "deviceOwner", "state": "done"},
                    {"stepId": "writeSecureSettings", "state": "pending-adb"}]}
check("report accepted", device("POST", f"/rest/plugins/deviceprofile/public/{DEVICE}/report", report)[0], 200)
check("unsigned report rejected", device("POST", f"/rest/plugins/deviceprofile/public/{DEVICE}/report", report,
                                         signed=False)[0], 403)
check("invalid outcome ignored", sql(f"SELECT COUNT(*) FROM plugin_deviceprofile_results WHERE deviceId = "
                                     f"{device_id}"), "2")
check("rule mismatch recorded", sql(f"SELECT COUNT(*) FROM plugin_deviceprofile_history WHERE deviceId = "
                                    f"{device_id} AND eventType = 'ruleMismatch'") != "0", True)
details = private(ADMIN, "GET", f"/devices/{device_id}")
check("failed capability becomes unsupported", capability(details, "wifi.toggle")["evidence"], "result:failed")
setup = {s["definition"]["id"]: (s["state"] or {}).get("state") for s in details["data"]["setup"]}
check("setup states stored", (setup["deviceOwner"], setup["writeSecureSettings"]), ("done", "pending-adb"))

upgraded = dict(profile, sdkInt=30, release="11", fingerprint="test/elo/30:1", firstReport=False)
check("upgraded profile accepted", device("POST", f"/rest/plugins/deviceprofile/public/{DEVICE}/profile",
                                          upgraded)[0], 200)
check("system update in the history", sql(f"SELECT eventType FROM plugin_deviceprofile_history WHERE deviceId = "
                                          f"{device_id} ORDER BY id DESC LIMIT 1"), "osUpgraded")
details = private(ADMIN, "GET", f"/devices/{device_id}")
check("API 30: location switched silently", capability(details, "location.toggle")["strategy"], "LocationEnabledApi30")
check("results kept after an update", capability(details, "wifi.toggle")["evidence"], "result:failed")

status, data, _ = device("GET", f"/rest/public/sync/configuration/{DEVICE}")
check("configuration has the adaptive block", data["data"]["adaptive"]["catalogVersion"], catalog_version)

print("== application version selection")
app_id = int(sql(f"INSERT INTO applications (pkg, name, showIcon, system, customerId, type) VALUES ('{TEST_PKG}', "
                 f"'Regression', TRUE, FALSE, (SELECT customerId FROM devices WHERE id = {device_id}), 'app') "
                 f"RETURNING id").splitlines()[0])
old_id = int(sql(f"INSERT INTO applicationVersions (applicationId, version, versionCode, url) VALUES ({app_id}, "
                 f"'1.0', 1, 'https://example.com/old.apk') RETURNING id").splitlines()[0])
new_id = int(sql(f"INSERT INTO applicationVersions (applicationId, version, versionCode, url) VALUES ({app_id}, "
                 f"'2.0', 2, 'https://example.com/new.apk') RETURNING id").splitlines()[0])
sql(f"UPDATE applications SET latestVersion = {new_id} WHERE id = {app_id}")
sql(f"INSERT INTO configurationApplications (configurationId, applicationId, applicationVersionId, action, showIcon) "
    f"VALUES ({config_id}, {app_id}, {new_id}, 1, TRUE)")


def synced_app():
    data = device("GET", f"/rest/public/sync/configuration/{DEVICE}")[1]["data"]
    apps = [a for a in data["applications"] if a["pkg"] == TEST_PKG]
    return (apps[0] if apps else None), data["adaptive"]["applicationOverrides"]


app, overrides = synced_app()
check("suitable version delivered unchanged", (app["version"], overrides), ("2.0", []))
check("set a higher lowest API level", private(ADMIN, "POST", f"/applications/{new_id}/overrides",
                                               {"minSdk": 31})["status"], "OK")
app, overrides = synced_app()
check("older suitable version delivered", (app["version"], app["url"]), ("1.0", "https://example.com/old.apk"))
check("replacement reported", (overrides[0]["fromVersion"], overrides[0]["version"]), ("2.0", "1.0"))
private(ADMIN, "POST", f"/applications/{old_id}/overrides", {"minSdk": 99})
app, overrides = synced_app()
check("application left out when no version suits", (app, overrides[0].get("version")), (None, None))
report = private(ADMIN, "GET", f"/configurations/{config_id}/check")["data"]
check("configuration check lists the application",
      any(i["kind"] == "application" and i["subject"] == TEST_PKG for i in report["issues"]), True)

print("== configuration check")
sql(f"UPDATE configurations SET restrictions = 'no_config_wifi,disallow_camera_toggle,no_such_restriction' "
    f"WHERE id = {config_id}")
report = private(ADMIN, "GET", f"/configurations/{config_id}/check")["data"]
issues = {i["subject"]: i for i in report["issues"]}
check("restriction introduced after the device's API level", issues.get("restriction.disallow_camera_toggle",
                                                                      {}).get("state"), "unsupported")
check("available restriction not listed", "restriction.no_config_wifi" in issues, False)
check("unknown restriction listed", issues.get("restriction.no_such_restriction", {}).get("kind"),
      "unknownCapability")
check("device listed", DEVICE in issues["restriction.disallow_camera_toggle"]["deviceNumbers"], True)

print("== fleet, catalogs, scripts")
fleet = private(ADMIN, "GET", "/fleet")["data"]
check("fleet lists the device", any(d["number"] == DEVICE and d["sdkInt"] == 30 for d in fleet["devices"]), True)
check("fleet counts Android versions", fleet["bySdk"].get("API 30 (Android 11)", 0) >= 1, True)
catalogs = private(ADMIN, "GET", "/catalogs")["data"]
check("built-in catalog in use", catalogs["active"], catalogs["builtIn"])
check("invalid catalog rejected", private(ADMIN, "POST", "/catalogs", {"schemaVersion": 1})["status"], "ERROR")
builtin = private(ADMIN, "GET", f"/catalogs/{catalogs['builtIn']}")["data"]
year, month, n = catalogs["builtIn"].split(".")
newer = dict(builtin, catalogVersion=f"{year}.{month}.{int(n) + 100}")
older = dict(builtin, catalogVersion="2000.01.1")
existing = [c["version"] for c in catalogs["uploaded"]]
if newer["catalogVersion"] not in existing:
    check("catalog uploaded", private(ADMIN, "POST", "/catalogs", newer)["status"], "OK")
if older["catalogVersion"] not in existing:
    private(ADMIN, "POST", "/catalogs", older)
check("same version rejected", private(ADMIN, "POST", "/catalogs", newer)["status"], "ERROR")
check("older catalog not activated", private(ADMIN, "POST", "/catalogs/2000.01.1/activate")["status"], "ERROR")
check("catalog activated", private(ADMIN, "POST", f"/catalogs/{newer['catalogVersion']}/activate")["data"]["version"],
      newer["catalogVersion"])
check("devices get the new catalog version",
      device("GET", f"/rest/public/sync/configuration/{DEVICE}")[1]["data"]["adaptive"]["catalogVersion"],
      newer["catalogVersion"])
check("capabilities resolved with the new catalog",
      sql(f"SELECT catalogVersion FROM plugin_deviceprofile_profiles WHERE deviceId = {device_id}"),
      newer["catalogVersion"])
check("built-in catalog restored", private(ADMIN, "POST", "/catalogs/builtin/activate")["data"]["version"],
      catalogs["builtIn"])
check("setup check requested", private(ADMIN, "POST", f"/devices/{device_id}/setupCheck")["status"], "OK")
script = private(ADMIN, "GET", f"/enrollment/script?configurationId={config_id}&format=sh&"
                                f"steps=writeSecureSettings,batteryOptimization")["data"]
check("script makes Butter the device owner",
      "adb shell dpm set-device-owner com.buttermdm.agent/com.buttermdm.agent.AdminReceiver" in script["content"], True)
check("script grants secure settings",
      "adb shell pm grant com.buttermdm.agent android.permission.WRITE_SECURE_SETTINGS || true" in script["content"],
      True)
check("script leaves out unselected steps", "SYSTEM_ALERT_WINDOW" in script["content"], False)
bat = private(ADMIN, "GET", f"/enrollment/script?configurationId={config_id}&format=bat&steps=overlay")["data"]
check("batch file", (bat["fileName"].endswith(".bat"), "\r\n" in bat["content"]), (True, True))
check("audit log records", int(sql("SELECT COUNT(*) FROM plugin_audit_log WHERE action LIKE "
                                   "'plugin.audit.action.deviceprofile.%'")) >= 4, True)

print("== permissions")
LIMITED = token("drlimited", ADMIN_PASS)
check("user without permissions: device details denied",
      private(LIMITED, "GET", f"/devices/{device_id}")["message"], "error.permission.denied")
check("user without permissions: catalog upload denied",
      private(LIMITED, "POST", "/catalogs", newer)["message"], "error.permission.denied")
check("user without permissions: script denied",
      private(LIMITED, "GET", f"/enrollment/script?configurationId={config_id}")["message"], "error.permission.denied")

# Clean up the configuration and the test application
restore = "NULL" if config_restrictions == "<null>" else "'" + config_restrictions.replace("'", "''") + "'"
sql(f"UPDATE configurations SET restrictions = {restore} WHERE id = {config_id}")
sql(f"DELETE FROM configurationApplications WHERE applicationId = {app_id}")
sql(f"UPDATE applications SET latestVersion = NULL WHERE id = {app_id}")
sql(f"DELETE FROM applications WHERE id = {app_id}")

print()
print("ALL PASSED" if fails == 0 else f"{fails} FAILED")
sys.exit(1 if fails else 0)
