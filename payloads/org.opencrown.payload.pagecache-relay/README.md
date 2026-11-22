# Page-cache Relay

Own-name payload for the Open Crown page-cache family.

- Entry: `payload.bin` (576 bytes)
- Family: page-cache / init relay
- Privilege: `uid 0` in `u:r:init:s0`
- Lifetime: current boot only

## Verified profile

| Device | SoC | Kernel | Build | Android |
| --- | --- | --- | --- | --- |
| SM-A528B (`a52sxq`) | Snapdragon 778G | `5.4.254-qgki-28575149-abA528BXXSBGYI3` | `A528BXXSBGYI3` | 14 / SDK 34 |

## Specification requirements

- `arm64-v8a`
- Android SDK 31+ (verified at 34)
- Kernel family `5.4-qgki`
- Android init linked against `libbase`
- `/system/lib64/libbase.so` exports `_ZN7android4base10LogMessageD2Ev` with at least 580 bytes for the relay
- `IpSecManager`, XFRM ESP-in-UDP, and the exec page-cache path
- SELinux enforcing init (`u:r:init:s0`)

The native code resolves the target symbol at runtime. No A52 file offset is
stored in the payload; only the verified device/kernel rows are A52-specific.

## Expansion

Add a row to `devices` only after hardware verification. A device that matches
only `profiles` is reported as **compatible, untested** and is not auto-run.
Add a new kernel row when the same libbase/init layout and exploit path are
confirmed. The payload file itself is calibration data for the init relay;
if a future device needs different relay bytes, ship a new payload entry rather
than mutating this one.

## Root consumption

The relay is created inside init. `untrusted_app` cannot connect to it, so the
app triggers it through an ADB property change and the operator uses the relay
from ADB. The app only detects `/dev/.dfi` and displays the success state.
