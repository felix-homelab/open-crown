# A52 exploit combination analysis

After the CVE triage, I checked whether the confirmed primitives could be
composed.

## FIVE + cred

- `task_integrity.value_lock` at 0xc overlaps `cred->suid`.
- The same label handler reads `cred->sgid` at 0x18 as a label pointer and
  dereferences it; ordinary app/shell gids are nonzero, so it panics.
- No unprivileged gid-0 target exists (`CONFIG_USER_NS` off, no setgid-root
  binary found, CapEff zero).
- No alternative FIVE handler takes the spinlock.

Result: no composition to a cred overwrite.

## FIVE + reset_file file UAF

- The monkey path gives a freed `struct file`/dentry.
- `d_path` reaches a CFI-protected `d_dname` call.
- No SysV/key spray for a controlled `struct file` reclaim; the file cache
  is separate from the task_integrity cache.
- No controlled fake file or arbitrary read was built.

Result: reachable UAF, no write primitive.

## GhostLock + FIVE

- GhostLock has a UAF and KASLR leak but no full unprivileged stack carrier.
- FIVE's write is a spinlock at object+0xc; it does not provide a stack
  write or control of the futex waiter.
- No shared object or ordering turns the two UAFs into a stronger primitive.

## Binder + FIVE

- Binder topology works on framework procs, but raw-proc handle exchange is
  denied (app addService/context manager).
- FIVE's procfs UAF does not grant binder handle or service-manager access.
- No composition.

## Additional surfaces checked

- `CONFIG_IO_URING=y`; `io_uring_setup` works as shell, but no applicable
  public CVE for this 5.4.254/SPL build was identified.
- `CONFIG_BPF_SYSCALL=y` and `unprivileged_bpf_disabled=0`, but a
  `bpf(BPF_MAP_CREATE)` probe as shell returns EACCES.
- `/dev/kgsl-3d0` is app-accessible in principle, but the public A7xx
  CVE-2025-21479 does not apply and the A52 SPL postdates CVE-2024-23380.
- `/dev/fuse` is root-only; `/dev/ion` is system-group; KGSL open as shell
  is SELinux-denied.

No combination produced `uid 0`.
