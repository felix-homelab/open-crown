# A52 recovery drill: app reinstall

Date: 2026-11-22 15:10 Europe/Berlin
Device: Samsung SM-A528B (a52sxq), Android 14 / SDK 34.
Build: Open Crown debug APK from this tree.
Serial: withheld.
Apple Watch: not present.

## What was done

1. Confirmed no notification-listener access was granted and no relay was live.
2. Uninstalled the app and reinstalled the debug APK with
   adb install --no-streaming -r -g.
3. Launched it and inspected the first screen.
4. Opened Settings -> Diagnostics and read the Rust core section.

## Observations

- The app returned to onboarding: Open Crown / "Get started" and the honest
  "This build cannot pair yet" note. Onboarding state did not survive
  reinstall.
- Notification-listener access was absent before and after reinstall.
- After tapping Get started, Diagnostics still reported:

~~~text
UniFFI                 loaded
                       UniFFI loaded; protocol revision 0
~~~

Screenshot: [a52-recovery-reinstall.png](a52-recovery-reinstall.png).

## Conclusion

- Reinstall is a clean recovery path for app-local state.
- The native core is packaged and loads after reinstall.
- This does not test Watch unpair, Watch reset, a populated pair store, corrupt
  sealed state, or an app update over existing data. Those remain open recovery
  gates because there is no Watch and no pairing implementation yet.
