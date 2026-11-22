# A52 broker: real untrusted_app denial

Date: 2026-11-22 14:33 Europe/Berlin
Device: Samsung SM-A528B (a52sxq), Android 14 / SDK 34, SELinux enforcing.
Build: Open Crown debug APK from this tree, versionName 0.1.0.
Serial: withheld. Boot count at capture: 71.

## What was done

1. Installed the new app and opened **Settings → Diagnostics**.
2. Tapped **Prepare this boot…**, accepted the consent sheet, and let the
   quarantined page-cache chain run from the app.
3. Confirmed the patch applied and triggered init with the documented shell
   command (the app cannot write `ctl.*` properties itself).
4. Verified the relay socket was listening, then restarted the app so its
   initial probe ran against the live relay.

## Raw evidence

Relay listening after the patch and the init log:

```text
$ adb shell cat /proc/net/unix | grep dfi
0000000000: 00000002 00000000 00010000 0001 01 125161 @dfi_init

$ adb shell ls -la /dev/.dfi
-rw------- 1 root root ... /dev/.dfi          # shell has no read access
```

The app process label, from the same boot while the relay was live:

```text
$ adb shell ps -A -Z | grep -i opencrown
u:r:untrusted_app:s0:c68,c257,c512,c768 u0_a324 $PID ... org.opencrown.app
```

The app's own probe result, rendered on Home and saved in
[a52-broker-untrusted-app.png](a52-broker-untrusted-app.png):

```text
Agent channel
SELinux or file permissions denied the connection (EACCES/EPERM)
```

The same probe is visible in
[a52-broker-untrusted-app-ui.xml](a52-broker-untrusted-app-ui.xml), which
contains the literal text above.

For contrast, the Phase 2 probe measured the shell domain separately:

```text
$ adb shell /data/local/tmp/probe getuid
connect(@dfi_init): OK
getuid: raw=0

$ adb shell run-as org.opencrown.app .../probe connect
connect(@dfi_init): FAILED errno=13(EACCES)
```

## Conclusion

- The relay is reachable from `u:r:shell:s0`.
- It is **denied** to the app's real domain, `u:r:untrusted_app:s0`, while the
  socket is live. The denial is an LSM `connectto` refusal; an abstract socket
  has no filesystem path whose DAC could fail.
- The app cannot consume its own per-boot privilege. This is **K2a blocked**,
  not a UI or permission bug.

## Compounding findings

- The relay is an unauthenticated raw-syscall RPC: connect, send a 4096-byte
  frame of six arguments plus a syscall number, receive a 4096-byte reply.
  Anyone permitted to connect owns arbitrary syscalls as uid 0 in init.
- After bind/listen it never returns to its init caller; the init thread stays
  in `accept()`. Property service and logd stop responding; the phone
  watchdog-resets within minutes. See
  [broker-probe-a52.md](broker-probe-a52.md).
- There is no unprivileged trigger for the chain. The app can patch the page
  cache, but it cannot make init log, and it cannot connect afterwards.

## Recovery

The phone reset on its own. After reboot the relay and the patch are gone. No
boot image, partition or package was modified by the chain. The app was
reinstalled after a package-manager wedge caused by the hijacked init thread;
that wedge is itself evidence that the relay is not production-safe.

## What would change the verdict

A different privilege mechanism that (a) yields a domain allowed to
`connectto` an app-facing socket, (b) does not consume init, and (c) has an
authenticated, allowlisted protocol. None of those is true here.
