# 0017 — A52 page-cache chain as an in-app experiment

Status: accepted for the current private research build.

The temporary-root chain is packaged as the named payload
`org.opencrown.payload.pagecache-relay`, not as an A52-only asset. The payload
lives under `payloads/org.opencrown.payload.pagecache-relay/` with a manifest
that lists supported specs, kernels, devices and target/driver requirements.
The native code lives under `app/src/main/cpp/pagecache_relay/`.

The app matches the manifest before running. A verified device row auto-runs;
a specification-only match is shown as compatible and untested. Adding a new
device or kernel is a manifest change plus hardware verification, not an app
change.

The chain yields an init-domain command relay (`uid 0`, `u:r:init:s0`) for the
current boot. Android's `untrusted_app` SELinux domain cannot connect to the
relay socket, so the app can stage the exploit but cannot consume the relay
itself. The operator triggers init and uses the relay over ADB. The app does
not silently gain or keep root.

Removing external chain directories means no third-party payload bundle or
external CLI is shipped or linked by the app.
