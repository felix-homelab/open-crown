# Contrast audit

Required target from the design language: **label and secondary text on page
and card surfaces >= 4.5:1 in light and dark**.

Tool: [tools/design/contrast.py](../../tools/design/contrast.py), which parses
CrownColors.kt rather than duplicating the palette. CI runs it.

## Required checks

| Theme | Text | Background | Ratio | Result |
| --- | --- | --- | ---: | --- |
| Light | label | grouped | 15.08 | pass |
| Light | label | card | 16.83 | pass |
| Light | secondary | grouped | 4.54 | pass |
| Light | secondary | card | 5.07 | pass |
| Light | destructiveText | grouped | 5.15 | pass |
| Light | destructiveText | card | 5.75 | pass |
| Dark | label | grouped | 19.29 | pass |
| Dark | label | card | 15.63 | pass |
| Dark | secondary | grouped | 8.16 | pass |
| Dark | secondary | card | 6.61 | pass |
| Dark | destructiveText | grouped | 7.45 | pass |
| Dark | destructiveText | card | 6.03 | pass |

Required failures: 0.

## Changes made

- Status pills now render their label in the high-contrast label token. The
  tone is carried by the dot and tinted surface; colour is not the only signal.
- Destructive row text uses a dedicated destructiveText token
  (5.15:1 light / 6.03:1 dark on cards) instead of the Apple-red fill token
  (3.55:1 light).

## Informational exceptions

| Item | Light | Dark | Disposition |
| --- | ---: | ---: | --- |
| tertiaryLabel on card | 3.26 | 5.22 | Non-essential placeholder/caption per design spec; not used for essential information |
| tint text/link | 4.02 | 4.66 | Large/semibold controls use the 3.0 large-text threshold; small tinted text should be avoided |
| onTint on tint | 4.02 | 3.65 | Button labels are 17sp headline/semibold; pass the large-text threshold, not the 4.5 normal-text threshold |
| destructive fill | 3.55 | 4.99 | Fill/dot only; destructive text uses destructiveText |
| success / warning | 2.22 / 2.20 | 8.42 / 8.28 | Fill/dot only after the pill change |

These are recorded rather than rounded away. A strict normal-text 4.5 target
would require darkening tint, success and warning in light mode, which changes
the Apple-derived accent palette; that remains a product decision.
