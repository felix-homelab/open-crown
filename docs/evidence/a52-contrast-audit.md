# A52 contrast audit and status-pill fix

Date: 2026-11-22 15:54 Europe/Berlin
Device: Samsung SM-A528B (a52sxq), Android 14 / SDK 34, dark appearance
Build: debug APK from this tree
Apple Watch: not present.

## Audit

[tools/design/contrast.py](../../tools/design/contrast.py) parsed CrownColors.kt
and checked the required tokens in both themes. Required failures: **0**.

The full table and informational exceptions are in
[design/contrast.md](../design/contrast.md).

## Fix

- Status pill text now uses the label token instead of the tone colour.
- Destructive row text uses a new destructiveText token (5.15:1 light, 6.03:1
  dark on cards) while the destructive fill stays Apple-red.

## Physical check

Home on the A52 after the change shows Done / Waiting / Blocked labels in the
label colour, with the tone still carried by the dot and tinted surface.

Screenshot: [a52-contrast-pills.png](a52-contrast-pills.png).

## Limits

- This is a token-level contrast audit, not a full APCA or screenshot pixel
  audit. Opacity, gradients and anti-aliasing are not measured.
- Tertiary light text remains below 4.5 and is documented as non-essential.
- Button accents pass only the large-text threshold; a strict normal-text target
  would require palette changes.
