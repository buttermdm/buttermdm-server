#!/usr/bin/env python3
# ButterMDM server regression test: Remote Support plugin (settings, sessions, WebSocket relay, view-only sessions,
# tokens, ending, recording, audit log, permissions). The device side is buttermdm/device-simulator.py.
# Runs against a live server with a disposable database, after server-regression.sh (it uses the device DRTEST1
# and the user drlimited created there). It restores the remote support settings of DRTEST1's configuration.
#
# Usage: BASE=http://127.0.0.1:8090 SECRET=<hash.secret> PSQL="psql -h 127.0.0.1 -p 5432 -U buttermdm buttermdm" \
#        ADMIN_USER=admin ADMIN_PASS=admin python3 buttermdm/remotesupport-regression.py
import asyncio, hashlib, json, os, shlex, subprocess, sys, time, urllib.error, urllib.request
import websockets

BASE = os.environ.get("BASE", "http://127.0.0.1:8090")
WS_BASE = BASE.replace("http", "ws", 1)
PSQL = shlex.split(os.environ["PSQL"])
SECRET = os.environ["SECRET"]
ADMIN_USER, ADMIN_PASS = os.environ.get("ADMIN_USER", "admin"), os.environ.get("ADMIN_PASS", "admin")
DEVICE = "DRTEST1"
SIMULATOR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "device-simulator.py")
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


def http(method, path, body=None, token=None):
    request = urllib.request.Request(BASE + path, method=method,
                                     data=json.dumps(body).encode() if body is not None else None,
                                     headers={"Content-Type": "application/json",
                                              **({"Authorization": "Bearer " + token} if token else {})})
    with urllib.request.urlopen(request) as response:
        return json.loads(response.read().decode())


def device_config():
    signature = hashlib.sha1((SECRET + DEVICE).encode()).hexdigest()
    request = urllib.request.Request(f"{BASE}/rest/public/sync/configuration/{DEVICE}",
                                     headers={"X-Request-Signature": signature})
    with urllib.request.urlopen(request) as response:
        return json.loads(response.read().decode())["data"]


def login(user, password):
    md5 = hashlib.md5(password.encode()).hexdigest().upper()
    return http("POST", "/rest/public/jwt/login", {"login": user, "password": md5})["id_token"]


def api(tok, method, path, body=None):
    return http(method, "/rest/plugins/remotesupport/private" + path, body, tok)


def simulator(*options):
    return subprocess.Popen([sys.executable, SIMULATOR, DEVICE, "--once", "--quiet", "--fps", "10", *options],
                            env=dict(os.environ, BASE=BASE))


async def technician(start, actions, duration=6.0, device_wait=30.0):
    """Connects as the technician, runs the actions after the first frames, returns what was received.
    The duration counts from the first frame; the device has device_wait seconds to send it (a busy machine may
    deliver the Push message to the simulator late)."""
    url = f"{WS_BASE}{start['path']}?session={start['sessionId']}&token={start['token']}"
    received = {"frames": 0, "jpeg": True, "texts": [], "closed": None}
    async with websockets.connect(url, max_size=8_000_000) as ws:
        t0, done = time.time(), False
        while time.time() - t0 < (duration if received["frames"] else device_wait):
            try:
                message = await asyncio.wait_for(ws.recv(), 1)
            except asyncio.TimeoutError:
                continue
            except websockets.ConnectionClosed as e:
                received["closed"] = e.rcvd.reason if e.rcvd else ""
                break
            if isinstance(message, bytes):
                if not received["frames"]:
                    t0 = time.time()
                received["frames"] += 1
                received["jpeg"] &= message[:2] == b"\xff\xd8"
                if received["frames"] == 3 and not done:
                    for action in actions:
                        if action.get("t") == "end":
                            # Let the device answer the input first
                            await asyncio.sleep(1.5)
                        await ws.send(json.dumps(action))
                    done = True
            else:
                received["texts"].append(json.loads(message))
    return received


def texts_of(received, kind):
    return [t for t in received["texts"] if t.get("t") == kind]


async def ws_rejected(url):
    try:
        async with websockets.connect(url) as ws:
            await asyncio.wait_for(ws.recv(), 3)
            return False
    except websockets.ConnectionClosed as e:
        return e.rcvd is not None and e.rcvd.code == 1008
    except Exception:
        return True


ADMIN = login(ADMIN_USER, ADMIN_PASS)
LIMITED = login("drlimited", ADMIN_PASS)
device_id, config_id = map(int, sql(f"SELECT id, configurationId FROM devices WHERE number = '{DEVICE}'").split("|"))
previous = sql(f"SELECT enabled || '|' || requireAcceptance || '|' || maxMinutes FROM plugin_remotesupport_settings "
               f"WHERE configurationId = {config_id}")

print("== settings")
api(ADMIN, "POST", f"/settings/{config_id}", {"enabled": False, "requireAcceptance": True, "maxMinutes": 30})
refused = api(ADMIN, "POST", "/sessions", {"deviceId": device_id})
check("disabled configuration refuses sessions", (refused["status"], refused["message"]),
      ("ERROR", "plugin.remotesupport.error.disabled"))
check("disabled: configuration has no remote support block",
      "remoteSupport" in device_config(), False)
check("invalid time limit rejected", api(ADMIN, "POST", f"/settings/{config_id}",
                                         {"enabled": True, "maxMinutes": 0})["message"],
      "plugin.remotesupport.error.maxMinutes")
check("remote support enabled", api(ADMIN, "POST", f"/settings/{config_id}",
                                    {"enabled": True, "requireAcceptance": True, "maxMinutes": 30})["status"], "OK")
settings = [s for s in api(ADMIN, "GET", "/settings")["data"] if s["configurationId"] == config_id][0]
check("settings listed", (settings["enabled"], settings["requireAcceptance"], settings["maxMinutes"]), (True, True, 30))
sync = device_config()
check("configuration tells the device", sync.get("remoteSupport"),
      {"enabled": True, "requireAcceptance": True, "maxMinutes": 30})

print("== control session")
sim = simulator()
time.sleep(1)
start = api(ADMIN, "POST", "/sessions", {"deviceId": device_id, "viewOnly": False})["data"]
check("session started", bool(start["sessionId"] and start["token"]), True)
received = asyncio.run(technician(start, [{"t": "tap", "x": 0.25, "y": 0.75}, {"t": "key", "key": "back"},
                                          {"t": "text", "text": "hello"}, {"t": "end"}]))
sim.wait(timeout=20)
check("device joined through its Push message; frames relayed", received["frames"] >= 3, True)
check("frames are JPEG", received["jpeg"], True)
check("session info sent", texts_of(received, "session")[0]["deviceNumber"], DEVICE)
check("device hello replayed or relayed", texts_of(received, "hello")[0]["w"], 360)
check("peers connected", texts_of(received, "peer")[0]["connected"], True)
echoed = [t["received"]["t"] for t in texts_of(received, "status") if t.get("state") == "input"]
check("input reached the device", echoed, ["tap", "key", "text"])
check("session recorded as ended", sql(f"SELECT endReason FROM plugin_remotesupport_sessions WHERE id = "
                                       f"'{start['sessionId']}'"), "ended by the technician")
check("frames counted", int(sql(f"SELECT frames FROM plugin_remotesupport_sessions WHERE id = "
                                f"'{start['sessionId']}'")) >= 3, True)

print("== view-only session")
sim = simulator()
time.sleep(1)
start = api(ADMIN, "POST", "/sessions", {"deviceId": device_id, "viewOnly": True})["data"]
received = asyncio.run(technician(start, [{"t": "tap", "x": 0.5, "y": 0.5}, {"t": "end"}]))
sim.wait(timeout=20)
check("view-only: frames relayed", received["frames"] >= 3, True)
check("view-only: input dropped by the server",
      [t for t in texts_of(received, "status") if t.get("state") == "input"], [])

print("== tokens, duplicates, ending from the device")
sim = simulator("--decline")
time.sleep(1)
start = api(ADMIN, "POST", "/sessions", {"deviceId": device_id})["data"]
check("wrong token rejected", asyncio.run(ws_rejected(
    f"{WS_BASE}{start['path']}?session={start['sessionId']}&token=0000")), True)
check("device token rejected on the technician side", asyncio.run(ws_rejected(
    f"{WS_BASE}/ws/remotesupport/device?session={start['sessionId']}&token={start['token']}")), True)
check("unknown session rejected", asyncio.run(ws_rejected(
    f"{WS_BASE}{start['path']}?session=nosuchsession&token={start['token']}")), True)


async def duplicate_and_decline():
    url = f"{WS_BASE}{start['path']}?session={start['sessionId']}&token={start['token']}"
    async with websockets.connect(url) as first:
        rejected = await ws_rejected(url)
        reason = None
        try:
            while True:
                message = json.loads(await asyncio.wait_for(first.recv(), 10))
                if message.get("t") == "end":
                    reason = message.get("reason")
                    break
        except Exception:
            pass
        return rejected, reason

rejected, reason = asyncio.run(duplicate_and_decline())
sim.wait(timeout=20)
check("second technician connection rejected", rejected, True)
check("declined on the device (reason relayed)", reason, "declined on the device")

print("== lists, audit, permissions")
sessions = api(ADMIN, "GET", "/sessions")["data"]
check("no session left in progress", [s for s in sessions["active"] if s["deviceNumber"] == DEVICE], [])
check("recent sessions listed", len([s for s in sessions["recent"] if s["deviceNumber"] == DEVICE]) >= 3, True)
check("audit: sessions started", int(sql("SELECT COUNT(*) FROM plugin_audit_log WHERE action = "
                                         "'plugin.audit.action.remotesupport.session.start'")) >= 3, True)
check("audit: sessions ended", int(sql("SELECT COUNT(*) FROM plugin_audit_log WHERE action = "
                                       "'plugin.audit.action.remotesupport.session.end'")) >= 3, True)
check("user without permissions: session denied",
      api(LIMITED, "POST", "/sessions", {"deviceId": device_id, "viewOnly": True})["message"], "error.permission.denied")
check("user without permissions: settings denied",
      api(LIMITED, "POST", f"/settings/{config_id}", {"enabled": True, "maxMinutes": 5})["message"],
      "error.permission.denied")
check("user without permissions: list denied", api(LIMITED, "GET", "/sessions")["message"], "error.permission.denied")

if previous:
    enabled, acceptance, minutes = previous.split("|")
    api(ADMIN, "POST", f"/settings/{config_id}", {"enabled": enabled == "t", "requireAcceptance": acceptance == "t",
                                                   "maxMinutes": int(minutes)})
else:
    sql(f"DELETE FROM plugin_remotesupport_settings WHERE configurationId = {config_id}")

print()
print("ALL PASSED" if fails == 0 else f"{fails} FAILED")
sys.exit(1 if fails else 0)
