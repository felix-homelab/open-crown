# ADR 0017 — A52 chain research branch

**Status:** Accepted for private research; no working chain
**Date:** 2026-11-21

## Context

The A52 has no public temporary-root payload. The owner asked for an original
chain based on the public research. The only plausible unpatched candidate is
CVE-2026-20971 (PROCA/FIVE `task_integrity` UAF); its public report stops at an
unprivileged DoS and lists dedicated-cache, scrub and RKP obstacles. Other
candidates are Qualcomm IOCTL UAFs/races with no public PoC.

Writing a chain is original kernel exploit development, not integration. It
needs a sacrificial device, a KASAN/KCSAN lab kernel and a recovery path. The
daily A52 must not be the lab.

## Decision

- Create `research/a52-chain/` with the plan, recon and lab requirements.
- Do not commit a trigger or exploit until a sacrificial device and lab kernel
  exist and the trigger is bounded (one run per boot, `panic_on_oops=0`).
- Keep the A52 as `example`; no catalog entry until an exact-target run and
  evidence exist.
- If P2 cannot produce a controlled primitive, record the dead end and stop.
- This branch is private-research code. It is not MIT-clean and must not be
  distributed without the same licence/provenance decision as ADR 0015.

## Consequences

**Positive:** the research is scoped and honest; no daily-device risk; a
negative P2 result is still useful.

**Negative:** high effort, uncertain outcome, and no working A52 root today.
The practical A52 root remains bootloader unlock + custom kernel (persistent),
and the practical payload run remains a supported DZJ3 device.

## Alternatives

- **Force a stock UAF trigger on the daily A52** — rejected: `panic_on_oops=1`
  panics on stock; no recovery guarantee.
- **Port a 5.10/6.x payload** — rejected: wrong kernel branch; not a port, a
  rewrite.
- **Use a supported device for the payload work** — still the shortest path to
  a working execution demo.
