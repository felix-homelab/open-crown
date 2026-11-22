# A52 chain in the Open Crown app

The A52 page-cache chain is built into the app:

- native: `app/src/main/cpp/a52root/a52_pagecache.c`
- library: `app/src/main/jniLibs/arm64-v8a/liba52root.so`
- payload: `app/src/main/assets/a52_payload.bin` (576-byte A52 relay)
- Kotlin: `app/src/main/kotlin/org/opencrown/app/root/A52RootChain.kt`
- UI: the "A52 temporary root" section in `MainActivity.kt`

On-device app run (SM-A528B, 5.4.254):

```text
ELF: FOUND _ZN7android4base10LogMessageD2Ev
Function offset: 0x18778
Payload fits: YES (580 <= 696)
Phase 2 complete: 35 writes OK, 0 failed
Phase 3 OK: ACTIVATED
Verified: 576/576 bytes match
*** PAGE CACHE MODIFIED — PAYLOAD DEPLOYED ***
*** Relay will activate on next init LOG() call ***
init trigger denied: Failed to set property 'debug.dirtyinit'
```

The app's property write is denied by SELinux, as expected for
`untrusted_app`, so the UI instructs the operator to trigger init over ADB.
With the trigger applied, the relay was verified over ADB:

```text
uid=0(root) gid=0(root) groups=3009(readproc) context=u:r:init:s0
```

The app cannot connect to the init relay socket itself; `untrusted_app` is
excluded from that Unix socket. The app therefore stages the chain and the
operator consumes the root command relay over ADB. The relay and page-cache
patch disappear at reboot.
