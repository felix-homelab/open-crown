# Evidence

Every hardware test produces a note here. The format is in
[08 — Testing](../08-testing.md#8-evidence-format).

@@@text
date, operator, phone build, watch model, watchOS, payload id
what was done, what was observed, what was captured
result: pass / fail / partial
attachments: capture hash, screenshots, logs (redacted)
@@@

Rules:

- One file per test session, named @@YYYY-MM-DD-<topic>.md@@.
- Raw captures do not live here; they are referenced by hash and stored outside
  the repository (see @@.gitignore@@).
- Redact identifiers, tokens, and health values before committing.
- A claim without a note is reverted.
