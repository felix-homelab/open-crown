# Security notes

Status: current for the private research build. This is an engineering document,
not an independent audit or a warranty.

## 1. Trust boundaries

| Boundary | Mechanism | Current state |
| --- | --- | --- |
| App ↔ Rust core | UniFFI/JNA, in-process | Verified loading on the A52; protocol revision 0 |
| App ↔ Android platform | Normal app permissions and app-private storage | Listener and Bluetooth permissions are explicit |
| App ↔ Watch | None | No transport, pairing or agent exists in the product |
| Privileged chain | Quarantined in-tree page-cache payload | Research only; not a product feature |
| Reference project | Quarantined outside the build | Gitignored; CI boundary guard |

The app runs as an ordinary Android app. It does not hold root, does not ship a
resident privileged agent, and does not expose a listening local socket or
exported privileged component. The notification listener is an Android-bound
service protected by the system-only
BIND_NOTIFICATION_LISTENER_SERVICE permission.

## 2. Secrets

- Pairing records are sealed with Android Keystore AES-GCM; the key is
  non-exportable.
- The app does not request or store an Apple account password, token or
  two-factor code.
- Notification content is memory-only and excluded from diagnostics.
- The repository has a CI secret/signing-material scan
  (tools/cleanroom/secret-scan.py) that runs over tracked files.
- The page-cache chain contains fixed AES/HMAC calibration keys; they are
  payload constants, not user or Apple secrets, and are documented in
  docs/cleanroom/PROVENANCE.md.

## 3. Privileged payload

The in-tree page-cache chain is **quarantined research code**:

- Its provenance review is open.
- It is consent-gated in Diagnostics and is not run automatically.
- It yields an unauthenticated init-domain raw-syscall relay that the app cannot
  reach; the relay also destabilises the phone.
- It is not part of the clean-room product. Release builds disable the CMake
  target and package the payload only in debug assets; the release was
  inspected on the A52 and contains neither. The Kotlin class and consent UI
  remain compiled but are gated by BuildConfig and cannot execute without the
  native library and payload.
- The provenance finding and K2a still must be resolved before the research
  chain can be treated as anything other than quarantined.

## 4. Supply chain

- Rust dependencies are vetted by cargo-deny; Kotlin dependencies are pinned in
  the version catalog and resolved from Google/Maven Central.
- No reference source, generated code or Apple-owned binary is built into the
  app.
- The reference boundary and generated-binding freshness are CI checks.
- The release APK is currently debug-signed for bench use only. Production
  signing and reproducible builds are open gates.

## 5. Known gaps

- No independent security audit or penetration test.
- No fuzzing of protocol decoders yet.
- JNA/UniFFI native surface has not been hardened beyond R8 keep rules and
  normal Android packaging.
- The clean-room payload provenance finding remains open.
- No Watch transport exists, so its attack surface is not yet designed in code.

## 6. Reporting

Follow [SECURITY.md](../SECURITY.md). Redact device identifiers, pairing
material, Apple account data and notification content.
