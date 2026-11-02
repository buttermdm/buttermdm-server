<p align="center">
  <img src="buttermdm/logo/buttermdm-logo.png" alt="ButterMDM" width="420">
</p>

# ButterMDM server

ButterMDM is an open source mobile device management (MDM) system for Android devices, built for point-of-sale
terminals, kiosks and other dedicated corporate devices. This repository contains the **server**: the web console,
the REST API and the device synchronization service. The device side is the **Butter** agent:
[ButterMDM/buttermdm-android](https://github.com/ButterMDM/buttermdm-android).

ButterMDM is licensed under the Apache License 2.0.

## Features

- Enrollment of Android devices as fully managed devices (device owner) by QR code or ADB
- Configurations: the apps, desktop layout, restrictions and settings each group of devices gets
- Automatic installation and update of applications from the console
- **Kiosk mode:** one app pinned on the screen (Android lock task), with an allowlist of further apps, a status bar
  lock and control of the Home, Recents, notifications and power menu buttons; administrator exit and re-entry by
  Push message (`exitKiosk`, `enterKiosk`)
- **Device Reset plugin:** remote factory reset, reboot, lock with a message, and device password reset, from the
  console and the REST API ([plugins/devicereset/README.md](plugins/devicereset/README.md))
- **Device Profile plugin:** the Android version, manufacturer and capabilities of every device; a check of each
  configuration against its devices; application versions chosen by Android version; a setup checklist on the
  device and ADB enrollment scripts ([plugins/deviceprofile/README.md](plugins/deviceprofile/README.md))
- **Remote Support plugin:** view and control the screen of a device from the console or from the
  ButterMDM Support app for Windows, relayed by the server, with
  acceptance by the person at the device, time limits and an audit log
  ([plugins/remotesupport/README.md](plugins/remotesupport/README.md))
- Push messages to devices; device logs; device information; audit log of administrator actions
- Groups, users, roles and permissions; several customers on one server (optional)
- Localized console (English and 13 other languages; the strings of the new screens are English until translated)

## Architecture Overview

| Area | ButterMDM |
|---|---|
| Device agent | The Butter agent (`com.buttermdm.agent`) is the default launcher application of new configurations and of the installer data |
| Device Reset plugin | Added (`plugins/devicereset`). Device confirmations must carry the request signature; requests are cleared once confirmed, so a reboot or reset happens once |
| Device Profile plugin | Added (`plugins/deviceprofile`): device profiles, capability catalog, configuration check, application version selection by API level, device setup states, enrollment scripts |
| Remote Support plugin | Added (`plugins/remotesupport`): remote view and control of devices over WebSocket connections to the server, off until enabled per configuration |
| Push plugin | Lists the `enterKiosk` message (return to kiosk mode after `exitKiosk`) |
| Updates | No self-update of the web application, no external update check, no usage statistics sent anywhere |
| Sample data | Contains only the Butter agent (streamlined default dataset) |
| Branding | ButterMDM name and logo in the console, e-mails, API documentation, installer and scripts |

## Requirements

| | |
|---|---|
| Build | JDK 11, 17 or 21, Maven 3.9; the build downloads Node.js for the web console (the installer builds ButterMDM itself) |
| Run | Linux (Debian 10-13 or Ubuntu 18.04-24.04), Java 11, 17 or 21, Apache Tomcat 9 (Tomcat 10+ is not supported), PostgreSQL |
| Devices | Android 8.0 or later with the Butter agent |

## Building

The installer compiles ButterMDM itself (see Installing). To build by hand:

```bash
git clone --branch v0.1.0 https://github.com/buttermdm/buttermdm-server
git clone --branch v0.1.0 https://github.com/buttermdm/buttermdm-console
cd buttermdm-server
cp server/build.properties.example server/build.properties   # adjust the values if you run the server from an IDE
mvn install -Dconsole.dir=../buttermdm-console
```

The web application is `server/target/launcher.war`. Details: [BUILD.txt](BUILD.txt).

## Installing

Follow [INSTALL.txt](INSTALL.txt): run the installer `sudo ./buttermdm_install.sh` from the repository root. The
installer is a bootstrapper: it detects the distribution, offers once to install the missing components (aapt,
PostgreSQL, Tomcat 9, and git, Maven and a JDK for the build), downloads the web console (tag `v0.1.0` of
`buttermdm-console`) and compiles ButterMDM, can create the local database, deploys the WAR, loads the sample data
(configurations, applications) into the database, and prints the console URL. On Debian 13, which has no `tomcat9`
package, it installs the official Apache Tomcat 9 release (checksum verified) as the `tomcat9` service.

After installing:

1. In the Tomcat context of the application, set a new random `hash.secret` and set `secure.enrollment` to `1`
   (see [install/context_template.xml](install/context_template.xml)).
2. Log in to the console and change the administrator password.
3. Set a strong password in every configuration: the sample configurations use `12345678`, and the Butter agent
   refuses to open its administrator panel for a configuration without a password.
4. Build the Butter agent with your server URL and the same `hash.secret`
   ([buttermdm-android](https://github.com/ButterMDM/buttermdm-android)), then upload the APK to the application
   `com.buttermdm.agent` in the console.
5. Grant the plugin permissions (`plugin_devicereset_*`, `plugin_deviceprofile_*`, `plugin_remotesupport_*`) only
   to the roles that need them. By default only the Super-Admin and Admin roles have them.
6. For remote support: turn it on for the configurations that need it (Functions → Remote support → Settings). If
   a reverse proxy is in front of Tomcat, it must pass WebSocket connections for `/ws/`
   ([plugins/remotesupport/README.md](plugins/remotesupport/README.md)).

## Enrolling devices

- **QR code:** in the console, open a configuration and show its QR code. On a new or factory-reset device, tap the
  welcome screen six times and scan the code. The device downloads Butter from your server and becomes a managed
  device.
- **ADB:** on a device without accounts:
  ```bash
  adb install butter.apk
  adb shell dpm set-device-owner com.buttermdm.agent/com.buttermdm.agent.AdminReceiver
  # Optional: lets Butter turn on its accessibility service for remote support control by itself
  adb shell pm grant com.buttermdm.agent android.permission.WRITE_SECURE_SETTINGS
  ```
  Functions → Device profiles → Enrollment creates a script with these commands and further setup steps.

## Repository layout

| Path | Content |
|---|---|
| `server/` | Web application: REST API, synchronization service; packages the console built from `../../console/<version>` (see [BUILD.txt](BUILD.txt)) |
| `../../console/<version>/` | Web console (React 19, TypeScript, shadcn/ui, Vite): dashboard, devices, configurations, applications, files, settings and every plugin page; its own README describes the development server and the browser tests |
| `common/`, `jwt/`, `notification/` | Shared domain, persistence, JWT authentication, Push notification services |
| `plugins/` | Plugins: `audit`, `deviceinfo`, `devicelog`, `devicereset`, `deviceprofile`, `messaging`, `push`, `platform`, `remotesupport`, `rustdesk`, and integrations with third-party systems (each plugin has its own README) |
| `swagger/` | REST API documentation (Swagger UI) |
| `install/` | Installer data: database sample data, Tomcat context and logging templates |
| `buttermdm/` | ButterMDM tools: regression tests, logo master and asset generator, re-branding script ([buttermdm/README.md](buttermdm/README.md)) |

## Security notes

- Keep `hash.secret` secret and unique per installation; it authenticates devices and signs configurations.
- Devices confirm reboots, resets and password resets with signed requests. Unsigned confirmations are rejected
  (controlled by the context parameter `plugin.devicereset.allow.unsigned`).
- Passwords sent to devices are never written to the audit log and are deleted once the device confirms them.
- Remote support is off until enabled per configuration; by default the person at the device must accept each
  session, and Android asks them to allow the screen capture. Sessions use one-time tokens and are written to the
  audit log.

## Versions

| Version | Status | Content |
|---|---|---|
| **0.1.0** | Pre-release | First public release: ButterMDM server with the Device Reset, Device Profile, Remote Support, RustDesk and maintenance gate plugins, the Adaptive Android Architecture (`2026.10.1` capability catalog), the React/TypeScript web console (built from `buttermdm-console` into the WAR), and an installer that compiles ButterMDM on Debian 10-13 and Ubuntu 18.04-24.04 |

Releases are tagged `vX.Y.Z`; 0.x releases are pre-releases. Use server 0.1.0 with console 0.1.0 and Butter agent 0.1.0.

## License

ButterMDM is licensed under the Apache License, Version 2.0: see [LICENSE](LICENSE) and [NOTICE](NOTICE).

Copyright (C) 2019-2026 ButterMDM (https://github.com/ButterMDM), licensed under the Apache License, Version 2.0.
