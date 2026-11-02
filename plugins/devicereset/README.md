# Device Reset plugin (ButterMDM)

Factory reset, reboot, lock (with a message) and device password reset for ButterMDM.
The launcher already contains the device side; this plugin adds the missing server side.

## How it works

1. An administrator (or an API client) stores a request with a private endpoint. The device is notified by a
   `configUpdated` Push message.
2. The pending requests are added to the device configuration (`factoryReset`, `reboot`, `lock`, `lockMessage`,
   `passwordReset`) by a sync response hook. If nothing is pending, the configuration is not changed at all.
3. The launcher confirms the factory reset and the reboot **before** performing them, and the password reset after
   applying it. A confirmation clears the request, so a reboot or a factory reset is performed only once.
   The plain-text password is deleted when the device confirms the password reset.

The lock stays until the device is unlocked.

## Private API (administrators)

All paths start with `/rest/plugins/devicereset/private`. Authentication and access checks are the same as for the
other plugins (web session or JWT). `{deviceId}` is the numeric device ID; the device must belong to the customer of
the current user. The responses use the standard format `{"status":"OK|ERROR","message":...,"data":...}`.

| Method and path | Permission | Body | Effect |
|---|---|---|---|
| `GET /{deviceId}` | any of the four | | Pending requests and confirmation times (the password itself is never returned) |
| `POST /{deviceId}/reset` | `plugin_devicereset_reset` | | Request the factory reset |
| `POST /{deviceId}/reboot` | `plugin_devicereset_reboot` | | Request the reboot |
| `POST /{deviceId}/lock` | `plugin_devicereset_lock` | `{"message": "..."}` (optional) | Lock the device |
| `POST /{deviceId}/unlock` | `plugin_devicereset_lock` | | Unlock the device |
| `POST /{deviceId}/password` | `plugin_devicereset_password` | `{"password": "..."}` | Set the screen lock password; an empty password removes it |
| `POST /{deviceId}/cancel` | any of the four | | Cancel the factory reset, reboot and password reset requests not yet confirmed (doesn't unlock) |

The permissions are granted to the Super-Admin and Admin roles only. Grant them to other roles explicitly.
Every request and every confirmation is written to the audit log (`Audit` plugin); passwords are never logged.

Two-person approval of the factory reset is expected to be done by the calling system before it calls `/reset`.

## Public API (devices)

`POST /rest/plugins/devicereset/public/{number}` (factory reset), `/public/reboot/{number}` and
`/public/password/{number}`, body: device info JSON.

- The `X-Request-Signature` header must be `SHA1(hash.secret + number)`, as for the configuration request.
  Otherwise the response is HTTP 403 and nothing changes.
- HTTP 200 on success, also if nothing was pending (nothing changes then). HTTP 404 for an unknown device,
  HTTP 500 on error. Errors are never reported with HTTP 200, because the launcher treats any 2xx status as a
  confirmation and would reboot or wipe the device.

The Butter agent (ButterMDM) signs these requests. Legacy unsigned clients do not; to allow unsigned confirmations, set the
`plugin.devicereset.allow.unsigned` context parameter to `1` (then anyone who knows a device number can confirm,
and so cancel, a pending request).

## Database

Table `plugin_devicereset_status` (one row per device, deleted with the device), permissions
`plugin_devicereset_reset`, `plugin_devicereset_lock`, `plugin_devicereset_reboot`, `plugin_devicereset_password`.
