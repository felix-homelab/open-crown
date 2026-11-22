# A52 discovery driven by the Rust core

Date: 2026-11-22 15:04 Europe/Berlin
Device: Samsung SM-A528B (a52sxq), Android 14 / SDK 34.
Build: Open Crown debug APK with DiscoveryBridge from opencrown-core.
Serial: withheld. Boot count: 73.
Apple Watch: not present.

## What changed

Android still supplies scan callbacks and display-only manufacturer bytes. The
candidate inventory, TTL, opaque tokens and selectable flag now come from the
Rust DiscoveryBridge through UniFFI. Kotlin no longer owns a parallel candidate
list.

## Observation

The app listed nine Apple advertisements, sorted by RSSI, with types 0x09,
0x10 and 0x12 and seen counts preserved across token updates:

~~~text
Seen nearby
Apple advertisement   Type 0x12 · RSSI -30 dBm · seen 5x    Not selectable
Apple advertisement   Type 0x10 · RSSI -49 dBm · seen 26x   Not selectable
Apple advertisement   Type 0x12 · RSSI -50 dBm · seen 2x    Not selectable
...
~~~

Screenshot: [a52-rust-discovery.png](a52-rust-discovery.png).

## Conclusion

- Rust now owns inventory and selection validation, and the app renders only
  what the core reports.
- Every candidate remained non-selectable because FE25 setup metadata cannot be
  decoded yet; there is no path from a raw advertisement to a pairing screen.
- This verifies the wiring and inventory behaviour on hardware. It does not
  verify Watch detection, model/OS decode or selection success, which need
  metadata and a physical Watch.
