# 10 — Risks

Scored L (likelihood) × I (impact), each 1–5. Anything with a score ≥ 15 has a
named owner and a written mitigation before the phase that exposes it.

## 1. Register

| # | Risk | L | I | Score | Mitigation |
| --- | --- | --- | --- | --- | --- |
| R1 | Pairing turns out to need an iPhone-held secret | 3 | 5 | 15 | Kill gate K3; the Phase 3 experiment is designed to find this early |
| R2 | No reliable exploit for the target device family | 3 | 5 | 15 | Multi-family catalog; bring-your-own-root; bridge fallback; explicit supported list |
| R3 | Exploit panics or bricks a phone | 3 | 4 | 12 | Memory-only payloads, preflight, watchdog, retry budget, brick drill |
| R4 | Apple ID sign-in triggers lockout or attestation | 3 | 4 | 12 | Conservative retry, clear warnings, "iPhone-once" fallback, kill gate K4 |
| R5 | Apple legal action or takedown | 2 | 5 | 10 | No Apple binaries/keys; jurisdiction review; research-only fallback |
| R6 | watchOS update breaks the protocol | 4 | 3 | 12 | Versioned specs, capture suite, per-release delta, "don't update" guidance |
| R7 | Android update breaks the exploit | 4 | 3 | 12 | Family-based catalog, support matrix, retire policy |
| R8 | Single test device hides portability bugs | 4 | 3 | 12 | Expand matrix before claiming support; label everything "tested on" |
| R9 | Maintainer burnout / abandonment | 3 | 4 | 12 | Scope discipline, documented architecture, small releases, no promises |
| R10 | Malicious or broken payload in catalog | 2 | 5 | 10 | Two reviewers, offline signing, transparency log, revocation |
| R11 | Credential handling leaks a password | 2 | 5 | 10 | Zeroization, no logging, open auth code, security review |
| R12 | Health data leak | 2 | 5 | 10 | Local-first, encrypted at rest, opt-in, export/delete |
| R13 | Scope creep (calls/SMS, watch apps) | 4 | 2 | 8 | T3 is best effort; watch apps permanently out |
| R14 | Legal ambiguity in a target jurisdiction | 3 | 3 | 9 | Counsel before release; geo-restrict if needed |
| R15 | Supply-chain compromise of the build | 2 | 4 | 8 | Pinned deps, SBOM, reproducible builds, signed releases |

## 2. The three that actually matter

### R1 — the hidden iPhone dependency

If pairing requires a value that only an iPhone can derive, the product's core
promise fails. This is why Phase 3 starts with a capture-and-classify
experiment, not with code: we need to know which messages are local before
committing months.

**Early warning:** any step where the Watch accepts a message only after a
round-trip we cannot reproduce, or where a key never appears on the wire.

### R2 — exploit fragility

Borrowed root is the product's foundation and its weakest point. A device that
loses its payload becomes discovery-only overnight. The catalog must be
family-based (so one technique covers many builds), and the app must degrade
gracefully rather than lie.

**Early warning:** a vendor security bulletin that lists the family's bug; a new
kernel build with different offsets; an exploit success rate below 80% on the
test device.

### R9 — one person

The project is long, the domain is deep, and the payoff is a fragile companion
for a small audience. Burnout is a real risk, and it is mitigated by scope
discipline: ship T0–T1 first, treat T2 as a bonus, and keep T3 behind a gate.

## 3. Assumptions that could invalidate the plan

1. The Watch can be paired without an iPhone physically present.
2. Activation can be relayed with the owner's Apple ID from a non-Apple client.
3. A per-boot exploit can be maintained for at least one device family.
4. The protocol is observable in captures (not fully out-of-band encrypted).
5. Users will accept a root-capable app for this use case.

Each assumption has a test in Phases 2–4. If one fails, the project re-scopes;
it does not "push through".

## 4. What we will not do to de-risk

- We will not ship a stolen-device feature to grow the audience.
- We will not hide root from the user to make the app feel smoother.
- We will not claim support for a device we have not tested.
- We will not use Apple binaries or keys to shortcut the protocol work.
