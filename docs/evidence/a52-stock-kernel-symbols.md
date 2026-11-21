# A52 stock kernel symbols — 2026-11-21

- **Device:** Galaxy A52s 5G `SM-A528B`, `A528BXXSBGYI3`, kernel
  `5.4.254-qgki-28575149-abA528BXXSBGYI3`, Android 14.
- **Firmware source:** Samsung FUS via `asgard-fus` (NEE CSC;
  `A528BXXSBGYI3/A528BOXMBGYI3/A528BXXSBGYI3/A528BXXSBGYI3`).
- **Boot image:** `boot.img` 96 MB, Android boot header v3, extracted with
  AOSP `unpack_bootimg.py`.
- **Kernel:** raw arm64 `Image`, 47 MB, text offset 0, 4 KB pages.
- **Symbol extraction:** `vmlinux-to-elf` found 190,732 symbols and 134,483
  relocations and reconstructed a 57 MB ELF. Guessed link base:
  `0xffffffc010080000`.

## Key symbols

```text
_text                 0xffffffc010080000
_stext                0xffffffc010081000
commit_creds          0xffffffc010347e48
init_cred             0xffffffc012687c78
init_task             0xffffffc012d24480
selinux_state         0xffffffc012f0d000
null_fops             0xffffffc012467708
anon_pipe_buf_ops     0xffffffc0124a7df8
ashmem_misc           0xffffffc012cb5420
kimage_vaddr          0xffffffc0117a5000
kimage_voffset        0xffffffc0125c9870
memstart_addr         0xffffffc0125c9850
```

`vmlinux-to-elf` warns that the base was guessed from relocation offsets; the
symbols above are **link-time addresses**, not necessarily runtime addresses.

## KASLR state

- `CONFIG_RANDOMIZE_BASE=y`, `CONFIG_RELOCATABLE=y`, VA_BITS=39.
- The boot image command line has no `nokaslr`.
- `/proc/device-tree/chosen/kaslr-seed` exists and is 8 bytes, but reading it
  is denied to shell and it is wiped by the kernel. So KASLR is likely active;
  a base leak is still required.
- `CONFIG_UNMAP_KERNEL_AT_EL0` is not set (KPTI off).

## Kernel physical load

Unknown. `BOARD_KERNEL_BASE=0` and `BOARD_KERNEL_OFFSET=0x8000` in the A52s
device tree do not give the bootloader's load address. Likely candidates are
`0x80080000` or `0xa8000000`; both need testing. The public arm64 GhostLock
reference uses a per-SoC `kernel_phys_load` for the direct-map alias.

## Source build

The Samsung source mirror builds only partially: it lacks
`techpack/Makefile`, `techpack/datarmnet/Makefile`, and the uppercase
`net/netfilter/xt_DSCP.c`, and `cnss2` trips a format warning. Stubs and a
`-Wno-error` override are being tried for a debug-info `vmlinux` (for struct
offsets only; stock symbol addresses come from the extracted ELF above).

## Verdict

Stock A52 symbols and the raw kernel image are now available locally. The next
blockers are KASLR/`kernel_phys_load` and the A52 `task_struct`/`cred`/
`rt_mutex_waiter` offsets.

## Struct offsets recovered from stock disassembly

`commit_creds` and `remove_waiter` in the reconstructed stock ELF give:

```text
task_struct.cred              0x800
task_struct.real_cred         0x808
task_struct.pi_lock           0x8dc
task_struct.pi_waiters        0x8e8   (rb_root)
task_struct.pi_waiters_leftmost 0x8f0
task_struct.pi_blocked_on     0x900

rt_mutex_waiter.tree_entry    0x00
rt_mutex_waiter.pi_tree_entry 0x18
rt_mutex_waiter.task          0x30
rt_mutex_waiter.lock          0x38
rt_mutex_waiter.prio          0x40
rt_mutex_waiter.deadline      0x48
```

`selinux_enforcing` is a separate data symbol at
`0xffffffc0130c8c54` (not the `selinux_state` struct base). The vulnerable
`remove_waiter()` clears `[current, #0x900]`, exactly the stock
`pi_blocked_on` field.

## Build attempt

The stubbed source build still fails in `techpack/display` and on the
uppercase `xt_DSCP.o` rule, so the debug-info `vmlinux` is not available yet.
The stock ELF above is sufficient for symbols and the key task/waiter
offsets; the remaining offsets (`rt_mutex`, `file_operations`, `mm_struct`)
can be read from source/disassembly.
