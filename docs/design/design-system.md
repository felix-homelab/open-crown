# Design language

Status: normative. Open Crown is an original app with an Apple-quality feel. It
is not an Apple app, does not use Apple trademarks, and does not redistribute
Apple assets.

## 1. Identity

- Name: **Open Crown**.
- Mark: the original digital-crown line drawing already in the app. It is a
  crown seen from the side, not an Apple Watch silhouette and not an Apple logo.
- Voice: calm, precise, adult. It explains limits without apologising and never
  pretends a feature works.
- No Apple wordmarks, no Apple logos, no "Watch" app iconography copied from
  Apple, no SF Symbols, no SF Pro font files.

## 2. Spatial hierarchy

The screen reads top to bottom as: navigation title, primary subject, grouped
detail, action. Use `CrownScaffold` for large titles that collapse into a
compact bar, an optional back chevron, and trailing actions.

- 8pt grid. Screen margins 20dp; grouped list cards span the width minus 16dp
  and use a 12dp radius.
- Grouped background is a light grey (#F2F2F7) in light mode and near-black
  (#000000 or #1C1C1E) in dark mode. Cards are #FFFFFF / #1C1C1E.
- Separators are hairline (0.5dp equivalent), inset to the text leading edge,
  never full-bleed between rows in the same group.
- Minimum row height 48dp; tappable area at least 44x44dp.
- Generous whitespace between groups (28-32dp), tighter inside a group (0dp
  between rows, 16dp horizontal padding).
- One primary action per screen. Bottom action areas respect the navigation
  insets and use a filled button, with a tinted or plain secondary action.

## 3. Typography

A carefully tuned system sans, not an Apple font file. Sizes and weights:

| Token | Size / line height | Weight | Use |
| --- | --- | --- | --- |
| largeTitle | 34 / 41 | Bold | collapsed top-level title |
| title1 | 28 / 34 | Bold | onboarding headline |
| title2 | 22 / 28 | Semibold | card headline |
| title3 | 20 / 25 | Semibold | section headline |
| headline | 17 / 22 | Semibold | row title |
| body | 17 / 22 | Regular | primary prose |
| callout | 16 / 21 | Regular | secondary prose |
| subhead | 15 / 20 | Regular | row subtitle |
| footnote | 13 / 18 | Regular | captions, provenance |
| caption1 | 12 / 16 | Regular | metadata |
| caption2 | 11 / 13 | Medium | badges |

Tracking is slightly tight on display sizes (-0.4 to -1.0sp) and neutral on
body. Use `sp`, never fixed `dp` text, so Dynamic Type scales. Layouts must
survive 200% font scale: rows wrap, badges move below, buttons grow.

## 4. Colour

Restrained, semantic, and original:

- tint / system blue: #007AFF light, #0A84FF dark
- success: #34C759 / #30D158
- warning: #FF9500 / #FF9F0A
- critical: #FF3B30 / #FF453A
- label: #1D1D1F / #F5F5F7
- secondary label: #6E6E73 / #AEAEB2
- tertiary label: #8E8E93 / #8E8E93
- separator: black at 8% / white at 12%

Colour never carries meaning alone: a status pill has a label and an icon.
Accent colour is used for actions and live state; unavailable state uses
secondary label plus a reason, not red.

## 5. Components

- **Inset grouped list**: `CrownInsetGroup`, `CrownRow`, `CrownSwitchRow`,
  `CrownValueRow`, `CrownDisclosureRow`.
- **Segmented control**: custom pill with an animated selection indicator and
  selectable semantics.
- **Buttons**: filled, tinted, plain, destructive; 50dp height; 12dp radius;
  press scale 0.98 with a spring, disabled at 38% opacity.
- **Sheets**: rounded top corners (large detent), grabber, title, content,
  bottom action; dismissible by drag or scrim.
- **Progress**: ring for a determinate phase; bar for transfer; indeterminate
  only while a probe is genuinely running.
- **Status pill**: neutral, info, success, warning, critical; includes a short
  word, not just a dot.
- **Skeletons**: shimmer that freezes under reduced motion; only for running
  probes, never for a stable unavailable state.
- **Empty state**: original line icon, title, one-sentence explanation, one
  action.

## 6. Motion

- Navigation push: slide from trailing edge with a spring (stiffness ~380,
  damping ~0.86); pop mirrors it. Interruptible; no blocking animations.
- Sheet: spring up, scrim fades.
- Selection indicators: spring, 220-280ms.
- Haptics: light impact on selection, soft on toggle, warning notification on
  failure, success notification only on a genuinely observed completion.
- Reduced motion (`LocalMotionDurationScale == 0` or system animator scale off):
  durations near zero, no shimmer, cross-fade only.

## 7. Accessibility

- Every icon that conveys meaning has a `contentDescription`; decorative icons
  are `null`.
- Headings use `semantics { heading() }`.
- Switches expose toggleable state; segmented controls expose selectable state.
- Row tap targets are at least 48dp; the whole row is clickable, not just text.
- Focus order follows visual order.
- Contrast: label, secondary and destructive text on page/card surfaces >= 4.5:1;
  status pills use label text with tone carried by dot/surface; tertiary text is
  not used for essential information. Checked by tools/design/contrast.py.
- No information is conveyed by colour alone.
- Text wraps; no fixed-height rows for body text.

## 8. Content rules

- Say what is true: "Not connected", "No watch paired", "Blocked: Apple account
  service", "Built, not yet verified".
- Never use "Coming soon" for something blocked; name the dependency.
- Never show an Apple feature name as if Open Crown provides it when it does
  not. Use neutral descriptions and the blocker id in diagnostics.
- Numerals and units use the locale's conventions; dates use the user's locale.
- No placeholder watch names or fake battery percentages.

## 9. Screen inventory

1. Welcome / onboarding (three pages, no fake pairing).
2. Home / watch status and connection health.
3. Discovery (real BLE scan when permitted).
4. Pairing (PIN/optical entry, unavailable state when transport is absent).
5. Setup progress (checkpoint rows).
6. Faces (inventory and editing; unavailable until transport).
7. Notifications (per-app mirroring; Android listener permission).
8. Health (activity snapshot; unavailable until transport).
9. Apps and software (installed apps; blocked rows explained).
10. Settings (grouped, with accessibility and diagnostics).
11. Diagnostics (real probes and timestamps).
12. Evidence (feature registry with filters and blocker detail).
13. About, privacy and licences.
