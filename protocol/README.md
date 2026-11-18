# Protocol

Specs and golden vectors. This directory is the project's evidence, and the
reason [03 — Provenance](../docs/03-clean-room.md) can be audited.

## Rules

1. A spec is written from captures **before** the code.
2. A spec without a vector is a hypothesis; a vector without a capture is a
   guess. Both are allowed if labelled.
3. Every area has its own directory, its own spec, and its own vectors.
4. Protocol facts may be learned from prior art and public documentation, but
   the writing and the vectors are our own.

## Areas

| Area | What it covers | Status |
| --- | --- | --- |
| [discovery](discovery/) | Advertising, setup state, model and watchOS hints | not started |
| [pairing](pairing/) | Bootstrap, control channel, capability probes | not started |
| [security](security/) | uIKE, SPAKE2+/PPK, PIN and optical proofs | not started |
| [link](link/) | ERTM, NetworkRelay, Class-C/D, reconnect | not started |
| [ids](ids/) | IDS framing, NWSC, correlation | not started |
| [setup](setup/) | PBBridge, initial properties, PairedSync | not started |
| [faces](faces/) | Collections, mutation, readback | not started |
| [notifications](notifications/) | Bulletin format and delivery | not started |
| [health](health/) | Ingress format, anchors | not started |
| [wifi](wifi/) | Credential archive | not started |
| [telephony](telephony/) | Calls/SMS expectations (T3) | not started |

## Adding a spec

Copy [TEMPLATE.md](TEMPLATE.md) to `<area>/spec.md`, fill it in, add at least
one vector, then open the PR.
