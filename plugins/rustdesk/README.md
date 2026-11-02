# ButterMDM RustDesk Integration Plugin (`rustdesk`)

The `rustdesk` plugin integrates **RustDesk (`com.carriez.flutter_hbb`)** and self-hosted RustDesk Server (`hbbs` / `hbbr`) infrastructure directly into ButterMDM.

## Features

1. **Self-Hosted RustDesk Server Discovery & Health Monitoring**:
   - Auto-detects `/opt/rustdesk/id_ed25519.pub` and `/opt/rustdesk/db_v2.sqlite3`.
   - Probes `hbbs` (`:21115`, `:21116`, `:21118`) and `hbbr` (`:21117`, `:21119`) status.
   - Generates official `===` encoded RustDesk configuration strings (`Base64(reverse(JSON))`).
2. **One-Click RustDesk APK Provisioning**:
   - Registers the RustDesk Android APK (`com.carriez.flutter_hbb`) into the ButterMDM application inventory and assigns it (`action = 1`) to target MDM Configurations for silent Device Owner installation.
3. **Zero-Touch Android Device Owner Permission & Accessibility Elevation**:
   - Grants `POST_NOTIFICATIONS`, `RECORD_AUDIO`, `CAMERA`, and storage permissions silently.
   - Automatically enables `com.carriez.flutter_hbb/com.carriez.flutter_hbb.InputService` in `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` for full unattended touch and keyboard control.
   - Pre-grants `SYSTEM_ALERT_WINDOW`, `MANAGE_EXTERNAL_STORAGE`, `PROJECT_MEDIA`, and Doze battery optimization exemption.
4. **Live `hbbs` Peer Correlation**:
   - Reads `/opt/rustdesk/db_v2.sqlite3` (`peer` table) and matches registered RustDesk Peer IDs with enrolled ButterMDM devices.

See [`RUSTDESK-PLUGIN-PLAN.md`](../rustdesk-plan/RUSTDESK-PLUGIN-PLAN.md) for the complete architecture, database schema, REST API specification, and capability matrix.
