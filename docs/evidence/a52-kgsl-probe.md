# A52 KGSL alignment probe — 2026-11-21

- **Device:** Samsung Galaxy A52s 5G `SM-A528B`, kernel
  `5.4.254-qgki-28575149-abA528BXXSBGYI3`, SPL `2025-09-01`, `a52sxq`
- **Probe:** CVE-2026-21385 KGSL alignment sign-extension probe from
  [zhanghangjie/CVE-2026-21385-CYL1](https://github.com/zhanghangjie/CVE-2026-21385-CYL1),
  built from `kit/t21385.c` with NDK 30 for arm64 API 34
- **Binary SHA-256:** `8d3305f2003f827a9832b7609a28702f6ec6d807226bb6c48c31d5183bed7303`
- **Run as:** `uid=2000(shell)`, SELinux `Enforcing`; no root, no payload
  installed, no persistent change.

## Commands

```sh
NDK=$PWD/.android-sdk/ndk/30.0.16248370
$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android34-clang \
  -O2 -Wall -Wextra -o /tmp/t21385 /tmp/t21385.c
adb push /tmp/t21385 /data/local/tmp/t21385
adb shell 'chmod 755 /data/local/tmp/t21385'
adb shell 'cd /data/local/tmp && timeout 20 ./t21385 -s 4096'
```

## Observed result

The probe opens `/dev/kgsl-3d0` (world rw, shell context) and exercises
`MAP_USER_MEM`, `GPUOBJ_ALLOC` and `GPUOBJ_IMPORT` with alignment exponents up
to 255. The CVE's expected artifact is a 64-bit GPU VA wrapped below the
pagetable base (`0x80000000`) for alignment 31.

On this A52 build:

```text
[map_user_mem 0x15] align= 31 size=4096 rc=0 errno=0 (Success) gpuaddr=0x4080000000 flags=0x1f0060
[gpuobj_alloc 0x45] align= 31 size=4096 rc=0 id=3 info_rc=0 gpuaddr=0x4004000000
align= 31 size=4096  import OK id=3  info rc=0  gpuaddr=0x4000001000
```

- No row contains `WRAPPED BELOW PAGETABLE VA BASE`.
- The alignment-31 `MAP_USER_MEM` address is a normal 64-bit pagetable address
  (`0x4080000000`), not the wrapped `0x80000000`.
- No `kgsl` fault, `BUG:`, `panic`, or `FATAL` line appeared in logcat after
  the probe; the shell stayed in `Enforcing`.
- The probe exits 0.

Full output: [a52-kgsl-probe.log](a52-kgsl-probe.log).

## Verdict

**PASS (probe ran); NEGATIVE for this exploit primitive on the A52.**

The A52's exact kernel source contains the uncapped
`max_t(uint64_t, 1 << kgsl_memdesc_get_align(memdesc), PAGE_SIZE)` line in
`drivers/gpu/msm/kgsl_iommu.c`, but this build does not produce the CVE's
sign-extension wrap under the public `MAP_USER_MEM`/`GPUOBJ_IMPORT` primitive.
That is a negative result for CVE-2026-21385 on this build, not a statement
that the device is secure.

No root was obtained, no payload was run, and no partition or boot state was
changed. The A52 remains an `example` probe device with no READY payload.
