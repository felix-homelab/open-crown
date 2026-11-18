# 09 — Legal and ethics

This document is not legal advice. It states the project's posture so that a
user, a contributor, and a reviewer can see the lines — including the ones we
chose not to draw.

## 1. The hard line: ownership

Open Crown works only with a Watch the user already owns and can activate with
their own Apple ID.

**Refused, permanently:**

- Activation Lock, iCloud Lock, or "find my" bypass.
- Pairing to a Watch still bound to another person's account.
- Any feature whose primary use is defeating a previous owner's lock.
- Any payload that runs on the Watch or modifies its firmware.

There is no technical mechanism that proves ownership beyond doubt, and this
document no longer pretends otherwise. A factory-reset Watch belonging to someone
else is indistinguishable from a spare. What the project relies on is:

- an explicit ownership affirmation at setup (typed, not a checkbox);
- activation through the user's own Apple ID, which a locked Watch cannot pass;
- a refusal to implement lock-bypass features even if requested;
- a public statement that Open Crown adds no theft capability beyond what an
  iPhone already provides.

That will not stop every misuse. It is the strongest practical line, documented
so that misuse is a violation of the terms rather than an accident of design.

## 2. Distribution risk: accepted

The project ships a signed catalog of root payloads and distributes it as source
and APK. That carries risk, and the earlier revision's posture — "a lawyer
reviews before release" — was a plan, not a fact. The accepted position is:

- **We accept the distribution risk.** The payload is dual-use, publicly
  disclosed, and extractable; we treat that as a known property, not a bug.
- Counsel is **advisory, not a gate**. If the maintainer obtains advice, it is
  published with the release; if not, the release notes say it was not obtained.
- The project is distributed openly under MIT. Anyone can audit, rebuild, or
  fork it. That is the mitigation we actually have.
- Geographical restriction is **not** used as a fig leaf. If a release is not
  lawful somewhere, the project says so and does not pretend a banner fixes it.

## 3. Interoperability and anti-circumvention

Reverse engineering for interoperability is treated differently across
jurisdictions. In the US, DMCA §1201 has narrow exemptions and case law; in the
EU, the Software Directive allows decompilation for interoperability under
conditions; other jurisdictions are stricter or silent.

Project posture:

- We study **our own devices**, not Apple's servers or other people's devices.
- We publish **prose, diagrams, and vectors**, not Apple binaries.
- We do not traffic in Apple keys, certificates, or internal entitlements.
- We do not claim a circumvention exemption we have not established. If a
  distribution is challenged, the project's answer is the published record, not
  a legal theory.

## 4. Apple's terms

Using an Apple ID and Apple's activation service from a non-Apple client may
violate Apple's terms of service. The project does not claim otherwise. The user
is told, in plain language, that:

- they are using their own account at their own risk;
- Apple may rate-limit, lock, or terminate the account;
- the project cannot appeal an Apple decision.

The app does not make an Apple ID the only way to use a feature if a non-Apple
path exists. Where the account is required (activation as currently understood),
the app says so before asking for anything.

## 5. Apple ID credential handling

The auth shape is undecided (ADR 0003). These rules hold either way:

**Required:**

- A password is used only for the exchange it is needed for and zeroized
  immediately. It is never written to disk, logs, crash reports, or backups.
- 2FA codes are never logged and expire from memory.
- Only tokens are stored, in the Android Keystore, with expiry and revocation —
  if the chosen shape stores tokens at all.
- The sign-in screen states exactly what is sent where, and why.
- A "sign out and delete tokens" action exists and works offline.
- The auth code is open source and reviewed.
- No third-party analytics or crash SDK sees auth screens.

**Forbidden:**

- Storing the password "for convenience".
- Sending credentials anywhere except Apple's own endpoints.
- Asking for an app-specific password unless Apple requires it and the user is
  told why.
- Auto-retrying a failed sign-in in a way that risks lockout.

## 6. Exploit distribution

The catalog ships code that yields root. That is dual-use and carries real risk.

- Payloads are signed, reproducible, and reviewed where a reviewer exists.
- A release without a second reviewer is labelled `unreviewed`.
- The app never hides a payload's purpose; consent is explicit.
- The project does not ship a persistent su or a boot-image modification.
- It does ship device-targeted root artifacts, and it says so. The claim "not a
  general-purpose rooting tool" was removed because anyone can extract the
  binary; "not a payload for arbitrary devices" is the accurate version.
- Users are told the risks: panic, data loss, warranty, and that a vendor patch
  can end support.
- A payload that bricks devices is revoked immediately and the incident is
  published.

Export control: some jurisdictions restrict distribution of cryptographic
software and exploit code. If a release would cross a line the maintainer can
identify, the release is published as source-only with a written note.

## 7. Privacy

- Health data is processed on-device and is opt-in.
- No telemetry. Diagnostics are local and user-exported.
- Pair records and tokens are encrypted at rest; keys live in the Keystore.
- The user can delete all data, and the app documents what is deleted.
- Published captures are redacted: no identifiers, tokens, health values, or
  location.

GDPR note: the maintainer is not a controller for data that never leaves the
device. If a cloud feature is ever added, it needs a DPIA and a lawful basis
before it ships.

## 8. Distribution

- Not on Google Play: a root-capable app with a payload catalog will not pass
  policy, and should not pretend it can.
- Distribution is direct (signed APK + checksums) and, where policy allows,
  reproducible builds.
- Every release publishes: source tag, SBOM, build instructions, APK hash,
  signing certificate fingerprint, and whether a second reviewer saw the
  payloads.
- The app is not sold. Donations do not buy support or priority.

## 9. Liability and warranty

The app is provided as-is, with no warranty (MIT). The in-app notice says, in
plain language:

- this can break your phone or your Watch;
- you may lose data;
- you may void warranties;
- you are responsible for complying with local law and Apple's terms;
- the maintainer is not liable for damage or account action.

A disclaimer does not replace careful engineering. It is the last line, not the
first.

## 10. Security disclosure

- `SECURITY.md` with a private contact and a 90-day disclosure window.
- Payload vulnerabilities are critical.
- A revoked payload is announced in-app and in the catalog.
- The project keeps an incident log for payload failures.

## 11. Ethics summary

1. The Watch is never the target.
2. Ownership is affirmed, and lock bypass is refused.
3. Credentials are transient; tokens are minimal; the shape is decided by
   experiment.
4. Privilege is borrowed for one boot, visible, and gone at reboot.
5. Claims match evidence. Unproven claims are labelled.
6. If the law and the feature conflict, the feature loses.
