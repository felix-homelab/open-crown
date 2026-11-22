# A52 per-app notification capture policy

Date: 2026-11-22 15:20 Europe/Berlin
Device: Samsung SM-A528B (a52sxq), Android 14 / SDK 34.
Build: Open Crown debug APK from this tree.
Serial: withheld.
Apple Watch: not present.

## What was done

1. Granted notification-listener access and opened **Settings -> Notifications**.
2. Posted a synthetic shell notification; it was captured.
3. Muted the Shell app with the per-app switch.
4. Posted a second notification; the captured count stayed at zero and the
   second notification did not appear.
5. Unmuted Shell and posted a third notification; it was captured.

## Observation

~~~text
Captured · 1
Policy test                    Local
Shell · First notification · 15:20

Apps
Shell                          Capturing
~~~

After muting:

~~~text
Captured · 0
Apps
com.android.shell              Muted · com.android.shell
~~~

After unmuting and reposting:

~~~text
Captured · 1
Shell · Third notification after unmute · 15:20
Shell                          Capturing
~~~

Screenshot: [a52-notification-policy.png](a52-notification-policy.png).

## Conclusion

- Per-app capture policy works on the A52: muting removes that app's captured
  rows and suppresses new ones; unmuting resumes capture.
- This is a **local capture filter only**. Notification mirroring, actions and
  replies still require a watch transport that does not exist.
- Listener access was revoked after the test.
