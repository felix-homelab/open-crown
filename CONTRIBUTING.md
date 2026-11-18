# Contributing

## Before code

The project is spec-first. A protocol change needs, in order:

1. a spec under @@protocol/<area>/@@ following @@protocol/TEMPLATE.md@@;
2. at least one golden vector from a capture;
3. an implementation that consumes the vector;
4. a hardware note under @@docs/evidence/@@ if it touches a device.

A pull request that skips step 1 or 2 is closed, not reviewed.

## Provenance

Every contribution carries this attestation in the PR:

@@@text
I confirm that this contribution was written from the linked specification
and my own captures, and that no source, asset, or binary from another
project was copied into it.
@@@

The prior art is named in [docs/provenance.md](docs/provenance.md). Read
[docs/03-clean-room.md](docs/03-clean-room.md) before you read that project's
code. Ideas are allowed; expression is not.

## Style

- Rust: @@cargo fmt@@, @@cargo clippy -- -D warnings@@, no new dependencies
  without a line in the PR explaining why.
- Kotlin: @@ktlint@@, @@detekt@@; Compose for UI.
- Commit messages: imperative subject, a body that says why, and a reference to
  the spec or ADR when one exists.

## Licensing

By contributing you agree that your contribution is licensed under the MIT
licence in [LICENSE](LICENSE).
