# App-domain kernel probe

The debug app was extended with a native probe for:

- `perf_event_open(PERF_COUNT_SW_CPU_CLOCK)`;
- `/proc/self/stack`;
- `/proc/kallsyms`.

On-device result as `untrusted_app` (debug app):

```text
perf_fd=-1 errno=13;
stack_fd=96 errno=13;
stack_read=-1 errno=13;
kallsyms_fd=-1 errno=13
```

The stack file opens (fd 96) but reading it is SELinux-denied. perf and
kallsyms are also denied. So the app domain cannot be used to read the waiter
kernel stack or perf-sample the kernel SP.

No root.
