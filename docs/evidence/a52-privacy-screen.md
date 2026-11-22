# A52 privacy screen: local data and live permissions

Date: 2026-11-22 15:15 Europe/Berlin
Device: Samsung SM-A528B (a52sxq), Android 14 / SDK 34.
Build: Open Crown debug APK from this tree, versionName 0.1.0.
Serial: withheld.
Apple Watch: not present.

## What was done

Opened **Settings -> Privacy and permissions** and inspected the rendered state.

## Observation

~~~text
Privacy
What Open Crown knows and keeps

Local by default
Open Crown has no account system, no analytics and no server.

Data on this phone
Pairing metadata      sealed with Android Keystore
Notification content  memory only, capped, never sent to a watch
Diagnostics           model, Android, kernel and probes; no serial or key
Health and activity   not collected
Apple account        never requested, stored or transmitted

Permissions
Nearby devices        Granted
Notification access   Not granted
Camera                Not requested
~~~

Screenshot: [a52-privacy-screen.png](a52-privacy-screen.png).

## Conclusion

- The privacy document and in-app screen exist and reflect live permission
  state. "Notification access: Not granted" is read from Android, not a
  hardcoded placeholder.
- The screen makes no claim that pairing works and lists current limits.
- This verifies the privacy surface on one device; it does not constitute a
  legal review, an independent audit, or a claim of distribution readiness.
