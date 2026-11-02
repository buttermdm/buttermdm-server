# Device Profile plugin (ButterMDM)

Knows the Android version, manufacturer and capabilities of every device, and adapts the management to them: which
settings and restrictions of a configuration work on which devices, which version of an application each device
gets, and which setup steps a device still needs. The device side is part of the Butter agent.

## How it works

1. After enrollment, after its own updates and after OS upgrades, Butter sends a **device profile**: API level,
   Android release, security patch, manufacturer, brand, model, build fingerprint, agent version, and probes such
   as device owner, granted permissions and enabled services.
2. The **capability catalog** says, for each management capability (kiosk mode, location switch, time zone,
   user restrictions, …), from which API level it works, what it requires (device owner, a permission granted over
   ADB, a person at the device) and which fallback applies. The server and Butter evaluate the same catalog with the
   same algorithm, so the console shows what the device will do.
3. After applying a configuration, Butter checks what actually took effect and **reports** an outcome per
   capability (`applied`, `fallback`, `skipped-unsupported`, `failed`, `needs-user`, `needs-adb`) and the state of
   each setup step (`done`, `pending-user`, `pending-adb`, `not-applicable`, `failed`). An outcome that contradicts
   the catalog is recorded as a rule mismatch, and a capability that failed on a device counts as unsupported
   there from then on.
4. When the server sends a configuration to a device, it replaces application versions that need a higher Android
   version than the device has (see "Application versions").

## Console

Functions → Device profiles, and the Device profiles entry of each device:

| View | Content |
|---|---|
| Fleet | Devices by Android version, model and security patch age; devices without a profile |
| Device | Profile, capabilities with their state and the evidence for it (e.g. `rule:sdk=29`, `probe:writeSecureSettings=false`, `result:failed`), setup steps, history (first profile, OS upgrades, rule mismatches, setup changes); "Check setup" asks the device to check its setup again |
| Configuration check | For a configuration: the settings, restrictions and applications that won't work on some of its devices, and on which |
| Applications | Lowest and target API level and signing certificate of every application version (read from the APK files), with administrator overrides |
| Enrollment | A script (Linux shell or Windows batch) that installs Butter over ADB, makes it the device owner and runs the selected setup steps that need ADB, e.g. granting `WRITE_SECURE_SETTINGS` so Butter can turn on its remote support accessibility service |
| Capability catalog | The built-in catalog and uploaded ones; upload and activate a newer catalog |

## Application versions

The plugin reads the lowest API level (`minSdk`), the target API level and the signing certificate of each
uploaded APK; an administrator can override the lowest and highest API level of a version. When a configuration
gives a device a version whose lowest API level is above the device's, the device gets the highest version of the
same package that fits instead; if none fits, the application is left out for that device. Both are listed in the
configuration check and in the `adaptive.applicationOverrides` field of the configuration sent to the device.

This applies to the Butter agent too: a Butter release that drops an Android version can be configured next to
the last release supporting it.

## Capability catalog

The built-in catalog is `src/main/resources/catalog/capabilities.json` (the same file is built into Butter as
`app/src/main/assets/adaptive/capabilities.json`). Structure (`schemaVersion` 1):

| Field | Content |
|---|---|
| `catalogVersion` | e.g. `2026.09.1`; devices only move to higher versions |
| `capabilities` | `id`, `area`, `title`, `minSdk` / `maxSdk`, `requiresDeviceOwner`, `strategies` (per API level range), `requirements` (probes, e.g. a permission granted over ADB), `configFields` (the configuration fields that use it), `confidence`, `tests`, `notes` |
| `setupSteps` | The steps of the device setup screen: automatic, needs the person at the device, or needs ADB, per API level |
| `installFloor` | The lowest target API level Android accepts for installing an APK, per Android version |
| `vendors` | Manufacturer-specific rules (matched by manufacturer) |

The user restriction entries are generated from the Android SDK with `buttermdm/catalog-restrictions.py`. A new
catalog is uploaded in the console (Capability catalog) and activated; it is validated first (schema version,
version format `YYYY.MM.N`, unique IDs, API levels and strategies of every capability, setup step states) and must
have a higher version than the active one. Devices fetch the active catalog, signed like configurations, on
their next synchronization.

The resolver exists twice, in Java on the server (`catalog/CapabilityResolver`) and in Butter
(`adaptive/CapabilityResolver`). Both run the test vectors `src/test/resources/catalog-test-vectors.json`; a
change to the algorithm must be made in both and pass both.

## Permissions

| Permission | Allows |
|---|---|
| `plugin_deviceprofile_view` | The views above |
| `plugin_deviceprofile_manage` | Application overrides, setup checks, enrollment scripts; uploading and activating catalogs (catalogs apply to the whole server: super-admins only when there are several customers) |

The permissions are granted to the Super-Admin and Admin roles only. Catalog changes, overrides, setup checks and
enrollment scripts are written to the audit log (`Audit` plugin).

## REST API

Device endpoints (signed with `X-Request-Signature` = SHA-1 of `hash.secret` + device number, like the
configuration request), under `/rest/plugins/deviceprofile/public`:

| Method and path | Content |
|---|---|
| `POST /{number}/profile` | The device profile; the answer gives the active catalog version. A changed API level makes the server send the device a new configuration |
| `POST /{number}/report` | `{"catalogVersion", "results": [{"capabilityId", "outcome", "strategyId", "detail"}], "setup": [{"stepId", "state", "detail"}]}` |
| `GET /{number}/catalog/{version}` | The active catalog; the response is signed (`X-Response-Signature`) |

Console endpoints under `/rest/plugins/deviceprofile/private`: `GET /fleet`, `GET /devices/{id}`,
`POST /devices/{id}/setupCheck`, `GET /configurations`, `GET /configurations/{id}/check`, `GET /applications`,
`POST /applications/analyze`, `POST /applications/{versionId}/overrides`, `GET /catalogs`, `GET /catalogs/{version}`,
`POST /catalogs`, `POST /catalogs/{version}/activate` (`builtin` for the built-in catalog),
`GET /enrollment/script?configurationId=&format=sh|bat&steps=`.

The configuration sent to devices gets an `adaptive` field: `catalogVersion` (the active catalog),
`profileRequested` (the device should send its profile) and `applicationOverrides`.

## Database

| Table | Content |
|---|---|
| `plugin_deviceprofile_profiles` | The last profile of each device and its resolved capabilities |
| `plugin_deviceprofile_results` | The last reported outcome per device and capability |
| `plugin_deviceprofile_setup` | The last reported state per device and setup step |
| `plugin_deviceprofile_history` | Profile history and rule mismatches |
| `plugin_deviceprofile_catalog` | Uploaded catalogs and the active one |
| `plugin_deviceprofile_appversions` | API levels and certificates read from the APK files, and overrides |

## Tests

`buttermdm/deviceprofile-regression.py` tests the endpoints, the configuration check, the application selection,
catalogs, enrollment scripts, audit and permissions against a test server (usage in the script header). Unit tests:
`CapabilityResolverTests` (with the shared test vectors) and `ApkSigningBlockTests`.
