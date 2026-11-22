# A52 diagnostics export

Date: 2026-11-22 15:18 Europe/Berlin
Device: Samsung SM-A528B (a52sxq), Android 14 / SDK 34.
Build: Open Crown debug APK from this tree, versionName 0.1.0.
Serial: withheld.
Apple Watch: not present.

## What was done

1. Opened Settings -> Diagnostics.
2. Tapped **Export diagnostics**, which wrote a text report to the app cache
   and opened the Android share sheet.
3. Read the generated file through run-as and checked its contents.

## Observation

The share sheet appeared with the file
opencrown-diagnostics-20261122-151809.txt. The report contained:

~~~text
Open Crown diagnostics
generated: 2026-11-22 15:18:09 +0100
app: 0.1.0
Redacted: serial number, device identifiers, pairing keys, Apple account
material, notification content and health data.

[device]
model: SM-A528B
device: a52sxq
android: 14 (SDK 34)
abi: arm64-v8a
kernel: 5.4.254-qgki-28575149-abA528BXXSBGYI3
boot-count: 74
selinux: u:r:untrusted_app:s0:c69,c257,c512,c768

[bluetooth]
...
[privilege]
...
[pair-and-link]
pair-records: 0
...
[rust-core]
available: true
protocol-revision: 0
...
[registry]
total: 226
blocked: 32
implemented_unverified: 28
unimplemented: 163
verified: 3
~~~

Screenshot: [a52-diagnostics-export.png](a52-diagnostics-export.png).

## Redaction checks

- No device serial or serial-like identifier.
- No pairing key, bond material or Apple account material.
- No notification title/text or health sample.
- The report says what was redacted.

## Limits

- The share target is selected by the user; once shared, the receiving app's
  handling is outside Open Crown's control.
- The file stays in the app cache until app data is cleared or the system
  evicts it.
- This is a support artifact, not a full evidence bundle; registry rows and
  blocker documents are not embedded.
