# 0017 — A52 page-cache chain as an in-app experiment

Status: accepted for the current private research build.

The A52 (SM-A528B, 5.4.254) temporary-root chain is the page-cache/IPsec
family. The exploit code and payload are built in-tree under
`app/src/main/cpp/a52root/` and `app/src/main/assets/a52_payload.bin`; the app
exposes it from the M1 screen behind an explicit research label.

The chain yields an init-domain command relay (`uid 0`, `u:r:init:s0`) for the
current boot. Android's `untrusted_app` SELinux domain cannot connect to the
relay socket, so the app can stage the exploit but cannot consume the relay
itself. The operator triggers init and uses the relay over ADB. The app does
not silently gain or keep root.

Removing external chain directories means no third-party payload bundle or
external CLI is shipped or linked by the app.
