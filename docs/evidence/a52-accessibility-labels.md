# A52 accessibility labels

Date: 2026-11-22 15:56–16:03 Europe/Berlin
Device: Samsung SM-A528B (a52sxq), Android 14 / SDK 34, dark appearance
Build: debug APK from this tree
Apple Watch: not present.

## Why

TalkBack is installed as com.samsung.android.accessibility.talkback. It was bound
once during this probe with requestTouchExplorationMode=true; the TalkBack
welcome tutorial prevented a full spoken traversal, and the service was disabled
afterwards. The app tree was inspected with UiAutomator instead.

## Before

Home exposed three clickable nodes with no text and no content-description:
refresh, settings, and the primary Pair a watch button. Settings exposed eleven
unlabeled clickable rows.

## Change

- Icon-only buttons now carry an explicit content description on the clickable
  node.
- Primary buttons expose their label, role and click action in one node.
- Clickable rows expose title + subtitle and a button click action; decorative
  children are hidden from the accessibility tree.
- Switch rows no longer make the whole row an unlabeled click target; the
  switch carries its own description/state/action. That last part is
  implemented but was not re-confirmed in a captured tree after the latest
  capture issue.

## After

~~~text
Home:      3 clickable, 0 unlabeled
Settings: 11 clickable, 0 unlabeled

Home labels: Refresh observations, Settings, Pair a watch
Settings:    Status and connection, Watch faces, Notifications, …
~~~

## Limits

- This is a node-tree check, not a spoken TalkBack traversal or audio capture.
- TalkBack tutorial completion, focus order and switch announcement remain
  unverified.
- Only Home and Settings were counted; every screen was not re-audited.
