# Binder bootstrap probe

**Status:** bootstrap proven; no exploit code and no root.

This is a standalone research APK (`org.opencrown.binderprobe`, not part of the
Open Crown app) that tests the one bootstrap the public CVE-2026-64468 PoC
does not have on Android: getting a binder handle to a process-owned binder
node **without** becoming `servicemanager`'s context manager and without
`addService` permission.

## What it does

- `ManagerService` runs in a separate `:manager` process and returns a
  `Binder` from `onBind`.
- `MainActivity` binds to it through `ActivityManager`, receives the
  `IBinder` handle, and performs a synchronous transaction (`41 -> 42`).
- Both processes are ordinary `untrusted_app` processes, same UID.

## Observed on the A52

```text
BinderProbe: binding to ManagerService
BinderProbe: manager process created pid=19270
BinderProbe: manager onBind
BinderProbe: manager transaction value=41
BinderProbe: connected handle=147695331 result=42
```

`dumpsys activity services` shows the service in
`org.opencrown.binderprobe:manager` with a `BinderProxy` binding. This proves
an app can obtain a binder handle to a process-owned node through
`ActivityManager`, even though `servicemanager` `addService` is denied
(`EX_SECURITY`).

## Native bridge attempt

A JNI path using `AIBinder_fromJavaBinder()` was also tried. The NDK
`AIBinder_prepareTransaction()` refused the Java binder (`-38`,
`STATUS_INVALID_OPERATION`), and a raw `AParcel` through `AIBinder_transact()`
returned `-22`. This is the AIDL-oriented NDK binder API, not a raw
`/dev/binder` transaction interface. The working transaction in this spike is
the Java `IBinder.transact()` path (41 -> 42); the native topology must use
Java binder/parcel or a private libbinder bridge.

## Native handle extraction

A hidden-API bypass (`HiddenApiBypass.addHiddenApiExemptions`) made
`BinderProxy.mNativeData` readable. From that pointer the native side extracted
the `IBinder*` and a candidate `BpBinder` handle (12):

```text
BinderProbe: unsafe nativeData=...
BinderProbeNative: native_data=... ibinder=... h2=12
BinderProbeNative: extracted handle=12
```

A raw `BC_TRANSACTION` to handle 12 returned `-1` and did not reach
`ManagerService.onTransact` (no `manager transaction value=...` line). The
handle extraction works; the raw packet/thread path still needs debugging.

## Earlier native handle extraction attempts

The next step was to use the bound-service handle from native code. Two paths
failed on this build:

- `dlopen("libbinder.so")` is denied to the app namespace
  (`library "libbinder.so" not found`).
- JNI lookup of the `BinderProxy` private fields (`mNativeData`, `mObject`)
  returns null under hidden-API enforcement. The `AIBinder` wrapper's raw
  pointer fields did not yield a usable service handle (one candidate was 0,
  which targeted `servicemanager` and returned `EX_SECURITY`; the others were
  invalid).

Java `IBinder.transact()` works, but it does not expose the nested
target-thread/`BINDER_THREAD_EXIT` behavior the public PoC relies on. A native
raw-binder path needs a packaged `libbinder` + dependencies or a private
`ibinderForJavaObject()`/`BpBinder` bridge.

## What it does not prove

- It does not run the CVE-2026-64468 transaction topology or trigger the UAF.
- It does not port the x86_64 LPE to arm64.
- It does not produce root.

The next step is to express the PoC's manager/walker/victim topology using
these bound-service handles (or native binder nodes), then attempt the UAF on
the A52. That is still original exploit work.

## Build

```sh
JAVA_HOME=$PWD/.toolchain/jdk/Contents/Home \
ANDROID_HOME=$PWD/.android-sdk \
GRADLE_USER_HOME=$PWD/.gradle-home \
.toolchain/gradle-8.14.3/bin/gradle -p research/a52-chain/binder-bootstrap :app:assembleDebug
```

Install the APK, launch `.MainActivity`, and read the `BinderProbe` logcat tag.
