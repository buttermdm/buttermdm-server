#!/usr/bin/env python3
# Regenerates the "restriction.*" entries of the capability catalog from the Android SDK: the restriction names
# (UserManager.DISALLOW_* values, from android.jar) and the API level that introduced each (api-versions.xml).
# Run from the repository root after installing a new Android platform in the SDK, then review the diff:
#
#   buttermdm/catalog-restrictions.py $ANDROID_HOME/platforms/android-XX
import json, os, re, subprocess, sys
import xml.etree.ElementTree as ET

CATALOG = "plugins/deviceprofile/src/main/resources/catalog/capabilities.json"

if len(sys.argv) != 2 or not os.path.isfile(os.path.join(sys.argv[1], "android.jar")):
    sys.exit("usage (from the repository root): buttermdm/catalog-restrictions.py <SDK>/platforms/android-XX")
platform = sys.argv[1]

out = subprocess.run(["javap", "-constants", "-cp", os.path.join(platform, "android.jar"), "android.os.UserManager"],
                     check=True, capture_output=True, text=True).stdout
values = dict(re.findall(r'String (DISALLOW_[A-Z0-9_]+) = "([^"]+)"', out))

root = ET.parse(os.path.join(platform, "data", "api-versions.xml")).getroot()
user_manager = next(c for c in root.iter("class") if c.get("name") == "android/os/UserManager")
since = {}
for f in user_manager.iter("field"):
    if f.get("name") in values:
        since[f.get("name")] = (int(float(f.get("since") or user_manager.get("since"))), f.get("deprecated"))

entries = []
for name in sorted(values, key=lambda n: (since[n][0], values[n])):
    level, deprecated = since[name]
    entry = {
        "id": "restriction." + values[name],
        "area": "restrictions",
        "title": "Restriction " + values[name] + " (UserManager." + name + ")",
        "minSdk": level,
        "requiresDeviceOwner": True,
        "strategies": [{"id": "UserRestriction", "minSdk": level}],
        "configFields": ["restrictions"],
        "confidence": "documented",
    }
    if deprecated:
        entry["notes"] = "Deprecated since API " + deprecated.split(".")[0]
    entries.append(entry)

catalog = json.load(open(CATALOG, encoding="utf-8"))
catalog["capabilities"] = [c for c in catalog["capabilities"] if not c["id"].startswith("restriction.")] + entries
with open(CATALOG, "w", encoding="utf-8") as f:
    json.dump(catalog, f, indent=2, ensure_ascii=False)
    f.write("\n")
print(f"{len(entries)} restriction entries written to {CATALOG}")
