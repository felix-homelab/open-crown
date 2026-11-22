# 12 — Decisions

This is the decision log produced by the document review of 2026-11-18. Each
entry names the decision, the evidence behind it, and what it changes. The ADRs
are the formal record; this page is the narrative that ties them together.

## 1. The prior art is named

**Decision:** cite [apple_watch_connector](https://github.com/LordixDemon/apple_watch_connector)
by name in the README, the provenance log, and the feasibility doc.

**Evidence:** it demonstrates fresh pairing, device activation, and encrypted
reconnect on Linux and Windows, and on Android with Magisk.

**Changes:** the feasibility chapter, the provenance policy, and the effort
estimate. It also removes the "prior companion projects" ambiguity.

## 2. Borrowed privilege means uid 0 + a domain

**Decision:** the payload contract is `uid 0 + an SELinux context that can hold
the controller`. [ADR 0007](adr/0007-uid0-plus-domain.md).

**Evidence:** the prior art ships a priv-app, a hidden-API whitelist and SELinux
rules, and states that root access alone does not supply that configuration.

**Changes:** the payload schema, the architecture, the threat model, and both K2a and K2b.

## 3. K2a runs first

**Decision:** the first experiment is the first payload, on a stock phone: the
payload yields `uid 0` and the probe takes the controller. There is no harness
root ([ADR 0011](adr/0011-no-harness-root.md)).

**Changes:** [06 — Roadmap](06-roadmap.md) starts at Phase 0, not Phase 1.
Scaffolding moved behind the decision.

## 4. The agent is resident

**Decision:** the agent stays alive while a watch is paired, behind a foreground
service. [ADR 0008](adr/0008-resident-agent.md).

**Evidence:** notifications require a live link; the prior art runs its root
owner behind a foreground service.

**Changes:** the charter's "no always-on daemon" persona is removed; consent is
once per boot; the security section treats a long-lived root process as the
norm.

## 5. Activation is a decision, not an assumption

**Decision:** ADR 0003 is **Proposed**. Phase 2 determines whether the Watch
holds the Apple ID session and the phone relays input, or the phone authenticates.

**Changes:** the credential chapter is written for both shapes; no SRP code is
committed before the experiment.

## 6. Supply is public n-days

**Decision:** exact-target payloads built on public n-days, with a supported
list and an explicit update warning. [ADR 0009](adr/0009-payload-supply.md).

**Changes:** the README's "what it needs today" box, the support policy, the
risk register (R16, R18, R19).

## 7. The rewrite is the goal

**Decision:** the rewrite is a maintainability decision and is priced as such.

**Changes:** ADR 0001's rationale, the effort table, and the stack chapter. The
rewrite is no longer justified by JNI ergonomics alone.

## 8. The scope is a portfolio with an order

**Decision:** all three projects are in scope, shipped in a defined order: the
research record continuously, a rooted-device companion as v1, the catalog when
the supply and review gates pass. [ADR 0010](adr/0010-ship-order.md).

**Changes:** the README status, the roadmap, and ADR 0004's guardrail.

## 9. The licence is MIT and the risk is accepted

**Decision:** MIT; distribution risk accepted and documented; counsel advisory,
not a gate. [ADR 0006](adr/0006-license-mit.md).

**Changes:** [09 — Legal and ethics](09-legal-ethics.md), the README licence
section, and the removal of "not a general-purpose rooting tool".

## 10. The doc set follows its own rules

**Decision:** no claim without a source; no gate that a single maintainer cannot
pass; no contradiction between documents; every count appears once.

**Changes:** citations in the feasibility doc, named hardware in the test
matrix, one retry number (three), one effort story, and a single definition of
activation.

## 11. No harness root

**Decision:** no manual rooting step at any point, in development or testing.
[ADR 0011](adr/0011-no-harness-root.md).

**Changes:** Phase 0 now consumes a real payload, so K2a and K2b are exercised
together; the runbook has no Magisk step; the probe reports stages separately so
a failure is still attributable.

## 12. Readiness is data, not execution

**Decision:** M1 lists payloads from a source-level index and computes readiness
from data on disk only: index target, bundle presence, manifest, pinned
signature, artifacts, revocation and agent version.
[ADR 0014](adr/0014-payload-index-and-readiness.md).

**Changes:** `catalog/payload-index.json`, `runtime::readiness`, the probe FFI,
and the app's probe/payload screens. Probing never stages, mints a token, runs a
payload, or requests root.

## 13. External chains are not part of the tree

External chain directories, quarantined payload bundles, and the external CLI
are removed. The only chain built into the app is the A52 page-cache
experiment under `app/src/main/cpp/pagecache_relay/`, described by
[ADR 0017](adr/0017-pagecache-relay-payload.md).

## 14. Execution is a consent-gated launch contract

**Decision:** `runtime::execution` launches a staged entry only with consent
that matches the manifest, a re-hashed entry, a token and a boot id; it does
not acquire privilege. [ADR 0016](adr/0016-consent-gated-execution.md).

**Changes:** the execution supervisor and its host tests. It is not yet wired
to Kotlin or run on the A52.

## Left open on purpose

1. **Which side authenticates** — Phase 2, ADR 0003 stays Proposed.
2. **Transport shape A or B** — Phase 0 decides; the architecture supports both.
3. **Whether the catalog ever returns to v1** — if K2a and K2b pass early.

These are open because they are experiments. Everything else in this set is
either decided or labelled as a hypothesis.
