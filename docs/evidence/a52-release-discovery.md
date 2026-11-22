# A52 release discovery: Apple advertisements, not watches

Date: 2026-11-22 15:47 Europe/Berlin
Device: Samsung SM-A528B (a52sxq), Android 14 / SDK 34.
Build: bench-signed release APK from this round (no privileged relay or payload).
Serial: withheld.
Apple Watch: not present.

## What was done

Granted Nearby devices, launched the release build, opened **Pair a watch** and
let the scan run for about 14 seconds.

## Observation

The screen reported nearby Apple advertisements, not watches. A representative
result list:

~~~text
Seen nearby
Apple advertisement   Type 0x09 · RSSI -29 dBm · seen 65×   Not selectable
Apple advertisement   Type 0x12 · RSSI -40 dBm · seen 6×    Not selectable
Apple advertisement   Type 0x10 · RSSI -44 dBm · seen 40×   Not selectable
Apple advertisement   Type 0x12 · RSSI -45 dBm · seen 7×    Not selectable
Apple advertisement   Type 0x12 · RSSI -50 dBm · seen 6×    Not selectable
Apple advertisement   Type 0x10 · RSSI -51 dBm · seen 39×   Not selectable
~~~

Screenshot: [a52-release-discovery.png](a52-release-discovery.png).

## Conclusion

- The release artifact performs real unprivileged BLE observation and renders
  live advertisements.
- It does not promote an advertisement to a watch, a pair or a connection. Every
  result is non-selectable because the FE25 setup metadata is UNKNOWN.
- This is the same honest boundary as the debug build, now verified on the
  release package.

## Limits

- No Apple Watch was present; this verifies observation only.
- Nearby devices permission was revoked and the app force-stopped after the test.
