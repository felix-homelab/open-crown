# FIVE task_integrity cross-cache test

`/proc/slabinfo` as shell shows:

```text
task_integrity_cache    448  448  128  32  1  ... slabdata 14 14
```

So the object is 128 bytes, 32 per page, in a dedicated cache.

A harness ran a value-field UAF race for 20 seconds while:

- churning 39,552 child tasks that allocate/free `task_integrity`;
- spraying 21,846 AF_UNIX datagram skbs with a `0x41414141` marker;
- reading `/proc/<victim>/integrity/value` 776,337 times.

No marker was observed. Afterward:

```text
task_integrity_cache   2048  2048  128  32  1  ... slabdata 64 64
```

The dedicated cache retained/expanded its slabs instead of returning empty
pages to the buddy allocator, so a kmalloc-128/cred cross-cache reclaim did
not occur. `/sys/kernel/slab/task_integrity_cache/shrink` is permission
denied to shell.

This confirms the FIVE cross-cache route is blocked on this build without a
cache-shrink or memory-reclaim primitive.
