# A52 app-update path

Date: 2026-11-22 15:33–15:34 Europe/Berlin
Device: Samsung SM-A528B (a52sxq), Android 14 / SDK 34.
Build: Open Crown debug APK from this tree.
Serial: withheld.
Apple Watch: not present.

## Setup

- Completed onboarding.
- Granted notification-listener access.
- Captured a synthetic Shell notification.
- Muted Shell in the per-app policy.

## Update

Installed the same APK over the existing app with adb install -r, using the same
debug signing key. Relaunched.

## Result

- The app opened on **Watch**, not Welcome: onboarding completion survived the
  update.
- Settings -> Notifications still showed:

~~~text
Captured · 0
Apps
com.android.shell        Muted · com.android.shell
~~~

- A new Shell notification posted after the update was not captured, so the
  policy survived the process restart and package replacement, not just the
  in-memory UI state.

Screenshot: [a52-update-path.png](a52-update-path.png).

## Limits

- No pair record existed, so sealed pairing metadata preservation was not
  tested. The pair store is app-private and survives install -r by design, but
  this run does not prove it with real bond material.
- This used the same debug signing key. A different key requires uninstall, so
  it is not an update path.
- Notification access is a system grant; it was revoked after the test and is
  not preserved by the app's own update logic.
