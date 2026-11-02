# ButterMDM tools

| Tool | Purpose |
|---|---|
| `server-regression.sh` | API regression test of the Device Reset plugin (requests, sync hook and signature, device confirmations, audit log, permissions). Run it against a test server with a disposable database after every build or merge (usage in the script header) |
| `console-test.py` | Drives the Device Reset page of the web console (React console: login, device menu, lock/unlock, factory reset confirmation, cancel) in a headless Chromium-based browser (usage in the script header) |
| `deviceprofile-regression.py` | API regression test of the Device Profile plugin (device endpoints and signatures, catalogs, reports and rule mismatches, application selection, configuration check, enrollment scripts, audit, permissions); usage in the script header |
| `catalog-restrictions.py` | Generates the user restriction entries of the capability catalog from the Android SDK (`api-versions.xml` and `android.jar`) |
| `remotesupport-regression.py` | API and WebSocket regression test of the Remote Support plugin (sessions, relay, view-only, tokens, end reasons, audit, permissions); usage in the script header |
| `device-simulator.py` | Plays the remote support side of a Butter device (Push polling, session, generated screen frames, input), for tests and demonstrations without a device |
| `reapply.py` | Re-applies the mechanical ButterMDM changes (branding, launcher application `com.buttermdm.agent`) after merging a new ButterMDM version; `--check` lists what would change |
| `logo/buttermdm-logo.png`, `logo/buttermdm-logo.svg` | The ButterMDM logo: transparent PNG (master of the generated images) and the vector original |
| `make-logo-assets.sh` | Generates the console logo and browser icons from the master (ImageMagick 7) |
| `rename.py` | Renames ButterMDM's identifiers (Java packages, Maven group IDs, file, database and path names) to ButterMDM's; used by `documentation/tools/clean-upstream.py` on every ButterMDM commit before a merge |
