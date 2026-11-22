# Broker probe on the A52 — can the app reach the init relay?

Date: 2026-11-22 (UTC times below; local Europe/Berlin = UTC+1).
Device: Samsung SM-A528B (`a52sxq`), Android 14 / SDK 34, build
`UP1A.231005.007` / `A528BXXSBGYI3`, kernel `5.4.254-qgki`, SELinux
Enforcing. No serial, account material, or pairing key appears in this file.
No bootloader change, no wipe, no persistent modification was made.

Artefacts:

- `payloads/org.opencrown.payload.pagecache-relay/payload.bin`
  576 bytes, sha256 `48d294ce335cf99f59b6b3031ed3741a033556ac55a445f122aea695b630ba43`
- `tools/broker-probe/` — probe client, build script, README
- this log

## Verdict

**Blocked.** The unprivileged app cannot obtain a usable command channel to
the init-domain relay without a manual ADB step, and the block is a measured
kernel refusal, not an inference:

1. The relay is a raw-syscall RPC on abstract socket `@dfi_init`, with **no
   authentication** beyond "am I uid 0 and pid 1". Anyone who can connect owns
   arbitrary syscalls as uid 0 in `u:r:init:s0`.
2. From `u:r:shell:s0` the channel works: `connect()` succeeds, `getuid()`
   returns 0 through the relay, and `/proc/1/attr/current` read *through* the
   relay returns `u:r:init:s0`.
3. From `u:r:runas_app:s0` (same uid and MLS categories as the real app,
   different type only) the same `connect()` fails with **errno 13 EACCES**
   while the socket is proven live in the same second. `EACCES` on an
   abstract unix socket can only come from the LSM, i.e. SELinux `connectto`
   on `unix_stream_socket`.
4. Independently, the app has no unprivileged way to make init call `LOG()`,
   which is the only thing that runs the payload. The property write is
   denied (recorded before this session), and a second init log after
   activation re-enters the payload.

Two structural findings matter as much as the policy denial:

- **The relay never returns to init.** After `bind`/`listen` the payload
  loops in `accept()` forever; there is no return path except the two early
  guards. Arming the relay therefore permanently consumes the init thread
  that logged. On this device that presented as a dead property service and a
  dead logd socket, then a reset.
- **The relay is not a broker.** No token, no allowlist, no audit, no
  lifecycle. Shipping it as an app-facing service would be handing every app
  that can reach the socket a root syscall primitive.

## 1. Relay protocol (payload disassembly)

Method: the 576 bytes were embedded with `.incbin` into an aarch64 object and
decoded with Capstone 5.0.7. `llvm-objdump` from NDK 27.0.12077973 printed
every instruction as `.word` for a raw `.incbin` section (verified: the ELF
header says `Machine: AArch64` and `.text` is `AX`), so Capstone was used for
the instruction map. All prose below is original analysis.

### Control flow (offsets are file offsets)

| Offset | What it does |
| --- | --- |
| 0x000 | prologue: three `stp` pairs, saves x0/x19-x22/x30 |
| 0x00c | `getuid()` (syscall 174); if != 0 jump to epilogue 0x1f8 |
| 0x018 | `getpid()` (172); if != 1 jump to epilogue |
| 0x028 | `openat(AT_FDCWD, "/dev/.dfi", O_CREAT|O_EXCL, 0)` — marker file, then `close()` |
| 0x04c | dead argument setup (x0=17 … then `mov w0,#0`), never branched to |
| 0x06c | `setsid()` (157); result ignored (fails EPERM in pid 1) |
| 0x074 | `socket(AF_UNIX, SOCK_STREAM, 0)` (198) |
| 0x08c | build `sockaddr_un`: `sun_family=AF_UNIX`, `sun_path="\\0dfi_init"` |
| 0x0ac | `bind(fd, addr, 11)` (200) — abstract namespace, no filesystem entry |
| 0x0c0 | `listen(fd, 2)` (201) |
| 0x0d0 | allocate a 4096-byte frame on the stack |
| 0x0d8 | `accept(fd, NULL, NULL)` (202); negative return retries at 0x0d8 |
| 0x0f4 | `clone(SIGCHLD, 0,0,0,0)` (220) = fork, one child per connection |
| 0x110 | parent: close client fd, back to `accept` |
| 0x114 | child: close listening fd |
| 0x134 | `read()` until 4096 bytes or EOF |
| 0x164 | parse frame: nr, arg0-arg5, flags, pointer base = frame+0x40 |
| 0x188 | for flag bits 0..4, add the pointer base to that argument |
| 0x1b0 | `svc #0` — executes the caller's syscall number |
| 0x1b4 | store raw return at frame+0x00, store constant 0xd1 at frame+0x3c |
| 0x1c4 | if flag bit 7 set, close after one reply |
| 0x1c8 | `write(fd, frame, 4096)` (64), then loop to 0x134 |
| 0x1f0 | `exit(93)` — unreachable, no branch targets it |
| 0x1f8 | epilogue: three `ldp`, then `retaa` at 0x220 |
| 0x204 | seven NOPs — the patch slots the in-app writer fills with the live prologue |
| 0x224 | `"/dev/.dfi\\0"` |
| 0x230 | `"\\0dfi_init\\0"` (leading NUL = abstract namespace) |

The scan at load time finds the largest NOP run, which is 0x204..0x21c (7
slots); that is what the app overwrites with init's live prologue and a branch
back to the real epilogue. It also explains "Payload fits: YES (580 <= 696)":
576 payload bytes plus 4 bytes of preserved BTI/PAC landing pad.

### Wire format

Request and response are both exactly 4096 bytes, little-endian.

| Offset | Field |
| --- | --- |
| 0x00 | u64 syscall number |
| 0x08..0x30 | u64 arg0..arg5 |
| 0x38 | u32 flags: bit N => argN is an offset into the frame buffer |
| 0x3c | u32 unused in request; reply stores 0xd1 |
| 0x40..0xfff | string/struct scratch |

Reply offset 0x00 is the raw syscall return. A negative value is `-errno`;
the 0xd1 byte at 0x3c is a constant marker, not a computed errno.

### Operations and auth

- **Operations: any Linux syscall.** The relay does not expose a command
  vocabulary; it exposes `svc #0` with six caller-controlled arguments and a
  pointer-fixup bitmap. `execve`, `openat`, `mount`, `setresuid`, property
  socket writes — all reachable.
- **Auth: none.** The only gates are `uid == 0` and `pid == 1`, checked in
  the hijacked init process, so they are always true when the payload runs.
  There is no token, nonce, peer check, or credential inspection.
- **Framing: fixed 4096.** A client that sends fewer bytes blocks the child in
  `read()`; it gets no reply.
- **Re-entry is destructive.** `openat` fails EEXIST, `bind` fails
  EADDRINUSE, `listen` fails, and `accept` on an unbound socket returns
  EINVAL, which the `tbnz` treats as retry — a tight spin inside init.

## 2. SELinux ground truth

```text
$ adb shell getenforce
Enforcing
$ adb shell cat /sys/fs/selinux/enforce
1
$ adb shell cat /proc/self/attr/current
u:r:shell:s0
```

Policy file is present but not readable to shell, so the binary policy could
not be pulled or searched for `allow`/`neverallow` rules:

```text
$ adb shell 'ls -lZ /sys/fs/selinux/policy; head -c 4 /sys/fs/selinux/policy'
-r--r--r-- 1 root root u:object_r:selinuxfs:s0  0 1970-01-01 01:00 /sys/fs/selinux/policy
head: /sys/fs/selinux/policy: Permission denied
```

Denial channels are also unavailable to shell:

```text
$ adb shell 'dmesg'
dmesg: klogctl: Permission denied
$ adb shell 'cat /proc/kmsg'
cat: /proc/kmsg: Permission denied
```

`logcat` worked on a healthy boot (712 lines matched `avc`) but was dead on
the relay-armed boot, because logd stops serving once init is hijacked:

```text
$ adb shell 'logcat -d -t 3'
logcat: Logcat read failure: Connection refused
```

**Consequence:** no `avc:` line could be captured for the socket denial in
this session. The denial is still established by errno (below), and the
socket's own label was recovered a different way: `SO_PEERSEC` on a
shell-owned connection returns `u:r:init:s0` — so that is the `tcontext` a
denial names. The policy rule text itself remains UNKNOWN.

Contexts measured on the device:

```text
# real app process, read-only observation
$ adb shell 'ps -A -Z -o USER,PID,LABEL,NAME | grep -i opencrown'
u:r:untrusted_app:s0:c67,c257,c512,c768 u0_a323 15710 u:r:untrusted_app:s0:c67,c257,c512,c768 org.opencrown.app

# run-as approximation
$ adb shell 'run-as org.opencrown.app id'
uid=10323(u0_a323) ... context=u:r:runas_app:s0:c67,c257,c512,c768
```

Same uid, same MLS categories; only the type differs
(`runas_app` vs `untrusted_app`). That is why the run-as measurement is a
close approximation — and why it is still only an approximation.

## 3. Empirical connect tests

Client: `tools/broker-probe/probe.c` (see README). Binary built dynamically
for arm64-v8a; a `-static` build is rejected by bionic on this device
("executable's TLS segment is underaligned ... needs to be at least 64").

### 3a. Calibration, same kernel, no relay involved

```text
===== POSITIVE CONTROL: shell -> shell @bpcal =====
mode=connect sock=@bpcal
selinux_context=u:r:shell:s0
uid=2000 euid=2000 pid=16139
connect(@bpcal): OK
--- listener ---
listen(@bpcal): ACCEPTED (cross-domain connect allowed)

===== NEGATIVE CONTROL: runas_app -> shell @bpcal2 =====
mode=connect sock=@bpcal2
selinux_context=u:r:runas_app:s0:c67,c257,c512,c768
uid=10323 euid=10323 pid=16172
connect(@bpcal2): FAILED errno=13(EACCES)
verdict: DENIED BY POLICY (socket exists; connect refused by LSM)
--- listener ---
listen(@bpcal2): no connection within 15000ms
```

This proves the client distinguishes "nobody listening" from "LSM said no" on
this exact kernel, and that a cross-domain abstract-socket connect returns
`EACCES`, not `ECONNREFUSED`.

### 3b. Against the live relay (boot 68, armed via the documented ADB step)

Socket present, marker present:

```text
$ adb shell 'cat /proc/net/unix | grep dfi'
0000000000000000: 00000002 00000000 00010000 0001 01 90390 @dfi_init
$ adb shell 'ls -la /dev/.dfi'
ls: /dev/.dfi: Permission denied
```

**shell domain — allowed, usable root channel:**

```text
$ adb shell '/data/local/tmp/probe getuid'
mode=getuid sock=@dfi_init
selinux_context=u:r:shell:s0
uid=2000 euid=2000 pid=16210
connect(@dfi_init): OK
getuid: raw=0 frame_marker=0xd1
```

`getuid` returning 0 through the relay, from a uid-2000 shell, is the relay
executing in the init process. The socket's own label, asked of the kernel
with `SO_PEERSEC` on that same connection:

```text
$ adb shell '/data/local/tmp/probe connect'
mode=connect sock=@dfi_init
selinux_context=u:r:shell:s0
uid=2000 euid=2000 pid=17458
connect(@dfi_init): OK
peer_selinux_context=u:r:init:s0
```

That is the measured `tcontext`; the denial for `runas_app` is
`u:r:runas_app:s0:c67,c257,c512,c768` → `u:r:init:s0` on
`unix_stream_socket { connectto }`. A second call read init's own context
through the relay:

```text
$ adb shell '/data/local/tmp/probe readfile /proc/1/attr/current'
connect(@dfi_init): OK
openat: raw=21 frame_marker=0xd1
read: raw=12 frame_marker=0xd1
---- first 12 bytes of /proc/1/attr/current ----
u:r:init:s0
---- end ----
```

**runas_app — denied three times while the relay was live:**

```text
$ adb shell 'run-as org.opencrown.app sh -c "/data/data/org.opencrown.app/probe connect"'
mode=connect sock=@dfi_init
selinux_context=u:r:runas_app:s0:c67,c257,c512,c768
uid=10323 euid=10323 pid=16215
connect(@dfi_init): FAILED errno=13(EACCES)
verdict: DENIED BY POLICY (socket exists; connect refused by LSM)
```

Repeated twice more at 13:09:41Z and 13:09:42Z with the identical result while
`@dfi_init` (inode 90390) was still listed. The relay was demonstrably live
and accepting for shell at the same moment, so this is not a missing-socket
artefact.

**Interpretation.** `connect()` on an abstract unix socket has no filesystem
permission to fail on. `EACCES` there is the LSM's `security_unix_stream_connect`
refusing `connectto` on `unix_stream_socket`. With SELinux Enforcing and no
other LSM, that is a SELinux denial of the app-domain type against the relay
socket's label (expected `u:r:init:s0`, not verified — see UNKNOWNs).

### 3c. What was NOT measured

The real `untrusted_app` domain was not measured. The mission forbids
modifying app source, and there is no supported way to execute a test binary
in `untrusted_app` without doing so. `runas_app` shares the app's uid and MLS
categories but is a different type; a debug-only probe inside the app (or a
test-only debug manifest) is needed for the final confirmation. The in-tree
ADR already records the same conclusion from the app itself
(`docs/adr/0017-pagecache-relay-payload.md`: "Android's `untrusted_app`
SELinux domain cannot connect to the relay socket").

Note that the app already contains `AgentLinkProbe` (LocalSocket to abstract
`dfi_init`) and runs it on every refresh; its result is only displayed, never
persisted, so it could not be recovered from app data after the fact. Reading
that line off the device UI, or logging it, is the cheapest real
`untrusted_app` measurement the lead can take.

## 4. Trigger path

`zzz` is not a service. The documented trigger `adb shell setprop ctl.start zzz`
works because init logs a failure for the unknown service, and that `LOG()`
call reaches libbase's `~LogMessage`, whose page cache is patched. In other
words the trigger is "make init log", and `ctl.start` is only a convenient way
to do it.

Property writes from the unprivileged side are denied. Recorded before this
session in `docs/evidence/a52-app-chain.md`:

```text
init trigger denied: Failed to set property 'debug.dirtyinit'
```

The app's own attempt list (`PageCacheRelayChain.triggerInitLog`) is
`setprop ctl.start zzz`, `setprop debug.dirtyinit <ts>`, and
`sh -c "setprop ctl.start zzz"` — all three run as the app uid and all three
are property-service `set` attempts on `ctl.*`/`debug.*`, which
`untrusted_app` is not allowed to perform. No other unprivileged interface to
make init log was found: `/dev/kmsg` is root-only, `dmesg` is denied, and
service start/stop is the property interface.

Observed hazard while capturing this (reported as it happened, not as a
controlled experiment): with the relay already armed, `setprop ctl.start zzz`
from the ADB shell **hung** — the property service was already unreachable
because init's logging thread was inside the relay's `accept()` loop. Adb
lost the device and it returned at 13:08:12Z with 30.06 s uptime and a new
`boot_id`, i.e. it had reset. Whether the reset was caused by that command or
by the pre-existing init hijack (watchdog) is UNKNOWN; the sequence is
recorded and no further trigger was issued. This is direct evidence that the
relay is not re-entrant and that arming it destabilises the device.

## 5. Broker options

| Option | SELinux permission required | How to test | Result | Residual risk |
| --- | --- | --- | --- | --- |
| App connects to `@dfi_init` | `allow untrusted_app init:unix_stream_socket connectto` (+ MLS constraints; AOSP neverallow for app→init) | probe `connect` from `runas_app`; real domain via in-app probe | **EACCES (13)** measured from `runas_app`; blocked | Would hand every app a root syscall primitive; no auth, no audit |
| init proxies to an app-owned socket | `allow init <app_domain>:unix_stream_socket connectto` **and** a way to make init call `connect()` on demand | needs a trigger first; not testable while the only trigger is init's own `LOG()` | Not tested — chicken-and-egg: init only acts when someone connects to it | Solves transport only if init polls; relay has no poll loop, it blocks in `accept()` |
| File/queue in app data | `allow init app_data_file:dir/file { open read write }` | would need an init-side reader loop | Not tested — relay executes exactly one syscall per request and never polls a path | Same trigger problem; broad `init`→app-data access is a policy hole |
| Binder service in app, called by init | `allow init <app_domain>:binder call` + init binder client | not testable without modifying init | Not feasible as-is: init has no binder client loop, and the relay cannot call one on demand | Would need a new init-side daemon, i.e. a different payload |
| Root agent launched by relay with `setcon` | `allow init <target_domain>:process setcurrent` (and the target must still be reachable by the app) | not tested | Not attempted | Does not create app→agent reachability; the app still cannot connect to the new domain without a policy allow |
| Magisk / persistent root | n/a (out of policy) | n/a | Rejected | Out of scope by project rules (no bypass, no hiding) and by ADR 0002 (per-boot borrowed root) |

The common blocker across the first five is the same: **nothing in `init`
listens for the app**. The relay is a hijacked thread inside a `LOG()` call,
not a registered service that can be woken by an unprivileged client, and the
only unprivileged→init channel Android offers (properties) is closed to
`untrusted_app`.

## 6. One-boot lifecycle

Directly observed, though via an unintended reset rather than a planned
reboot: after the reset the device came back with uptime 30.06 s, a new
`boot_id` (`b2b8e31d-0607-4baa-8865-f793d9bad0d0`), **no `@dfi_init` in
`/proc/net/unix`** and **no `/dev/.dfi`**:

```text
$ adb shell 'cat /proc/net/unix | grep -i dfi; echo grep_rc=$?'
grep_rc=1
$ adb shell 'ls -la /dev/.dfi'
ls: /dev/.dfi: No such file or directory
```

That matches `docs/13-status.md` and ADR 0017 ("relay and page-cache patch
disappear at reboot") and `manifest.json` (`persistence: boot`,
`reversible: reboot`).

Non-destructive check for a later session (no reboot required):

```sh
# before: record the boot identity and relay state
adb shell 'cat /proc/sys/kernel/random/boot_id; cat /proc/uptime'
adb shell 'cat /proc/net/unix | grep -c dfi_init'
# reboot at a time of the operator's choosing, then:
adb shell 'cat /proc/sys/kernel/random/boot_id; cat /proc/uptime'
adb shell 'cat /proc/net/unix | grep -c dfi_init'   # expect 0
adb shell 'ls /dev/.dfi'                            # expect "No such file"
# and confirm the page cache is clean without running anything:
adb shell 'sha256sum /system/lib64/libbase.so'      # compare against a pristine build
```

The boot-identity comparison alone is enough to separate "relay died" from
"relay never armed".

## UNKNOWNs

1. **The AVC line for the socket denial.** logd was not serving on the armed
   boot, `dmesg` and `/proc/kmsg` are denied to shell, and the policy file is
   unreadable. The errno is conclusive and the socket label is measured
   (`u:r:init:s0` via `SO_PEERSEC`), but the literal `avc: denied { connectto }`
   line — and therefore which rule or neverallow produces it — was not
   captured.
2. **The real `untrusted_app` denial.** Measured on `runas_app` only. Same
   uid and MLS categories, different type. The lead can close this with a
   debug-only in-app probe, or by reading the app's own `AgentLinkProbe`
   result from the UI while the relay is armed.
3. **Why the device reset.** Ordering is known (hung `setprop`, adb loss,
   reset within ~2 min); the mechanism (watchdog vs. a second payload entry vs.
   something else) was not observed. No intentional reboot was performed.
4. **The policy's `neverallow` set.** Not readable from the device at this
   privilege level, so whether a policy edit is even *possible* on a stock
   Samsung build (vs. blocked by neverallow) is not verified here.
5. **Whether any non-init domain can reach `@dfi_init`.** Only `shell` (yes)
   and `runas_app` (no) were measured. A `bluetooth`-domain or
   `system_app` measurement was not possible without a test app in those
   domains.
6. **`/dev/.dfi`'s label.** Even `stat` from shell returns `EACCES`, which
   implies a `getattr` denial on its label; the label itself was not read.
