# Provenance — futex-timer

- **Open Crown catalog id:** `org.opencrown.payload.futex-timer`
- **Upstream name:** `libtimer` (SamTimer DZJ3 target)
- **What it actually does:** CVE-2026-98260 POSIX-timer use-after-free →
  pipe read/write primitive → forged workqueue → `call_usermodehelper` →
  KernelSU late-load. It is a root/domain payload, **not** a controller payload.
- **Source:** <https://github.com/NanoTurtle1145/root-my-s24>
- **Commit:** `c3a5edc5135a380a992d1d88fac43c1d71fb23a5`
- **Upstream path:** `app/src/main/assets/samtimer/targets/S9280ZCS6DZJ3/libtimer`
- **Upstream build record:** `app/src/main/cpp/targets/S9280ZCS6DZJ3/BUILD.md`
- **Licence:** GPL-3.0 (full text in `LICENSE`; upstream repository also
  contains GPL-2.0 and GPL-3.0 notices)
- **Artifact:** `files/libtimer`, 2,228,648 bytes,
  SHA-256 `8989511991779fa0eefa68bcde0373ff36737b228a617e9e2c0851edf5386539`
- **Exact target:** `SM-S9280` / `6.1.145-android14-11-3254743-abS9280ZCS6DZJ3`
  / arm64-v8a
- **Upstream claim:** manually staged and run with `libtimer umhpipe` on that
  build, reported `KSU_LOADED`; see the upstream BUILD record.
- **Open Crown status:** extracted unchanged and signed for private
  development. **Not built, run, or hardware-verified by Open Crown.** The
  attached A52 is not a target.
- **Execution note:** upstream runs this from a Shizuku `UserService` / shell
  context. The Open Crown app process is `untrusted_app`; it cannot execute this
  payload without that privileged runner. No Shizuku bridge exists yet.

This bundle is quarantined under ADR 0015. The name `futex-timer` is a
descriptive catalog label; copyright remains with the upstream authors.
