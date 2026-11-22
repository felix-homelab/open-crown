# A52 discovery: advertisements observed, none selectable

Date: 2026-11-22 14:48 Europe/Berlin
Device: Samsung SM-A528B (a52sxq), Android 14 / SDK 34.
Build: Open Crown debug APK from this tree, versionName 0.1.0.
Serial: withheld. Boot count: 73.
Apple Watch: not present.

## What was done

1. Opened **Home → Pair a watch** with Nearby devices permission granted.
2. Let the unprivileged BLE scan run for eight seconds.
3. Inspected the rendered list and the UI hierarchy.

## Raw observation

The app listed five Apple manufacturer advertisements:

```text
Seen nearby
Apple advertisement   Type 0x09 · RSSI -38 dBm · seen 31x   Not selectable
Apple advertisement   Type 0x12 · RSSI -40 dBm · seen 3x    Not selectable
Apple advertisement   Type 0x12 · RSSI -42 dBm · seen 3x    Not selectable
Apple advertisement   Type 0x09 · RSSI -43 dBm · seen 33x   Not selectable
Apple advertisement   Type 0x10 · RSSI -43 dBm · seen ...   Not selectable
```

The screen also states: *"The FE25 setup-metadata layout is UNKNOWN, so this
build can list nearby Apple advertisements but cannot identify a pairable Watch
or offer a selection."*

Screenshot: [a52-discovery.png](a52-discovery.png).

## Conclusion

- Unprivileged Android BLE observation works on the A52 and the UI reflects
  real radio observations.
- No row was selectable. This is intentional: Apple manufacturer data alone is
  not a pairable watch (Lane A spec 01-discovery, selection rule 4), and the
  FE25 layout is not established.
- No Apple Watch was present, so this does **not** verify Watch detection,
  model/OS decoding, or pairing. Those remain unimplemented.

## Registry effect

`foundation.ble-observation` is verified on this hardware for the narrow claim
"scan and list Apple advertisements without selecting them". Every watch-scoped
row remains unimplemented or blocked.
