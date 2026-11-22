# A52 design-system verification: light appearance and Dynamic Type

Date: 2026-11-22 14:49 Europe/Berlin
Device: Samsung SM-A528B (a52sxq), Android 14 / SDK 34.
Build: Open Crown debug APK from this tree, versionName 0.1.0.
Serial: withheld.
Apple Watch: not present.

## What was done

1. Forced light appearance (`cmd uimode night no`) and captured Home.
2. Raised the system font scale to 1.3 and captured the same screen.
3. Restored dark appearance and font scale 1.0 afterwards.

## Observations

- Light appearance renders the grouped-grey background, white inset cards,
  hairline separators, tinted secondary buttons and semantic status pills.
  See [a52-design-light.png](a52-design-light.png).
- At 1.3x Dynamic Type, row titles and subtitles wrap instead of clipping,
  status pills stay on the row, buttons grow, and the content remains
  scrollable. See [a52-design-large-text.png](a52-design-large-text.png).
- No placeholder watch value appeared in either mode. Unavailable rows kept
  their reason text.
- The settings and refresh actions stayed reachable in the compact title bar.

## Not verified

- VoiceOver/TalkBack traversal was not exercised.
- Reduced-motion behaviour was not visually captured; the implementation reads
  Android's animator setting and snaps animations, but this note does not claim
  a physical verification.
- Tablet/landscape layouts are out of scope for the current portrait shell.
- Contrast was reviewed visually, not measured with a tool.

This is evidence for appearance and text scaling only. It does not move any
watch capability row to verified.
