# GhostLock / CVE-2026-43499 on the A52

**Status:** UAF trigger reproduced; no root yet.

CVE-2026-43499 is a `rtmutex`/futex-PI stack use-after-free in
`remove_waiter()`: the proxy-lock rollback clears `pi_blocked_on` on
`current` instead of `waiter->task`. The A52 kernel source still has the
vulnerable line:

```c
raw_spin_lock(&current->pi_lock);
rt_mutex_dequeue(lock, waiter);
current->pi_blocked_on = NULL;
raw_spin_unlock(&current->pi_lock);
```

The fix uses the waiter's task. The A52 config has `CONFIG_FUTEX_PI=y` and
`CONFIG_RANDOMIZE_BASE=y`. KPTI is **off**
(`# CONFIG_UNMAP_KERNEL_AT_EL0 is not set`); PAN is on.

## Trigger

`poc_deadlock_arm64.c` creates the three-thread PI cycle and calls
`FUTEX_CMP_REQUEUE_PI`; the proxy-lock deadlock path returns `EDEADLK`:

```text
GhostLock three-thread deadlock trigger start
trigger ret=-1 errno=35 (Resource deadlock would occur)
waiter ret=-1 errno=110 (Connection timed out)
```

The boot id stayed `27d1bcba-dea6-45b0-9cd6-1679851bbb67`; no panic. This is
a trigger, **not** an exploit: no kernel read/write and no root.

## Build

```sh
ndk=$PWD/.android-sdk/ndk/30.0.16248370
$ndk/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android31-clang \
  -static -O2 -pthread poc_deadlock_arm64.c -o gl_deadlock
adb push gl_deadlock /data/local/tmp/
adb shell chmod 755 /data/local/tmp/gl_deadlock
adb shell /data/local/tmp/gl_deadlock
```

## Public references

- [GhostLock ARM32 5.4 research](https://github.com/gitchw/ghostlock-cve-2026-43499)
- [Honor 80 GT 5.10 arm64 port](https://github.com/yakidango-official/GhostLock-H80GT)
- [OnePlus aarch64 reference](https://github.com/gitchw/ghostlock-cve-2026-43499/tree/main/references/ref_oneplus_aarch64)
- [NebulaSec write-up](https://nebusec.ai/research/ionstack-part-2/)

## Next

1. Build the A52 `vmlinux` from the mirrored source and the on-device
   `config.gz`; recover symbol offsets and struct layouts.
2. Implement a KASLR leak (the OnePlus reference uses `perf_event_open` /
   `kernelsnitch`; the A52 shell may need a different route).
3. Port the arm64 PI write / SELinux / `init_cred` path from the public
   arm64 reference to the A52 5.4 offsets.
4. Verify `uid 0` and a working domain; no persistent change.
