# A52 Dynamic Type at 200%

Date: 2026-11-22 15:29–15:30 Europe/Berlin
Device: Samsung SM-A528B (a52sxq), Android 14 / SDK 34.
Build: Open Crown debug APK from this tree.
Font scale: 2.0 (system setting), then restored to 1.0.
Serial: withheld.
Apple Watch: not present.

## Problem

At 200% text, rows with a trailing status pill kept the pill on the same
horizontal line as the title and subtitle. The subtitle was squeezed into a
narrow column and grew very tall; the first capture showed the badge consuming
roughly half the row width.

Before: [a52-large-text-200-before.png](a52-large-text-200-before.png)

## Change

[CrownLists.kt](../../app/src/main/kotlin/org/opencrown/app/ui/components/CrownLists.kt)
now detects font scale at or above 1.5 and, for those rows:

- gives title and subtitle the full row width;
- places the trailing pill/switch/chevron on its own line, aligned to the end;
- allows a value row two lines instead of one.

The change is in the shared row component, so Settings, Privacy, Setup,
Diagnostics and notifications all get it.

## Result

After: [a52-large-text-200.png](a52-large-text-200.png)

- Setup subtitles use the full card width and no longer form a narrow column.
- Status pills remain visible below their explanation.
- The scroll remains usable; content still extends below the fold, as expected
  for 200% text.
- No text was observed clipped horizontally in the captured rows.

## Limits

- This is one screen on one device at 200%, not a full accessibility audit.
- TalkBack traversal, switch roles and focus order were not exercised.
- Landscape at 200% was not re-tested after the change.
