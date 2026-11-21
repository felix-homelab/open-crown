# FIVE/PROCA UAF → credential hypothesis

The A52 `task_integrity` object has:

```text
user_value   0x00
value        0x04
usage_count  0x08
value_lock   0x0c
...
```

The `proc_integrity_label_read()` path takes `value_lock`, so a stale
`task_integrity` can perform a queued-spinlock acquire/release at offset
0xc of whatever object has reclaimed the freed slot.

A52 `struct cred` layout (no `CONFIG_DEBUG_CREDENTIALS`):

```text
usage        0x00
uid          0x04
gid          0x08
suid         0x0c
sgid         0x10
euid         0x14
egid         0x18
fsuid        0x1c
fsgid        0x20
```

So if the freed `task_integrity` slot is reclaimed by a `struct cred`, the
spinlock unlock zeroes **`cred->suid`** (the low byte at minimum). An
unprivileged process is allowed to set its effective UID to its saved UID,
so a target child whose `suid` has become 0 can call:

```c
setresuid(-1, 0, -1);
```

and become euid 0 without `CAP_SETUID`.

## Why this is not yet a chain

The freed slot belongs to the dedicated `task_integrity_cache`; the
reclaim must cross caches into `cred_jar`. The A52 has no SysV IPC or key
spray, and both caches are dedicated. The practical route is to allocate and
free enough `task_integrity` objects to return slab pages to the buddy
allocator, then spray fresh `cred` allocations from a target process and win
the reader's scheduled-out window.

No on-device cred overwrite or `uid 0` has been achieved; the msg_msg route
from public research is unavailable here.
