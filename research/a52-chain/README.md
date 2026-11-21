# A52 chain research branch

**Status:** research only — no working chain. One stock DoS run confirmed the
CVE-2026-20971 UAF on the attached A52 (panic/reboot, no root); a controlled
primitive needs a lab kernel.
**Target:** Samsung Galaxy A52s 5G `SM-A528B`, kernel
`5.4.254-qgki-28575149-abA528BXXSBGYI3`, binary B (11), SPL `2025-09-01`.
**Scope:** determine whether an original temporary-root chain is feasible on
this build; do not touch the daily A52.

This branch exists because no public per-boot payload targets the A52. See
[the payload search](../../docs/research/a52-payload-search.md) and
[CVE-2026-21385 probe](../../docs/evidence/a52-kgsl-probe.md).

It is **not** a promise of root. The public CVE-2026-20971 work explicitly
stops at an unprivileged UAF/DoS; its own report lists the dedicated
`task_integrity_cache`, free-time scrubbing and RKP as unsolved exploitation
obstacles.

Start here:

1. [PLAN.md](PLAN.md) — phases, candidate triage, abort criteria.
2. [RECON.md](RECON.md) — exact A52 source facts already checked.
3. [CANDIDATE-64468.md](CANDIDATE-64468.md) — the binder UAF candidate and
   the app-based bootstrap probe in [binder-bootstrap/](binder-bootstrap/README.md).
4. [LAB.md](LAB.md) — sacrificial-device and recovery requirements.

No exploit code is committed until there is a lab device and a bounded,
KASAN-instrumented trigger. Running a stock-kernel UAF trigger on the daily
A52 will panic it.
