# 09 — Legal and ethics

This document is not legal advice. It states the project's intended posture so
that a lawyer, a contributor, and a user can all see the lines.

## 1. The hard line: ownership

Open Crown works only with a Watch the user already owns and can activate with
their own Apple ID.

**Refused, permanently:**

- Activation Lock, iCloud Lock, or "find my" bypass.
- Pairing to a Watch that is still bound to another person's account.
- Any feature whose primary use is defeating a previous owner's lock.
- Any payload that runs on the Watch or modifies its firmware.

There is no technical mechanism that proves ownership beyond doubt. The project
therefore relies on:

- a clear, explicit ownership affirmation at setup (typed, not a checkbox);
- activation through the user's own Apple ID (a stolen, locked Watch cannot
  activate);
- a refusal to implement lock-bypass features even if requested.

This will not stop every misuse. It is the strongest practical line, and it is
documented so that misuse is a violation of the terms, not an accident of
design.

## 2. Interoperability and anti-circumvention

Reverse engineering for interoperability is treated differently across
jurisdictions. In the US, DMCA §1201 has narrow exemptions and case law; in the
EU, the Software Directive allows decompilation for interoperability under
conditions; other jurisdictions are stricter or silent.

Project posture:

- We study **our own devices**, not Apple's servers or other people's devices.
- We publish **prose, diagrams, and vectors**, not Apple binaries.
- We do not traffic in Apple keys, certificates, or internal entitlements.
- Before any public release, a lawyer in the maintainer's jurisdiction reviews
  the distribution model.
- If the review says "do not distribute in jurisdiction X", the release is
  geo-restricted or the project stays research-only.

## 3. Apple's terms

Using an Apple ID and Apple's activation service from a non-Apple client may
violate Apple's terms of service. The project does not claim otherwise. The
user is told, in plain language, that:

- they are using their own account at their own risk;
- Apple may rate-limit, lock, or terminate the account;
- the project cannot appeal an Apple decision.

The app must never make an Apple ID the only way to use the product if a
non-Apple path exists for a given feature.

## 4. Apple ID credential handling

This is the highest-risk part of the product and gets its own rules.

**Required:**

- Password is used only for the SRP exchange and zeroized immediately.
- Password is never written to disk, logs, crash reports, or backups.
- 2FA codes are never logged and expire from memory.
- Only tokens are stored, in the Android Keystore, with expiry and revocation.
- The sign-in screen states exactly what is sent where, and why.
- A "sign out and delete tokens" action exists and works offline.
- The auth code is open source and reviewed; the SRP implementation is tested
  against published vectors.
- No third-party analytics or crash SDK sees auth screens.

**Forbidden:**

- Storing the password "for convenience".
- Sending credentials anywhere except Apple's own endpoints.
- Asking for an app-specific password unless Apple requires it and the user is
  told why.
- Auto-retrying a failed sign-in in a way that risks lockout.

## 5. Exploit distribution

The catalog ships code that borrows root. That is dual-use and carries real
risk.

- Payloads are signed, reviewed by two people, and reproducible.
- The app never hides a payload's purpose; consent is explicit.
- The project does not ship a general-purpose rooting tool or a persistent su.
- Users are told the risks: panic, data loss, warranty, and the possibility
  that a vendor patch kills support.
- A payload that bricks devices is revoked immediately and the incident is
  published.

Export control: some jurisdictions restrict distribution of cryptographic
software and exploit code. The release process includes a check; if in doubt,
the maintainer seeks advice before publishing.

## 6. Privacy

- Health data is processed on-device and is opt-in.
- No telemetry. Diagnostics are local and user-exported.
- Pair records and tokens are encrypted at rest; keys live in the Keystore.
- The user can delete all data from the app, and the app documents what is
  deleted.
- Captures published for research are redacted: no identifiers, tokens, health
  values, or location.

GDPR note: the maintainer is not a data controller for on-device data that never
leaves the device. If a cloud feature is ever added, it needs a DPIA and a
lawful basis before it ships.

## 7. Distribution

- Not on Google Play: a root-capable app with an exploit catalog will not pass
  policy, and should not pretend it can.
- Distribution is direct (signed APK + checksums) and, if policy allows,
  F-Droid-style reproducible builds.
- Every release publishes: source tag, SBOM, build instructions, APK hash, and
  signing certificate fingerprint.
- The app is not sold. If donations exist, they do not buy support or priority.

## 8. Liability and warranty

The app is provided as-is, with no warranty. The EULA must say, in plain
language:

- this can break your phone or your Watch;
- you may lose data;
- you may void warranties;
- you are responsible for complying with local law and Apple's terms;
- the maintainer is not liable for damage or account action.

A disclaimer does not replace careful engineering. It is the last line, not the
first.

## 9. Security disclosure

- A `SECURITY.md` with a private contact and a 90-day disclosure window.
- Payload vulnerabilities are treated as critical.
- A revoked payload is announced in-app and in the catalog.
- The project keeps an incident log for payload failures.

## 10. Ethics summary

1. The Watch is never the target.
2. Ownership is affirmed, and lock bypass is refused.
3. Credentials are transient; tokens are minimal.
4. Root is borrowed, visible, and reversible.
5. Claims match evidence.
6. If the law and the feature conflict, the feature loses.
