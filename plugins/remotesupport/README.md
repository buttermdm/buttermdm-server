# Remote Support plugin (ButterMDM)

Remote view and control of devices by technicians, from the web console or the ButterMDM Support app for
Windows (`buttermdm-support`). The device side is part of the Butter agent.

The screen and the input are relayed by the server over WebSocket connections that the device and the technician
open to the server, so no other server (relay, TURN) is needed and the devices need no inbound connections. The
screen is sent as JPEG frames when it changes; input is sent as small JSON messages.

## Settings and permissions

Remote support is **off** for every configuration until an administrator turns it on (console: Functions → Remote
support → Settings). Per configuration:

| Setting | Meaning |
|---|---|
| Remote support | Sessions can be started for the devices of the configuration |
| The person at the device must accept | Butter asks the person at the device to accept each session (default on) |
| Time limit | A session ends after this many minutes (1–480, default 60) |

Android itself asks the person at the device to allow the screen capture (MediaProjection) when a session starts,
on Android 14 and later for every session. If the screen is off, Butter turns it on and asks over the lock screen;
a device locked with a PIN, pattern or password must be unlocked before the session starts. Control (taps, swipes, keys, text) needs the Butter accessibility
service: Butter enables it itself when it was granted `WRITE_SECURE_SETTINGS` over ADB during the setup
(`adb shell pm grant com.buttermdm.agent android.permission.WRITE_SECURE_SETTINGS`), otherwise it has to be enabled
by hand in the Settings of the device. Without it, sessions are view-only.

| Permission | Allows |
|---|---|
| `plugin_remotesupport_view` | View-only sessions, the list of sessions, ending sessions |
| `plugin_remotesupport_control` | Sessions with control |
| `plugin_remotesupport_settings` | Changing the settings of configurations |

The permissions are granted to the Super-Admin and Admin roles only. Every session start and end, and every change of
the settings, is written to the audit log (`Audit` plugin).

## REST API (technicians)

All paths start with `/rest/plugins/remotesupport/private`; authentication as for the other plugins (web session or
JWT, `POST /rest/public/jwt/login`).

| Method and path | Body | Result |
|---|---|---|
| `POST /sessions` | `{"deviceId": 12, "viewOnly": false}` | `{"sessionId", "token", "path": "/ws/remotesupport/client", "deviceNumber", "viewOnly", "requireAcceptance"}`; the device is asked by a Push message (`remoteSession`) to connect |
| `GET /sessions` | | `{"active": [...], "recent": [...]}` of the customer |
| `POST /sessions/{id}/end` | | Ends a session in progress |
| `GET /settings` | | The settings of all configurations of the customer |
| `POST /settings/{configurationId}` | `{"enabled": true, "requireAcceptance": true, "maxMinutes": 60}` | Saves the settings; the devices of the configuration are notified |

Errors: `plugin.remotesupport.error.disabled` (remote support is off for the device's configuration),
`error.permission.denied`, `error.notfound.device`.

## WebSocket protocol

The technician connects to `ws(s)://<server><context>/ws/remotesupport/client?session=<sessionId>&token=<token>`
within two minutes; the device connects to `/ws/remotesupport/device` with the session ID and the token it received
in the Push message. Each token is valid for one connection of its side. A wrong session, token or side, or a second
connection of the same side, is closed with code 1008.

Text messages are JSON objects with the type `t`. Binary messages are screen frames (JPEG), from the device only.

**Server → both**

| Message | Meaning |
|---|---|
| `{"t":"session","sessionId","deviceNumber","viewOnly","requireAcceptance","maxMinutes","technician"}` | Sent on connection |
| `{"t":"peer","connected":true}` | Both sides are connected |
| `{"t":"end","reason":"..."}` | The session ended; the connection is closed next |

**Device → technician** (relayed; the last `hello` and `status` are sent again to a technician connecting later)

| Message | Meaning |
|---|---|
| `{"t":"hello","w":1080,"h":2400,"sdk":29,"capture":"mediaProjection","input":true}` | Screen size, Android API level, capture method, whether control is available |
| `{"t":"status","state":"waitingAcceptance"}` | Waiting for the person at the device to accept the session |
| `{"t":"status","state":"waitingConsent"}` | Waiting for the person at the device to allow the screen capture |
| `{"t":"status","state":"streaming"}` | Frames follow |
| `{"t":"status","state":"inputUnavailable"}` | Control isn't possible (accessibility service off) |
| binary | A JPEG frame of the whole screen, scaled |

**Technician → device** (relayed; the input messages of view-only sessions are dropped by the server)

| Message | Meaning |
|---|---|
| `{"t":"tap","x":0.5,"y":0.25}` | Tap; coordinates relative to the screen (0–1). With `"ms"` above 600: long press |
| `{"t":"swipe","x1","y1","x2","y2","ms":300}` | Swipe (also scrolling) |
| `{"t":"key","key":"back"}` | `back`, `home`, `recents`, `notifications` |
| `{"t":"text","text":"..."}` | Types text into the focused field |
| `{"t":"quality","maxFps":8,"scale":0.5,"jpegQuality":60}` | Stream settings (the device may adjust them) |
| `{"t":"end"}` | Ends the session (also from the device, e.g. when the person declines) |
| `{"t":"ping"}` | Keeps the connection open through proxies; not relayed |

A session ends when either side sends `end` or disconnects, when a side doesn't connect within two minutes, or at
the time limit. The `end` message gives the reason, e.g. `ended by the technician`, `declined on the device`,
`screen capture not allowed on the device`, `device disconnected` or `time limit reached`.

## Reverse proxies

If the server is behind a reverse proxy, the proxy must pass WebSocket upgrades for `/ws/`. nginx example:

```nginx
location /ws/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 3600s;
}
```

## Database

Tables `plugin_remotesupport_settings` (one row per configuration) and `plugin_remotesupport_sessions` (history:
device, technician, connection and end times, end reason, frames and bytes relayed).
