# A52 notification ingress: local capture only

Date: 2026-11-22 14:57 Europe/Berlin
Device: Samsung SM-A528B (a52sxq), Android 14 / SDK 34.
Build: Open Crown debug APK from this tree, versionName 0.1.0.
Serial: withheld. Boot count: 73.
Apple Watch: not present.

## What was done

1. Granted notification-listener access to the app's
   `CrownNotificationListener` through Android's own permission mechanism.
2. Opened **Settings → Notifications**.
3. Posted one synthetic notification from the shell:
   `cmd notification post -t 'Ingress test' crown_test3 'A synthetic notification for local capture'`.
4. Observed the captured row, then revoked listener access and cleared the
   notification.

## Raw observation

```text
Notifications
Local ingress, no delivery

Captured locally
Notifications are held in memory on this phone. Nothing is delivered to a
watch: no transport exists in this build.

Captured · 1
Ingress test                                  Local
Shell · A synthetic notification for local capture · 14:57

Delivery is still blocked
No watch is paired. Local ingress is not mirroring; the watch link is the
missing half.
```

Screenshot: [a52-notification-ingress.png](a52-notification-ingress.png).

## Conclusion

- Android notification ingress works on the A52, including title, text, app
  label, time, package identity and bounded sanitisation.
- Nothing was sent to a watch and the UI says so on the same screen.
- Notification content is memory-only: no database, no file, no log. Revoking
  the listener stops capture.
- This verifies **local ingress**, not mirroring, actions, replies or
  attachments. Those remain unimplemented or blocked by the missing transport.

## Privacy notes

- The test notification is synthetic.
- The service ignores the app's own notifications.
- Content is capped at 120 characters for the title and 400 for the text, and
  control characters are removed before display.
- Access was revoked immediately after the capture.
