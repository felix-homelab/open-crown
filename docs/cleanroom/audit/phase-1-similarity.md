# Phase 1 similarity audit

Date: 2026-11-22
Auditor: lead (Lane C function; not independently staffed)
Tool: [tools/cleanroom/similarity-audit.py](../../../tools/cleanroom/similarity-audit.py)
Reference: quarantined local copy at reference/apple_watch_connector

## Method

- Compare each product source file with reference source files of the same
  language (Kotlin, Rust, Java, Dart, C, header).
- Strip C-style comments. Treat a quoted string as one token so copied string
  tables are visible rather than washed out.
- Lowercase tokens. Count shared 10-token shingles; score a product file by
  containment (shared shingles / product shingles).
- Flag at 0.20 containment. Review the 0.08–0.20 band manually.
- Exclude build outputs, toolchains, generated bindings and the reference tree
  from the product side.
- Report shared identifiers separately; generic language and public protocol
  terms are expected.

Limits: token shingles catch literal/structural copying, not semantic
reimplementation. A low score is necessary but not sufficient for originality.
The product's C payload has no reference counterpart and is handled separately
by the provenance finding.

## Results

~~~text
product_files=94 reference_files=794
max_containment=0.189 findings=0
shared_identifiers=1477
~~~

No product file met the finding threshold.

### Review band

| Product file | Closest reference | Containment | Disposition |
| --- | --- | ---: | --- |
| agent/build.rs | crates/watch-transport-linux/src/bin/windows_hci.rs | 0.160 | Generic prost/protoc build boilerplate; no copied identifiers or comments |
| core/src/error.rs | crates/watch-protocol/src/framing.rs | 0.189 | Generic Rust error enum and Display match; no copied identifiers or comments |
| agent/src/audit.rs | crates/watch-transport-linux/src/adapters.rs | 0.099 | Generic std::fs/Write and enum names; no copied structure |

Manual review of the three files found no shared comments, no distinctive
identifier sequences and no copied test names. The shared shingles are common
Rust idioms (match arms, error formatting, cargo build directives).

### Shared identifiers

1,477 identifiers occur in both trees. The sample is dominated by generic terms
(about, absent, accept, access, action, adapter, address, agent, align) and
public protocol vocabulary (activation, advertisement, activation_confirmed).
No reference-internal project prefix or class name was copied into product code.
The full intersection is reproducible by running the tool; it is not committed
as a list because it is large and mostly noise.

## Disposition

- No one-to-one copy found in this pass.
- No product file required renaming or rewriting.
- The audit is not a release sign-off: it must be rerun at each phase gate and
  before release, and it cannot certify semantic originality.
- Generated bindings under ffi/generated are excluded from this comparison and
  are reviewed as generated output instead.

## Reproduction

~~~sh
# reference/ must be present locally; CI intentionally does not fetch it
python3 tools/cleanroom/similarity-audit.py
PYTHONPATH=tools/lib python3 -m unittest tools.tests.test_similarity
~~~

Exit code 1 means at least one file met the 0.20 threshold; exit 2 means the
reference tree is absent.
