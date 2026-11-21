# A52 binder bootstrap probe — 2026-11-21

- **Device:** Galaxy A52s 5G `SM-A528B`, kernel
  `5.4.254-qgki-28575149-abA528BXXSBGYI3`, Enforcing
- **APK:** `org.opencrown.binderprobe` (research only, separate from Open
  Crown), arm64-v8a, debug
- **Result:** an ordinary app process obtained a binder handle to a binder
  node in a separate `:manager` process and completed a synchronous
  transaction. No exploit code, no root, no panic.

## Commands

```sh
JAVA_HOME=$PWD/.toolchain/jdk/Contents/Home ANDROID_HOME=$PWD/.android-sdk \
GRADLE_USER_HOME=$PWD/.gradle-home .toolchain/gradle-8.14.3/bin/gradle \
  -p research/a52-chain/binder-bootstrap :app:assembleDebug
adb install -r research/a52-chain/binder-bootstrap/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n org.opencrown.binderprobe/.MainActivity
adb logcat -d -s BinderProbe:*
```

## Observed

```text
BinderProbe: binding to ManagerService
BinderProbe: manager process created pid=19270
BinderProbe: manager onBind
BinderProbe: manager transaction value=41
BinderProbe: connected handle=147695331 result=42
```

`dumpsys activity services` shows:

```text
ServiceRecord{... org.opencrown.binderprobe/.ManagerService}
  processName=org.opencrown.binderprobe:manager
  binder=android.os.BinderProxy@51da26b
  requested=true received=true hasBound=true
```

## Native bridge limitation

A JNI `AIBinder_fromJavaBinder()` / `AIBinder_transact()` attempt was also
made. `AIBinder_prepareTransaction()` failed with `-38`
(`STATUS_INVALID_OPERATION`), and a raw `AParcel` through
`AIBinder_transact()` failed with `-22`. The NDK binder API is AIDL-oriented;
it is not a raw `/dev/binder` transact interface. The usable path is Java
`IBinder.transact()` or a private libbinder native bridge.

## Verdict

**PASS for the bootstrap probe.** `ActivityManager` binding gives an
unprivileged app a binder handle to a process-owned node, bypassing the
`BINDER_SET_CONTEXT_MGR`/`addService` wall that blocked the public
CVE-2026-64468 PoC.

**Not proven:** the CVE-2026-64468 topology, UAF, reclaim, arm64 LPE, or root.
The next phase is to port the PoC topology onto this handle path.
