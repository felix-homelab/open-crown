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

## Stock symbols

The stock `boot.img` was extracted from Samsung FUS (`A528BXXSBGYI3`, NEE)
with `asgard-fus`; the raw arm64 `Image` yielded 190,732 symbols via
`vmlinux-to-elf`. Key stock addresses (link-time):

```text
_text          0xffffffc010080000
commit_creds   0xffffffc010347e48
init_cred      0xffffffc012687c78
init_task      0xffffffc012d24480
selinux_state  0xffffffc012f0d000
null_fops      0xffffffc012467708
```

See [../../docs/evidence/a52-stock-kernel-symbols.md](../../../docs/evidence/a52-stock-kernel-symbols.md).

## arm64 port

The public Huawei P50 Pro 5.4.86 arm64 GhostLock chain builds against an A52
target header and runs on the device. `MCAST_BLOCK_SOURCE` copies the fake
waiter but returns `EADDRNOTAVAIL` after validation on A52; a full write test
with the link-time base panicked the kernel (`ro.boot.bootreason=
kernel_panic,bug`), and the phone recovered. The carrier copy offset, fake
lock target, or KASLR assumption still needs correction. See
[../../../docs/evidence/a52-ghostlock-arm64-port.md](../../../docs/evidence/a52-ghostlock-arm64-port.md).

## Next

1. Replace/adapt the MCAST carrier for A52 (pselect/select_stack or another
   stack-copy syscall).
2. Fix the boot_id `ctl_table` address; the binary currently reads a wrong
   UUID.
3. Retry the pointer/leaf write tests, then cred/SELinux root.
4. Verify `uid 0` and a working domain; no persistent change.
