# broker-probe

A small single-file client used to answer one question with a measured errno
instead of an assumption:

> can a given SELinux domain open a usable command channel to the
> init-domain relay that listens on the abstract unix socket `@dfi_init`?

It connects, optionally issues one tiny RPC, prints the errno, and stops.
It does not patch anything, does not load shellcode, and does not write to the
relay beyond one request frame. It runs from the shell domain only; the app
domain is measured separately by the app's own Diagnostics probe.

`reboot` is a recovery escape hatch, not a feature. The relay's design leaves
the init property thread unusable, so a normal `adb reboot` can hang; the
relay's own rack of raw syscalls is then the only software way to clear it. Use
it once, then reboot normally and verify the relay is gone.

## Build

```sh
tools/broker-probe/build.sh          # uses .android-sdk/ndk/27.0.12077973
# or: ANDROID_NDK_HOME=/path/to/ndk tools/broker-probe/build.sh
```

The result is `tools/broker-probe/probe`, a dynamically linked arm64-v8a
executable. Static linking is deliberately not used: the NDK emits an
8-byte-aligned TLS segment for `-static` and bionic refuses to exec it on
arm64 (measured on the A52: `executable's TLS segment is underaligned`).

## Run

Shell domain (`u:r:shell:s0`, uid 2000):

```sh
adb push tools/broker-probe/probe /data/local/tmp/probe
adb shell 'chmod 755 /data/local/tmp/probe; /data/local/tmp/probe connect'
```

App domain approximation (`u:r:runas_app:s0`, the app's uid):

```sh
# copies the binary into app data through run-as stdin (no install, no rebuild)
adb shell 'run-as org.opencrown.app sh -c "cat > /data/data/org.opencrown.app/probe"' \
    < tools/broker-probe/probe
adb shell 'run-as org.opencrown.app sh -c "chmod 755 /data/data/org.opencrown.app/probe; \
    /data/data/org.opencrown.app/probe connect"'
```

`runas_app` is an **approximation**, not the real `untrusted_app` domain. On
the A52 the two carry the same uid and the same MLS categories
(`u:r:runas_app:s0:c67,c257,c512,c768` vs
`u:r:untrusted_app:s0:c67,c257,c512,c768`); only the type differs. A denial
measured under `runas_app` is strong evidence but not proof for
`untrusted_app`; that needs a debug-only probe inside the app.

## Modes

| Mode | Meaning |
| --- | --- |
| `connect [sock]` | connect only, print errno and the peer's SELinux label; default socket `dfi_init` |
| `getuid [sock]` | connect, ask the relay for `getuid()`, print the raw return |
| `readfile <path> [sock]` | connect, `openat` + `read` the path through the relay |
| `listen <sock>` | bind an abstract socket and accept one connection (calibration target) |
| `reboot` | **recovery only**: ask the relay to execute `reboot(2)` when the property service is already wedged. Not used by the app; it does not make the relay safe. |

### Calibration

`listen` exists so a denial can be compared against a known-good and a
known-bad case on the same kernel:

```sh
# positive control: shell -> shell
adb shell '/data/local/tmp/probe listen bpcal' &
adb shell '/data/local/tmp/probe connect bpcal'          # expect OK

# negative control: runas_app -> shell (cross-domain)
adb shell '/data/local/tmp/probe listen bpcal2' &
adb shell 'run-as org.opencrown.app sh -c \
    "/data/data/org.opencrown.app/probe connect bpcal2"'  # expect EACCES
```

Expected answers for the relay itself:

| Caller | `connect @dfi_init` with relay live |
| --- | --- |
| `shell` | OK |
| `runas_app` | EACCES (13) — SELinux `connectto` on `unix_stream_socket` |
| anyone, relay absent | ECONNREFUSED (111) |

`ECONNREFUSED` means "no relay". `EACCES`/`EPERM` means "a relay exists and
the LSM refused". Do not report a denial without one of those two errnos.

On a successful connect the probe also prints `peer_selinux_context`, which
is the server's label from `SO_PEERSEC` (measured: `u:r:init:s0`). That is
the `tcontext` a denial would name, so a denial can be described exactly even
when logd is down and no `avc:` line can be read.

## Wire format

Derived from the payload disassembly (`docs/evidence/broker-probe-a52.md`
has the byte-level map). The relay is a raw syscall RPC, not a command set.

Request, exactly 4096 bytes, little-endian:

| Offset | Type | Meaning |
| --- | --- | --- |
| 0x00 | u64 | syscall number (aarch64 generic table) |
| 0x08 | u64 | arg0 |
| 0x10 | u64 | arg1 |
| 0x18 | u64 | arg2 |
| 0x20 | u64 | arg3 |
| 0x28 | u64 | arg4 |
| 0x30 | u64 | arg5 |
| 0x38 | u32 | flags: bit N means "argN is an offset into the buffer below" |
| 0x3c | u32 | unused by the client; relay writes 0xd1 here in the reply |
| 0x40 | bytes | string/struct scratch space addressed by the flag bits |

Response, exactly 4096 bytes:

| Offset | Type | Meaning |
| --- | --- | --- |
| 0x00 | u64 | raw syscall return; negative value is `-errno` |
| 0x3c | u8 | constant 0xd1 marker written by the relay |

The client must send all 4096 bytes: the relay's child blocks in `read()`
until the frame is complete. Flag bit 7 (0x80) makes the relay close the
connection after one reply; the probe does not set it, so one connection can
carry several calls.

## Safety

- The probe never writes to `/dev/.dfi`, never binds `@dfi_init`, and never
  sends a state-changing syscall. `getuid` and `readfile` are read-only.
- Do not send `execve`, `mount`, `setcon`, or property writes through the
  relay from this tool. The relay runs as uid 0 in `u:r:init:s0`; a careless
  syscall can wedge or reboot the device.
- The relay's thread never returns to init (see the evidence file). Anything
  that makes init log again after activation re-enters the payload, whose
  `bind()` then fails and whose `accept()` spins on an unbound socket. Do not
  run `setprop ctl.start ...` against an already-armed relay.
- `listen` creates a temporary abstract socket; it disappears with the
  process. Nothing is written to disk.
- Keep the device serial and any account material out of logs and commits.

## Files

- `probe.c` — the client (single file, bionic only).
- `build.sh` — NDK build, dynamic link.
- `README.md` — this file.