# Melon Maintenance Gate Plugin (`plugins/melon-maintenance`)

The **Melon Maintenance Gate** plugin (`melon-maintenance`) coordinates non-emergency Android device maintenance (APK installations, agent self-updates, device reboots, and OS updates) between the ButterMDM agent (`com.buttermdm.agent` 0.1.0+) and the installed Melon POS terminal application (`IMaintenanceGate` bound service).

When `gateEnabled` is `false` (default for non-POS configurations), the ButterMDM agent behaves as without the plugin and does not bind to any external service.

---

## Supported POS Program Packages

Each Melon POS terminal runs one of five program packages depending on its store configuration:

- `com.melon.pos.grocery` (Melon Grocery)
- `com.melon.pos.retail` (Melon Retail)
- `com.melon.pos.fsr` (Melon Full Service Restaurant)
- `com.melon.pos.qsr` (Melon Quick Service Restaurant)
- `com.melon.pos.gas.station` (Melon Gas Station)

These are the packages the server accepts as `gatePackage` (`MelonMaintenanceSettings.ALLOWED_GATE_PACKAGES`) and the agent
plugin binds to (`MelonMaintenanceConfig.ALLOWED_POS_PACKAGES`). All five applications export the same `com.melon.pos.action.MAINTENANCE_GATE` bound service (`IMaintenanceGate.aidl`). The `gatePackage` field in `melonMaintenance` selects which program package the ButterMDM agent binds to on a given configuration.

---

## Device Sync Block (`melonMaintenance`)

When the plugin is enabled for a customer/configuration, `MelonMaintenanceSyncResponseHook` appends the `melonMaintenance` JSON block to the device sync response (`GET /rest/public/sync/configuration/{deviceNumber}`):

```json
{
  "melonMaintenance": {
    "gateEnabled": true,
    "gatePackage": "com.melon.pos.grocery",
    "gateSignerSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    "askTimeoutSeconds": 10,
    "minRetrySeconds": 300
  }
}
```

### Bilateral Authentication & Fail-Closed Rules

1. **Agent verifies POS (`B-1`):** Before binding to `<gatePackage>` with action `com.melon.pos.action.MAINTENANCE_GATE`, the ButterMDM agent checks via `PackageManager.GET_SIGNING_CERTIFICATES` that the target package is signed by `gateSignerSha256`.
2. **POS verifies Agent (`B-1`):** `IMaintenanceGate.Stub` verifies `Binder.getCallingUid()` package name (`com.buttermdm.agent`) and signing certificate SHA-256 against the signed store profile's `mdmAgentSignerSha256`.
3. **Fail-closed (`B-2`):** If `<gatePackage>` is missing (except during initial enrollment when POS is not yet installed), fails signer verification, fails to bind, or does not answer within `askTimeoutSeconds` (`10 s`), the agent does **not** install, update, or reboot and schedules a retry after `minRetrySeconds` (`300 s`).
4. **Immediate security actions (`G4-5`):** Remote lock and remote factory reset bypass the maintenance gate and execute immediately.

---

## REST API

### Device Public Endpoint (Signed with `X-Request-Signature`)

| Method | Path | Description |
|---|---|---|
| `POST` | `/rest/plugins/melon-maintenance/public/report/{number}` | Records the latest maintenance gate decision (`GRANTED` or `DENIED`), `reason` (one of the 20 `MaintenanceReason` codes), `leaseId`, `outcome` (`DONE`, `FAILED`, `ABORTED`), `deferralCount`, and `nextRetryAt`. Automatically sets `threeNightAlertActive = true` when `deferralCount >= 3`. Verified by `CryptoUtil.checkRequestSignature(X-Request-Signature, hash.secret + number)`. |

### Console / Administrator Private Endpoints (JWT + Role Permissions)

| Method | Path | Permission | Description |
|---|---|---|---|
| `GET` | `/rest/plugins/melon-maintenance/private/settings` | `plugin_melon_maintenance_access` | Fetch customer maintenance gate configuration (`gateEnabled`, `gatePackage`, `gateSignerSha256`, `askTimeoutSeconds`, `minRetrySeconds`). |
| `PUT` | `/rest/plugins/melon-maintenance/private/settings` | `plugin_melon_maintenance_settings` | Update customer maintenance gate configuration. Validates `gatePackage` and 64-hex `gateSignerSha256`. |
| `GET` | `/rest/plugins/melon-maintenance/private/devices` | `plugin_melon_maintenance_access` | List device gate status rows including `lastDecision`, `lastReason`, `deferralCount`, `threeNightAlertActive`, and `nextRetryAt`. |
| `POST` | `/rest/plugins/melon-maintenance/private/devices/{deviceId}/force-install` | `plugin_melon_maintenance_force_install` | Audited technician break-glass override (`B-10`). Requires a non-empty `ticketRef` and `reason`, records an immutable audit row, and dispatches a `melonForceInstall` push command. |
