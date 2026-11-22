#!/usr/bin/env python3
"""Build and validate the Open Crown feature registry.

The registry is the single source of truth for capability status. It is not a
marketing list: a row is only "verified" with physical evidence, and a blocked
row must name a blocker defined in docs/cleanroom/BLOCKERS.md.

Usage:
    python3 tools/feature-registry/build.py            # validate + write docs
    python3 tools/feature-registry/build.py --check    # validate only
"""
from __future__ import annotations

import argparse
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT_JSON = ROOT / "docs" / "cleanroom" / "feature-registry.json"
OUT_MD = ROOT / "docs" / "cleanroom" / "FEATURE_REGISTRY.md"
BLOCKERS = ROOT / "docs" / "cleanroom" / "BLOCKERS.md"

STATUSES = {
    "verified": "Physically exercised on the named hardware; all acceptance gates met.",
    "implemented_unverified": "Code and tests exist, but physical verification is outstanding.",
    "blocked": "Reachability is prevented by a named blocker with evidence.",
    "out_of_scope": "Excluded with explicit product-owner approval recorded.",
    "unimplemented": "Targeted, not built. No claim is made.",
}

CATEGORIES = [
    "Foundation",
    "Onboarding & lifecycle",
    "Pairing & setup",
    "Notifications & communication",
    "Media & remote",
    "Watch faces & personalization",
    "Time & built-in apps",
    "Health & fitness",
    "Apps & software",
    "Settings & accessibility",
    "Secure & Apple-service",
    "About, privacy & evidence",
    "Operations & quality",
]


def f(fid, cat, title, behavior, status, phase, impl=(), evidence=(), blocker=None, notes=""):
    return {
        "id": fid,
        "category": cat,
        "title": title,
        "iphone_behavior": behavior,
        "status": status,
        "blocker": blocker,
        "phase": phase,
        "implementation": list(impl),
        "tests": [],
        "evidence": list(evidence),
        "physical_verified": status == "verified",
        "notes": notes,
    }


F = []
A = F.append

# ---------------------------------------------------------------- Foundation
A(f("foundation.uniffi-seam", "Foundation", "Kotlin to Rust protocol seam",
    "The iPhone Watch app uses first-party frameworks; Open Crown's portable protocol logic is Rust behind a typed FFI.",
    "verified", 1,
    impl=["ffi/src/lib.rs", "ffi/generated/org/opencrown/ffi/opencrown.kt", "app/src/main/kotlin/org/opencrown/app/data/RustCore.kt", "app/build.gradle.kts"],
    evidence=["docs/evidence/a52-uniffi-core.md"],
    notes="Verified on SM-A528B: libopencrown.so and JNA loaded; protocol revision and core rules rendered in Diagnostics."))
A(f("foundation.observations", "Foundation", "Observation model",
    "Every value the iPhone Watch app shows reflects a live watch state or a named cached state; nothing is shown as applied before the watch confirms it.",
    "implemented_unverified", 1, impl=["app/src/main/kotlin/org/opencrown/app/model/Observation.kt"],
    evidence=["docs/design/state-model.md"],
    notes="Carries source, freshness, pair/epoch and confidence. UI must render unavailable states explicitly."))
A(f("foundation.feature-registry", "Foundation", "Feature registry",
    "The iPhone feature surface is organised by capability with a clear availability state.",
    "implemented_unverified", 1, impl=["tools/feature-registry/build.py", "docs/cleanroom/feature-registry.json"],
    evidence=["docs/cleanroom/FEATURE_REGISTRY.md"]))
A(f("foundation.device-profile", "Foundation", "Exact device profile matching",
    "The iPhone app is preinstalled; a third-party companion must decide whether its privileged payload matches the exact phone/kernel before acting.",
    "implemented_unverified", 1, impl=["runtime/src/matcher.rs", "runtime/src/devices.rs", "runtime/tests/"],
    evidence=["docs/04-payload-system.md"],
    notes="Host-tested exact model+kernel matching. The app must never hardcode an offset."))
A(f("foundation.payload-verification", "Foundation", "Signed payload verification",
    "Not applicable to a stock iPhone; required for any borrowed-privilege Android companion.",
    "implemented_unverified", 4, impl=["runtime/src/manifest.rs", "runtime/src/stage.rs", "runtime/src/revocation.rs"],
    evidence=["runtime/tests/", "docs/04-payload-system.md"]))
A(f("foundation.payload-readiness", "Foundation", "Read-only payload readiness",
    "Not applicable to a stock iPhone; shows whether a payload would be ready without executing it.",
    "implemented_unverified", 4, impl=["runtime/src/readiness.rs", "runtime/tests/"],
    evidence=["docs/adr/0014-payload-index-and-readiness.md"]))
A(f("foundation.payload-execution", "Foundation", "Borrowed-privilege payload execution",
    "Not applicable to a stock iPhone; Android needs a per-boot privileged process.",
    "implemented_unverified", 2, impl=["app/src/main/cpp/pagecache_relay/", "app/src/main/kotlin/org/opencrown/app/root/PageCacheRelayChain.kt"],
    evidence=["docs/evidence/a52-app-chain.md"],
    notes="The existing A52 chain is quarantined payload code with a provenance review open (PROVENANCE.md section 7)."))
A(f("foundation.privilege", "Foundation", "uid 0 + controller-capable domain",
    "The iPhone owns the radio through its own frameworks; an Android companion must hold the controller itself.",
    "blocked", 2, blocker="B-HCI-OWNERSHIP",
    evidence=["docs/13-status.md"],
    notes="K2a. The A52 chain gives init-domain uid 0 but has not bound HCI_CHANNEL_USER under enforcing SELinux."))
A(f("foundation.broker", "Foundation", "App-to-agent broker",
    "The iPhone Watch app talks to the watch through privileged first-party daemons.",
    "blocked", 2, blocker="B-APP-AGENT-BROKER",
    evidence=["docs/evidence/a52-broker-untrusted-app.md", "docs/evidence/a52-file-broker-probe.md", "docs/evidence/broker-probe-a52.md", "docs/evidence/a52-app-chain.md"],
    notes="Measured: shell domain connects to @dfi_init, app-domain runas_app gets EACCES; the relay is unauthenticated raw-syscall root and never returns to init."))
A(f("foundation.agent-ipc", "Foundation", "Authenticated agent IPC",
    "First-party daemons use private authenticated IPC; a third-party agent must authenticate the app caller.",
    "implemented_unverified", 2, impl=["agent/src/ipc.rs", "agent/src/protocol.rs", "agent/tests/"],
    evidence=["agent/tests/e2e.rs", "docs/adr/0013-agent-ipc-envelope.md"]))
A(f("foundation.agent-session", "Foundation", "Resident agent lifecycle",
    "The iPhone has always-on daemons; the Android agent must be visible, watchdogged and gone at reboot.",
    "unimplemented", 2,
    evidence=["docs/adr/0008-resident-agent.md"]))
A(f("foundation.controller-owner", "Foundation", "Bluetooth controller ownership",
    "The iPhone Bluetooth stack is the controller owner and exposes link control to the Watch app.",
    "blocked", 2, blocker="B-HCI-OWNERSHIP",
    evidence=["docs/02-architecture.md"]))
A(f("foundation.ble-discovery", "Foundation", "BLE discovery and advertisement decoding",
    "The iPhone discovers a nearby Watch and shows it with model and pairing state.",
    "unimplemented", 2, evidence=["docs/cleanroom/spec/01-discovery.md"],
    notes="Android scanning is implemented, but FE25 setup-metadata decode is UNKNOWN, so no advertisement is selectable."))
A(f("foundation.ble-observation", "Foundation", "Unprivileged BLE observation",
    "The iPhone scans for nearby Apple advertisements; an observation alone is never a pairing candidate.",
    "verified", 2,
    impl=["app/src/main/kotlin/org/opencrown/app/data/BleDiscoveryRepository.kt", "app/src/main/kotlin/org/opencrown/app/ui/screens/PairingScreens.kt"],
    evidence=["docs/evidence/a52-discovery.md", "docs/evidence/a52-release-discovery.md"],
    notes="Verified on SM-A528B in debug and in the release artifact: Apple advertisements listed, all Not selectable. Does not verify Watch detection or FE25 decode."))
A(f("foundation.discovery-lifecycle", "Foundation", "Discovery inventory and selection rules",
    "The iPhone bounds its candidate list, expires stale watches and rechecks a selection before connecting.",
    "implemented_unverified", 2, impl=["core/src/discovery/mod.rs", "ffi/src/lib.rs", "app/src/main/kotlin/org/opencrown/app/data/BleDiscoveryRepository.kt"],
    evidence=["docs/cleanroom/spec/01-discovery.md", "docs/evidence/a52-rust-discovery.md"],
    notes="Rust owns the inventory/tokens/TTL and the app renders them on hardware; selection success is unverified without FE25 metadata and a Watch."))
A(f("foundation.ble-connect", "Foundation", "BR/EDR + BLE link establishment",
    "The iPhone establishes the pairing and normal links automatically.",
    "unimplemented", 2, evidence=["docs/cleanroom/spec/02-pairing-security.md"]))
A(f("foundation.l2cap", "Foundation", "L2CAP / ERTM channels",
    "The iPhone opens the Watch protocol channels over L2CAP.",
    "unimplemented", 2, evidence=["protocol/link/README.md"]))
A(f("foundation.smp", "Foundation", "SMP / OOB security",
    "The iPhone completes Bluetooth security with out-of-band material during pairing.",
    "unimplemented", 2, evidence=["docs/cleanroom/spec/02-pairing-security.md"]))
A(f("foundation.link-encryption", "Foundation", "Encrypted durable link",
    "The iPhone reconnects to a paired Watch with an encrypted link and no re-pairing.",
    "unimplemented", 3, evidence=["docs/cleanroom/spec/02-pairing-security.md"]))
A(f("foundation.networkrelay", "Foundation", "NetworkRelay / ESP / IPsec",
    "The iPhone carries IP traffic to the Watch through NetworkRelay.",
    "unimplemented", 3, evidence=["docs/cleanroom/spec/00-overview.md"]))
A(f("foundation.ids", "Foundation", "IDS application session",
    "The iPhone exchanges Watch application messages over IDS.",
    "unimplemented", 3, evidence=["docs/cleanroom/spec/04-ids-session.md"],
    notes="Session rules are implemented separately; the IDS envelope and control Hello remain UNKNOWN."))
A(f("foundation.faces-delta-rules", "Foundation", "Face mutation and read-back rules",
    "The iPhone applies a face change only after the Watch reports the expected collection; a timeout or disconnect is unknown, not applied.",
    "implemented_unverified", 4, impl=["core/src/faces/mod.rs"],
    evidence=["docs/cleanroom/spec/05-faces.md"],
    notes="Complete-baseline planning, last-face refusal, exact reorder, staged transaction, 180 s read-back and 16 MiB/64 KiB limits. Wire encoding remains UNKNOWN."))
A(f("foundation.preference-writes", "Foundation", "Preference write baselines and read-back",
    "The iPhone confirms a Watch setting only after the exact key is observed with the new value.",
    "implemented_unverified", 4, impl=["core/src/settings.rs"],
    evidence=["docs/cleanroom/spec/07-settings-wifi-health.md"],
    notes="Known key/domain table, unknown-never-false, pair/session/revision baseline, exact-key read-back and timeout-is-unknown. NPS value encodings remain UNKNOWN."))
A(f("foundation.pigment-sets", "Foundation", "Pigment sets and baseline",
    "The iPhone sends both complete pigment sets and confirms them from a new mirror.",
    "implemented_unverified", 4, impl=["core/src/settings.rs"],
    evidence=["docs/cleanroom/spec/07-settings-wifi-health.md"],
    notes="1023 names/64 KiB bounds, duplicate/empty refusal, pair/session binding and exact two-set mirror confirmation. Palette namespace remains UNKNOWN."))
A(f("foundation.wifi-guards", "Foundation", "Wi-Fi provisioning guards",
    "The iPhone dispatches one encrypted network archive per setup generation and never treats an ACK as a Wi-Fi join.",
    "implemented_unverified", 6, impl=["core/src/wifi.rs"],
    evidence=["docs/cleanroom/spec/07-settings-wifi-health.md"],
    notes="WPA2-PSK/CCMP only, three reads five seconds apart, ten-second archive lifetime, one dispatch per generation and delivery-versus-join separation. Archive layout remains UNKNOWN; no passphrase is held by the guard."))
A(f("foundation.health-mirror", "Foundation", "Health defaults mirror rules",
    "The iPhone validates and merges Watch health defaults without turning missing data into zero.",
    "implemented_unverified", 5, impl=["core/src/health.rs"],
    evidence=["docs/cleanroom/spec/07-settings-wifi-health.md"],
    notes="Newer-replaces/equal-or-older-ignored, tombstones keep the prior date, missing domain becomes empty, missing value never becomes zero, bounded 4096 rows/16 MiB, transactional batches. Encryption/anchors/native acceptance remain UNKNOWN."))
A(f("foundation.pairing-rules", "Foundation", "Pairing branch and bond ordering rules",
    "The iPhone pairs through a PIN or optical branch, never both, and reports bonded only after SMP and durable storage succeed.",
    "implemented_unverified", 3, impl=["core/src/pairing.rs"],
    evidence=["docs/cleanroom/spec/02-pairing-security.md"],
    notes="Branch exclusivity, bounded PIN attempts, optical downgrade/MAC refusal, bond-store failure, replacement ordering and optical payload parsing. uIKE/SPAKE2+/SMP wire formats remain UNKNOWN."))
A(f("foundation.write-confirmation", "Foundation", "Write-then-observe confirmation",
    "The iPhone shows a setting as changed only after the Watch reports the new value.",
    "implemented_unverified", 1, impl=["core/src/write.rs"],
    evidence=["docs/design/state-model.md"],
    notes="Queued, sent, acknowledged, observed; stale epoch/target dropped, mismatch failed, timeout expired. ACK never applies. Host tests only."))
A(f("foundation.notification-claims", "Foundation", "Notification claim journal rules",
    "The iPhone applies a Watch action only to the exact notification revision it came from, and never twice.",
    "implemented_unverified", 4, impl=["core/src/notifications.rs"],
    evidence=["docs/cleanroom/spec/06-notifications.md"],
    notes="Revision-bound actions and replies, tombstone non-resurrection, terminal no-retry outcomes and 4 KiB content bound; bulletin encoding remains UNKNOWN."))
A(f("foundation.setup-rules", "Foundation", "Provisioning checkpoints and completion rules",
    "The iPhone reaches setup completion through activation, PairedSync and either a strict native barrier or an explicitly recorded owner confirmation.",
    "implemented_unverified", 3, impl=["core/src/setup/mod.rs"],
    evidence=["docs/cleanroom/spec/03-activation-setup.md"],
    notes="Ordered checkpoints, locale hard stop, ACK-is-not-completion, strict and owner-confirmed routes. PBBridge/NPS payloads remain UNKNOWN."))
A(f("foundation.ids-session-rules", "Foundation", "IDS route, epoch and reconnect rules",
    "The iPhone rejects unknown routes, drops replies from an earlier session and backs off between reconnect attempts.",
    "implemented_unverified", 3, impl=["core/src/ids/mod.rs"],
    evidence=["docs/cleanroom/spec/04-ids-session.md"],
    notes="Route catalogue, class checks, epoch correlation, receipt-versus-effect split and 15/30/60 s backoff; no wire codec."))
A(f("foundation.nwsc", "Foundation", "NWSC and feature services",
    "The iPhone uses the Watch feature services for settings, faces, health and apps.",
    "unimplemented", 4, evidence=["docs/cleanroom/spec/00-overview.md"]))
A(f("foundation.keystore-store", "Foundation", "Keystore-sealed pair records",
    "The iPhone stores pairing material in the watch's protected store; Android must seal it at rest.",
    "unimplemented", 3, evidence=["docs/02-architecture.md"]))
A(f("foundation.epoch-replay", "Foundation", "Pair identity, epoch and replay protection",
    "The iPhone refuses stale pairing material after unpair or reset.",
    "unimplemented", 3, evidence=["docs/02-architecture.md"]))
A(f("foundation.crash-migration", "Foundation", "Crash-safe state migration",
    "The iPhone upgrades its Watch database in place without losing pairing.",
    "unimplemented", 6, evidence=["docs/02-architecture.md"]))
A(f("foundation.audit-log", "Foundation", "Append-only privileged audit log",
    "The iPhone's actions are attributable to system services; the agent's actions must be attributable here.",
    "implemented_unverified", 2, impl=["agent/src/audit.rs", "agent/tests/"],
    evidence=["docs/02-architecture.md"]))
A(f("foundation.watchdog", "Foundation", "Agent watchdog and retry budget",
    "First-party daemons are managed by launchd/init; a borrowed agent must not outlive its owner.",
    "unimplemented", 2, evidence=["docs/04-payload-system.md"]))
A(f("foundation.multi-watch", "Foundation", "Multiple paired watches",
    "The iPhone Watch app lists and switches between multiple watches.",
    "unimplemented", 3, evidence=["docs/cleanroom/spec/03-activation-setup.md"]))
A(f("foundation.recovery", "Foundation", "Recovery after reboot, disconnect and reset",
    "The iPhone restores the Watch connection and setup state without user re-pairing where possible.",
    "unimplemented", 6, evidence=["docs/08-testing.md"]))
A(f("foundation.diagnostics", "Foundation", "On-device diagnostics",
    "The iPhone exposes Watch diagnostics and connection information.",
    "unimplemented", 1, evidence=["docs/02-architecture.md"]))
A(f("foundation.evidence-export", "Foundation", "Evidence export",
    "Not a user-facing iPhone feature; required for Open Crown's honesty contract.",
    "verified", 1, impl=["app/src/main/kotlin/org/opencrown/app/diagnostics/DiagnosticsExporter.kt", "app/src/main/kotlin/org/opencrown/app/ui/screens/DiagnosticsScreen.kt"],
    evidence=["docs/evidence/a52-diagnostics-export.md"],
    notes="Verified on the A52: redacted report written to app cache and offered through the share sheet; no serial, pairing key, notification content or health data."))

# ------------------------------------------------------- Onboarding & lifecycle
A(f("lifecycle.welcome", "Onboarding & lifecycle", "Welcome and onboarding",
    "The iPhone shows the Watch app welcome and pairing introduction on first launch.",
    "unimplemented", 1))
A(f("lifecycle.permissions", "Onboarding & lifecycle", "Permission requests with rationale",
    "The iPhone requests Bluetooth, notifications, camera (optical pairing) and health access with system prompts.",
    "unimplemented", 1))
A(f("lifecycle.device-check", "Onboarding & lifecycle", "Device compatibility check",
    "The iPhone is the supported device; an Android companion must show supported, compatible-untested or unsupported.",
    "unimplemented", 1))
A(f("lifecycle.consent", "Onboarding & lifecycle", "Privileged-execution consent",
    "Not applicable to a stock iPhone; Android requires an explicit, informed per-boot gesture.",
    "unimplemented", 2, evidence=["docs/04-payload-system.md"]))
A(f("lifecycle.first-run", "Onboarding & lifecycle", "First-run state restoration",
    "The iPhone resumes the Watch app where the user left it.",
    "unimplemented", 1))
A(f("lifecycle.reset-app", "Onboarding & lifecycle", "Local reset and re-onboarding",
    "The iPhone can forget a watch and return to the pairing flow.",
    "implemented_unverified", 6,
    impl=["app/src/main/kotlin/org/opencrown/app/data/CrownRepository.kt", "app/src/main/kotlin/org/opencrown/app/ui/screens/FeatureScreens.kt"],
    evidence=["docs/evidence/a52-local-data-reset.md"],
    notes="Local erase verified on the A52: onboarding, notification buffer, policy and export cache cleared, Welcome returned. A pair record refuses erasure so a watch cannot be orphaned; that branch is untested without a watch."))

# --------------------------------------------------------------- Pairing & setup
A(f("pair.discovery", "Pairing & setup", "Discover a pairable Watch",
    "The iPhone scans for a nearby Watch in pairing mode and presents it.",
    "unimplemented", 2, evidence=["docs/cleanroom/spec/01-discovery.md"]))
A(f("pair.advertisement-decode", "Pairing & setup", "Advertisement model and state decode",
    "The iPhone identifies the Watch model, pairing state and capabilities from the advertisement.",
    "unimplemented", 2, evidence=["docs/cleanroom/spec/01-discovery.md"]))
A(f("pair.selection", "Pairing & setup", "Select the watch",
    "The iPhone shows the discovered watch and starts pairing on tap.",
    "unimplemented", 2))
A(f("pair.control-security", "Pairing & setup", "Control security association",
    "The iPhone establishes the pairing control channel security association.",
    "unimplemented", 3, evidence=["docs/cleanroom/spec/02-pairing-security.md"]))
A(f("pair.pin", "Pairing & setup", "PIN pairing",
    "The iPhone displays a PIN entry during pairing and verifies the code.",
    "unimplemented", 3, evidence=["docs/cleanroom/spec/02-pairing-security.md"]))
A(f("pair.optical", "Pairing & setup", "Optical pairing",
    "The iPhone camera captures the watch face optical code to complete pairing.",
    "blocked", 3, blocker="B-OPTICAL-FIRMWARE",
    evidence=["docs/cleanroom/spec/02-pairing-security.md", "docs/cleanroom/inventory/reference-features.md"],
    notes="No lawful independent decoder exists in the public tree; Apple VisualPairing firmware is absent and not vendorable."))
A(f("pair.security-code", "Pairing & setup", "Security code confirmation",
    "The iPhone confirms a numeric security code on both devices where required.",
    "unimplemented", 3))
A(f("pair.bond-persist", "Pairing & setup", "Durable bond persistence",
    "The iPhone keeps the pairing after app restart, Bluetooth toggle and phone reboot.",
    "unimplemented", 3, evidence=["docs/02-architecture.md"]))
A(f("pair.activation", "Pairing & setup", "Activation",
    "The iPhone activates the Watch against the owner's account during setup.",
    "unimplemented", 3, evidence=["docs/cleanroom/spec/03-activation-setup.md"],
    notes="Owner-authorized activation only. Account services may force a documented blocker; do not fake success."))
A(f("pair.setup-language", "Pairing & setup", "Language and locale selection",
    "The iPhone sends the chosen language, region and locale to the Watch during setup.",
    "unimplemented", 3, evidence=["docs/cleanroom/spec/03-activation-setup.md"]))
A(f("pair.setup-wifi", "Pairing & setup", "Wi-Fi handoff",
    "The iPhone hands Wi-Fi credentials to the Watch during setup and later sync.",
    "unimplemented", 3, evidence=["docs/cleanroom/spec/03-activation-setup.md"]))
A(f("pair.pairedsync", "Pairing & setup", "PairedSync",
    "The iPhone runs PairedSync to bring the Watch to a usable state after pairing.",
    "unimplemented", 3, evidence=["docs/cleanroom/spec/03-activation-setup.md"]))
A(f("pair.setup-completion", "Pairing & setup", "Setup completion",
    "The iPhone shows setup progress and completes only when the Watch reports ready.",
    "unimplemented", 3, evidence=["docs/cleanroom/spec/03-activation-setup.md"],
    notes="Completion policy is unresolved (B-COMPLETION-POLICY); no reference proof is accepted."))
A(f("pair.face-visible", "Pairing & setup", "Visible native face after setup",
    "The Watch shows its normal face after setup completes.",
    "unimplemented", 3, evidence=["docs/06-roadmap.md"],
    notes="Requires a live face observation, not owner confirmation. See B-COMPLETION-POLICY."))
A(f("pair.reconnect", "Pairing & setup", "Encrypted reconnect without re-pairing",
    "The iPhone reconnects to a paired Watch after Bluetooth toggles and reboots.",
    "unimplemented", 3, evidence=["docs/06-roadmap.md"]))
A(f("pair.unpair", "Pairing & setup", "Unpair",
    "The iPhone unpairs and erases the watch after user confirmation.",
    "unimplemented", 4))
A(f("pair.watch-reset", "Pairing & setup", "Watch reset recovery",
    "The iPhone detects a reset Watch and requires a fresh pairing rather than resuming stale material.",
    "unimplemented", 6))
A(f("pair.cancel-retry", "Pairing & setup", "Cancel and retry",
    "The iPhone cancels pairing cleanly and offers retry without corrupting state.",
    "unimplemented", 3))
A(f("pair.multi-watch", "Pairing & setup", "Multiple watches",
    "The iPhone pairs, lists and switches between several watches.",
    "unimplemented", 3))
A(f("pair.identity-epoch", "Pairing & setup", "Pair identity and epoch",
    "The iPhone treats a reset or re-paired watch as a new identity and never mixes records.",
    "unimplemented", 3))

# ------------------------------------------- Notifications & communication
A(f("notifications.mirror", "Notifications & communication", "Notification mirroring",
    "The iPhone mirrors selected app notifications to the Watch.",
    "unimplemented", 4, evidence=["docs/cleanroom/spec/06-notifications.md"],
    notes="Local Android ingress is verified separately; watch delivery has no transport."))
A(f("notifications.ingress", "Notifications & communication", "Local notification ingress",
    "The iPhone captures notification content from selected apps before mirroring it.",
    "verified", 4,
    impl=["app/src/main/kotlin/org/opencrown/app/notifications/CapturedNotification.kt", "app/src/main/kotlin/org/opencrown/app/notifications/CrownNotificationListener.kt", "app/src/main/kotlin/org/opencrown/app/ui/screens/FeatureScreens.kt"],
    evidence=["docs/evidence/a52-notification-ingress.md"],
    notes="Verified on SM-A528B with a synthetic notification: sanitised, memory-only, never delivered or persisted. Does not verify mirroring, actions or replies."))
A(f("notifications.actions", "Notifications & communication", "Notification actions",
    "The iPhone exposes app actions on the Watch and executes the chosen action.",
    "unimplemented", 4, evidence=["docs/cleanroom/spec/06-notifications.md"]))
A(f("notifications.reply", "Notifications & communication", "Inline replies",
    "The iPhone relays text and dictation replies from the Watch to the source app.",
    "unimplemented", 4, evidence=["docs/cleanroom/spec/06-notifications.md"]))
A(f("notifications.attachments", "Notifications & communication", "Notification attachments",
    "The iPhone forwards supported images and attachments to the Watch.",
    "unimplemented", 4, evidence=["docs/cleanroom/spec/06-notifications.md"]))
A(f("notifications.sounds", "Notifications & communication", "Notification sounds and haptics",
    "The iPhone chooses the Watch notification sound and haptic per app.",
    "unimplemented", 4, evidence=["docs/cleanroom/spec/06-notifications.md"]))
A(f("notifications.per-app", "Notifications & communication", "Per-app notification settings",
    "The iPhone lets the user choose which apps mirror and whether alerts are delivered quietly.",
    "implemented_unverified", 4,
    impl=["app/src/main/kotlin/org/opencrown/app/notifications/NotificationPolicy.kt", "app/src/main/kotlin/org/opencrown/app/notifications/CrownNotificationListener.kt", "app/src/main/kotlin/org/opencrown/app/ui/screens/FeatureScreens.kt"],
    evidence=["docs/evidence/a52-notification-policy.md"],
    notes="Local per-app capture mute/unmute verified on the A52. Watch mirroring and quiet-delivery policy remain blocked by the missing transport."))
A(f("notifications.clear-sync", "Notifications & communication", "Dismissal synchronisation",
    "Clearing a notification on one device clears the other.",
    "unimplemented", 4))
A(f("comm.calls-incoming", "Notifications & communication", "Incoming call relay",
    "The iPhone rings the Watch for incoming calls and can answer on the Watch.",
    "unimplemented", 5, notes="Android telephony/audio routing feasibility is unresolved; do not claim until measured."))
A(f("comm.calls-outgoing", "Notifications & communication", "Outgoing call relay",
    "The iPhone places calls started from the Watch.",
    "unimplemented", 5))
A(f("comm.sms", "Notifications & communication", "SMS/MMS relay",
    "The iPhone relays text messages and replies between the Watch and the phone.",
    "unimplemented", 5, notes="Android SMS role and carrier constraints apply."))
A(f("comm.contacts", "Notifications & communication", "Contacts",
    "The iPhone shares contacts with the Watch for calls and messages.",
    "unimplemented", 5))
A(f("comm.voicemail", "Notifications & communication", "Voicemail indicator",
    "The iPhone signals voicemail availability to the Watch where the carrier supports it.",
    "unimplemented", 5))
A(f("comm.walkie-talkie", "Notifications & communication", "Walkie-Talkie",
    "The iPhone hosts Walkie-Talkie sessions between paired watches.",
    "blocked", 5, blocker="B-APPLE-ACCOUNT",
    evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("comm.find-phone", "Notifications & communication", "Find phone from watch",
    "The Watch can ping or locate the paired iPhone.",
    "unimplemented", 4))
A(f("comm.find-watch", "Notifications & communication", "Find watch from phone",
    "The iPhone can play a sound or show the last known watch location.",
    "unimplemented", 4))
A(f("comm.emergency-sos", "Notifications & communication", "Emergency SOS surfaces",
    "The iPhone configures Emergency SOS and shows the Watch's SOS state.",
    "unimplemented", 5, notes="Android can surface the user-facing part only where the watch reports it."))
A(f("comm.fall-detection", "Notifications & communication", "Fall detection surfaces",
    "The iPhone configures fall detection and shows its state.",
    "unimplemented", 5))
A(f("comm.screen-time", "Notifications & communication", "Screen Time",
    "The iPhone configures Screen Time for the family member's Watch.",
    "unimplemented", 5, notes="Device-management account services may force a blocker; investigate before claiming."))
A(f("comm.schooltime", "Notifications & communication", "Schooltime",
    "The iPhone enables and schedules Schooltime on the Watch.",
    "unimplemented", 5))
A(f("comm.focus", "Notifications & communication", "Focus / Do Not Disturb",
    "The iPhone syncs Focus state and mirroring to the Watch.",
    "unimplemented", 5))

# ------------------------------------------------------------- Media & remote
A(f("media.now-playing", "Media & remote", "Now Playing",
    "The iPhone shows the Watch's now-playing state and controls playback.",
    "unimplemented", 5))
A(f("media.remote-control", "Media & remote", "Playback remote",
    "The iPhone controls play/pause, skip, volume and output routing.",
    "unimplemented", 5))
A(f("media.music-library", "Media & remote", "Music library sync",
    "The iPhone syncs selected music and playlists to the Watch.",
    "unimplemented", 5, notes="DRM content is blocked by B-APP-STORE-DRM; locally owned media is in scope."))
A(f("media.podcasts", "Media & remote", "Podcasts",
    "The iPhone syncs podcast episodes and progress to the Watch.",
    "unimplemented", 5))
A(f("media.audiobooks", "Media & remote", "Audiobooks",
    "The iPhone syncs audiobooks and position to the Watch.",
    "unimplemented", 5))
A(f("media.radio", "Media & remote", "Radio",
    "The iPhone syncs radio stations and playback state.",
    "unimplemented", 5))
A(f("media.camera-remote", "Media & remote", "Camera remote",
    "The Watch previews the iPhone camera and triggers the shutter.",
    "unimplemented", 5))
A(f("media.camera-preview", "Media & remote", "Camera preview stream",
    "The iPhone streams a live camera preview to the Watch.",
    "unimplemented", 5))
A(f("media.photos-sync", "Media & remote", "Photos sync",
    "The iPhone syncs selected photos and albums to the Watch.",
    "unimplemented", 5))
A(f("media.photo-album-select", "Media & remote", "Photo album selection",
    "The iPhone lets the user choose which albums mirror to the Watch.",
    "unimplemented", 5))
A(f("media.home-controls", "Media & remote", "Home controls",
    "The iPhone configures Home accessories that appear on the Watch.",
    "blocked", 5, blocker="B-APPLE-ACCOUNT",
    evidence=["docs/cleanroom/BLOCKERS.md"],
    notes="Apple Home accessory control is account- and first-party-app bound; a local HAP bridge would not appear in the Watch Home app."))

# --------------------------------------------- Watch faces & personalization
A(f("faces.inventory", "Watch faces & personalization", "Face inventory",
    "The iPhone reads every face configuration on the Watch.",
    "unimplemented", 4, evidence=["docs/cleanroom/spec/05-faces.md"]))
A(f("faces.preview", "Watch faces & personalization", "Face preview rendering",
    "The iPhone shows a rendered preview of each face.",
    "unimplemented", 4, evidence=["docs/cleanroom/spec/05-faces.md"]))
A(f("faces.select", "Watch faces & personalization", "Select the active face",
    "The iPhone changes the face shown on the Watch.",
    "unimplemented", 4, evidence=["docs/cleanroom/spec/05-faces.md"]))
A(f("faces.edit", "Watch faces & personalization", "Edit a face",
    "The iPhone edits colours, complications, styles and options.",
    "unimplemented", 4, evidence=["docs/cleanroom/spec/05-faces.md"]))
A(f("faces.duplicate", "Watch faces & personalization", "Duplicate a face",
    "The iPhone duplicates an existing face configuration.",
    "unimplemented", 4))
A(f("faces.remove", "Watch faces & personalization", "Remove a face",
    "The iPhone removes a face from the Watch.",
    "unimplemented", 4))
A(f("faces.reorder", "Watch faces & personalization", "Reorder faces",
    "The iPhone changes the order of the face carousel.",
    "unimplemented", 4))
A(f("faces.create", "Watch faces & personalization", "Create a face",
    "The iPhone creates a face from a template and sends it to the Watch.",
    "unimplemented", 4))
A(f("faces.import", "Watch faces & personalization", "Import .watchface",
    "The iPhone imports a face container and installs its configuration.",
    "unimplemented", 4, notes="Apple face resources are not redistributable; only user-supplied containers may be read."))
A(f("faces.export", "Watch faces & personalization", "Export .watchface",
    "The iPhone exports a face container for sharing.",
    "unimplemented", 4))
A(f("faces.native-resources", "Watch faces & personalization", "Native face resources",
    "The iPhone ships the Apple face definitions and renders their native resources.",
    "blocked", 4, blocker="B-ASSET-APPLERENDER",
    evidence=["docs/cleanroom/inventory/reference-assets.md"],
    notes="Apple-rendered face resources are not redistributable. An original renderer is a separate, unblocked effort."))
A(f("faces.complications", "Watch faces & personalization", "Complications inventory",
    "The iPhone lists the complications available for each face slot.",
    "unimplemented", 4))
A(f("faces.complication-configure", "Watch faces & personalization", "Complication configuration",
    "The iPhone sets the complication in a slot.",
    "unimplemented", 4))
A(f("faces.widgets", "Watch faces & personalization", "Widgets / Smart Stack",
    "The iPhone configures the widget stack on newer watchOS.",
    "unimplemented", 4))
A(f("faces.monogram", "Watch faces & personalization", "Monogram",
    "The iPhone edits the monogram used by supported faces.",
    "unimplemented", 4, evidence=["docs/cleanroom/spec/07-settings-wifi-health.md"]))
A(f("faces.pigments", "Watch faces & personalization", "Pigments / add colours",
    "The iPhone selects and edits face colour pigments.",
    "unimplemented", 4, evidence=["docs/cleanroom/spec/07-settings-wifi-health.md"]))
A(f("faces.photo", "Watch faces & personalization", "Photos face",
    "The iPhone creates a photo face from synced albums.",
    "unimplemented", 4))

# --------------------------------------------------------- Time & built-in apps
A(f("time.alarms", "Time & built-in apps", "Alarms",
    "The iPhone reads, creates and edits Watch alarms.",
    "unimplemented", 5))
A(f("time.timers", "Time & built-in apps", "Timers",
    "The iPhone reads and controls Watch timers.",
    "unimplemented", 5))
A(f("time.stopwatch", "Time & built-in apps", "Stopwatch",
    "The iPhone reads and controls the Watch stopwatch.",
    "unimplemented", 5))
A(f("time.world-clock", "Time & built-in apps", "World clock",
    "The iPhone configures world clock cities on the Watch.",
    "unimplemented", 5))
A(f("time.bedside", "Time & built-in apps", "Bedside mode",
    "The iPhone configures bedside mode and alarm behaviour.",
    "unimplemented", 5))
A(f("apps.weather", "Time & built-in apps", "Weather",
    "The iPhone supplies weather locations and data to the Watch.",
    "unimplemented", 5))
A(f("apps.stocks", "Time & built-in apps", "Stocks",
    "The iPhone configures stock watchlist and data.",
    "unimplemented", 5))
A(f("apps.calendar", "Time & built-in apps", "Calendar",
    "The iPhone syncs calendars and upcoming events to the Watch.",
    "unimplemented", 5))
A(f("apps.reminders", "Time & built-in apps", "Reminders",
    "The iPhone syncs reminders and completion state.",
    "unimplemented", 5))
A(f("apps.mail", "Time & built-in apps", "Mail",
    "The iPhone syncs mail notifications and account metadata to the Watch.",
    "unimplemented", 5))
A(f("apps.notes", "Time & built-in apps", "Notes",
    "The iPhone syncs notes to the Watch.",
    "unimplemented", 5))
A(f("apps.maps", "Time & built-in apps", "Maps and directions",
    "The iPhone sends map directions and turn-by-turn state to the Watch.",
    "unimplemented", 5))
A(f("apps.siri", "Time & built-in apps", "Siri",
    "The Watch invokes Siri through the paired iPhone and Apple account services.",
    "blocked", 5, blocker="B-APPLE-ACCOUNT", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("apps.compass", "Time & built-in apps", "Compass and elevation",
    "The iPhone shows compass calibration and elevation data state.",
    "unimplemented", 5))
A(f("apps.noise", "Time & built-in apps", "Noise app",
    "The iPhone shows noise exposure data and settings.",
    "unimplemented", 5))

# ------------------------------------------------------------- Health & fitness
A(f("health.activity-rings", "Health & fitness", "Activity rings",
    "The iPhone shows the Watch activity rings and today's progress.",
    "unimplemented", 4, evidence=["docs/cleanroom/spec/07-settings-wifi-health.md"]))
A(f("health.workouts-live", "Health & fitness", "Live workout",
    "The iPhone starts, pauses and ends Watch workouts.",
    "unimplemented", 5))
A(f("health.workout-history", "Health & fitness", "Workout history",
    "The iPhone lists completed workouts with metrics.",
    "unimplemented", 5))
A(f("health.heart-rate", "Health & fitness", "Heart rate",
    "The iPhone reads current and historical heart rate samples.",
    "unimplemented", 5))
A(f("health.resting-hr", "Health & fitness", "Resting heart rate",
    "The iPhone shows resting heart rate.",
    "unimplemented", 5))
A(f("health.hrv", "Health & fitness", "Heart rate variability",
    "The iPhone shows HRV samples.",
    "unimplemented", 5))
A(f("health.ecg", "Health & fitness", "ECG",
    "The iPhone records and displays ECG where the hardware and region allow it.",
    "unimplemented", 5, notes="Requires watch-side acquisition and Apple's ECG classification; region/account limits may block."))
A(f("health.blood-oxygen", "Health & fitness", "Blood oxygen",
    "The iPhone shows blood oxygen samples where hardware and region allow it.",
    "unimplemented", 5))
A(f("health.temperature", "Health & fitness", "Wrist temperature",
    "The iPhone shows wrist temperature samples.",
    "unimplemented", 5))
A(f("health.sleep", "Health & fitness", "Sleep stages",
    "The iPhone shows sleep schedule and stage data.",
    "unimplemented", 5))
A(f("health.respiratory", "Health & fitness", "Respiratory rate",
    "The iPhone shows respiratory rate samples.",
    "unimplemented", 5))
A(f("health.cycle", "Health & fitness", "Cycle tracking",
    "The iPhone shows cycle tracking entries and predictions.",
    "unimplemented", 5))
A(f("health.medications", "Health & fitness", "Medications",
    "The iPhone manages medication schedules and logs.",
    "unimplemented", 5))
A(f("health.mindfulness", "Health & fitness", "Mindfulness",
    "The iPhone shows mindfulness sessions and minutes.",
    "unimplemented", 5))
A(f("health.export", "Health & fitness", "Health export",
    "The iPhone exports health data on request.",
    "unimplemented", 6, notes="Local-first export only; iCloud sync is blocked by B-APPLE-ACCOUNT."))
A(f("health.import", "Health & fitness", "Health import",
    "The iPhone imports health data and merges it with watch records.",
    "unimplemented", 6))
A(f("health.anchors", "Health & fitness", "Health sync anchors",
    "The iPhone synchronises health samples with durable anchors.",
    "unimplemented", 5))
A(f("health.privacy", "Health & fitness", "Health privacy controls",
    "The iPhone controls per-category sharing and deletion.",
    "unimplemented", 6))
A(f("health.provenance", "Health & fitness", "Health data provenance",
    "The iPhone Health app labels the source of each sample.",
    "unimplemented", 5))
A(f("health.awards", "Health & fitness", "Awards and trends",
    "The iPhone shows activity awards and trends.",
    "unimplemented", 5))

# ------------------------------------------------------------- Apps & software
A(f("apps.inventory", "Apps & software", "Installed watch apps",
    "The iPhone lists the apps installed on the Watch.",
    "unimplemented", 5))
A(f("apps.install", "Apps & software", "Install a watch app",
    "The iPhone installs a compatible iPhone app's Watch extension.",
    "blocked", 5, blocker="B-WATCH-APP-SIGNING", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("apps.update", "Apps & software", "Update a watch app",
    "The iPhone updates installed Watch apps.",
    "blocked", 5, blocker="B-WATCH-APP-SIGNING", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("apps.remove", "Apps & software", "Remove a watch app",
    "The iPhone removes an installed Watch app.",
    "unimplemented", 5))
A(f("apps.layout", "Apps & software", "App layout",
    "The iPhone arranges the Watch app grid or list.",
    "unimplemented", 5))
A(f("apps.watchos-version", "Apps & software", "watchOS version and update availability",
    "The iPhone reports the Watch software version and available updates.",
    "unimplemented", 5))
A(f("apps.watchos-update", "Apps & software", "watchOS update download",
    "The iPhone downloads and verifies a watchOS update.",
    "blocked", 5, blocker="B-WATCHOS-UPDATE", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("apps.watchos-update-install", "Apps & software", "watchOS update install",
    "The iPhone installs a watchOS update and reports progress.",
    "blocked", 5, blocker="B-WATCHOS-UPDATE", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("apps.store-purchase", "Apps & software", "App Store purchase",
    "The Watch App Store can purchase apps with the Apple account.",
    "blocked", 5, blocker="B-APP-STORE-DRM", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("apps.drm-playback", "Apps & software", "DRM playback",
    "The Watch plays protected media authorised by the account.",
    "blocked", 5, blocker="B-APP-STORE-DRM", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("apps.third-party-complications", "Apps & software", "Third-party complications",
    "The iPhone installs app complications on the Watch.",
    "blocked", 5, blocker="B-WATCH-APP-SIGNING", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("apps.background-refresh", "Apps & software", "Background app refresh",
    "The iPhone configures background refresh for Watch apps.",
    "unimplemented", 5))
A(f("apps.permissions", "Apps & software", "Watch app permissions",
    "The iPhone reviews and grants per-app permissions on the Watch.",
    "unimplemented", 5))

# ------------------------------------------------------- Settings & accessibility
A(f("settings.wrist", "Settings & accessibility", "Wrist orientation",
    "The iPhone sets which wrist the Watch is worn on.",
    "unimplemented", 4, evidence=["docs/cleanroom/spec/07-settings-wifi-health.md"]))
A(f("settings.crown-orientation", "Settings & accessibility", "Digital Crown orientation",
    "The iPhone sets which side the Digital Crown is on.",
    "unimplemented", 4))
A(f("settings.24-hour", "Settings & accessibility", "24-hour time",
    "The iPhone toggles 24-hour time on the Watch.",
    "unimplemented", 4))
A(f("settings.brightness", "Settings & accessibility", "Brightness",
    "The iPhone sets Watch brightness.",
    "unimplemented", 4))
A(f("settings.text-size", "Settings & accessibility", "Text size",
    "The iPhone sets Watch text size.",
    "unimplemented", 4))
A(f("settings.bold-text", "Settings & accessibility", "Bold text",
    "The iPhone enables bold text on the Watch.",
    "unimplemented", 4))
A(f("settings.reduce-motion", "Settings & accessibility", "Reduce motion",
    "The iPhone enables reduce motion on the Watch.",
    "unimplemented", 4))
A(f("settings.reduce-transparency", "Settings & accessibility", "Reduce transparency",
    "The iPhone enables reduce transparency on the Watch.",
    "unimplemented", 4))
A(f("settings.voiceover", "Settings & accessibility", "VoiceOver",
    "The iPhone enables and configures VoiceOver on the Watch.",
    "unimplemented", 4))
A(f("settings.zoom", "Settings & accessibility", "Zoom",
    "The iPhone enables and configures Zoom on the Watch.",
    "unimplemented", 4))
A(f("settings.haptics", "Settings & accessibility", "Haptics",
    "The iPhone sets haptic strength and behaviour.",
    "unimplemented", 4))
A(f("settings.sounds", "Settings & accessibility", "Sounds and volume",
    "The iPhone sets alert volume and sound behaviour.",
    "unimplemented", 4))
A(f("settings.passcode-status", "Settings & accessibility", "Passcode status",
    "The iPhone shows whether a Watch passcode is set and configures related behaviour.",
    "unimplemented", 4, notes="Reading status may be possible; setting or extracting a passcode is not."))
A(f("settings.wrist-detection", "Settings & accessibility", "Wrist detection",
    "The iPhone toggles wrist detection.",
    "unimplemented", 4))
A(f("settings.raise-to-wake", "Settings & accessibility", "Raise to wake",
    "The iPhone toggles raise-to-wake.",
    "unimplemented", 4))
A(f("settings.aod", "Settings & accessibility", "Always-On display",
    "The iPhone toggles Always-On display.",
    "unimplemented", 4))
A(f("settings.theater", "Settings & accessibility", "Theater mode",
    "The iPhone toggles theater mode.",
    "unimplemented", 4))
A(f("settings.water-lock", "Settings & accessibility", "Water lock",
    "The iPhone toggles water lock and ejects water.",
    "unimplemented", 4))
A(f("settings.airplane", "Settings & accessibility", "Airplane mode",
    "The iPhone toggles Watch airplane mode.",
    "unimplemented", 4))
A(f("settings.dnd", "Settings & accessibility", "Do Not Disturb",
    "The iPhone toggles Watch Do Not Disturb.",
    "unimplemented", 4))
A(f("settings.cellular-status", "Settings & accessibility", "Cellular status",
    "The iPhone shows Watch cellular status and signal.",
    "unimplemented", 4))
A(f("settings.carrier", "Settings & accessibility", "Carrier information",
    "The iPhone shows the Watch carrier and plan state.",
    "unimplemented", 4, notes="Read-only status if reported; provisioning is B-CELLULAR-PROVISIONING."))
A(f("settings.battery", "Settings & accessibility", "Battery",
    "The iPhone shows Watch battery percentage and charging state.",
    "unimplemented", 4))
A(f("settings.storage", "Settings & accessibility", "Storage",
    "The iPhone shows Watch storage capacity and usage.",
    "unimplemented", 4))
A(f("settings.device-info", "Settings & accessibility", "Device information",
    "The iPhone shows model, serial, firmware and identifiers.",
    "unimplemented", 4, notes="Identifiers stay on device and out of logs."))
A(f("settings.language", "Settings & accessibility", "Language",
    "The iPhone sets the Watch language.",
    "unimplemented", 4))
A(f("settings.region", "Settings & accessibility", "Region",
    "The iPhone sets the Watch region.",
    "unimplemented", 4))
A(f("settings.return-to-clock", "Settings & accessibility", "Return to clock",
    "The iPhone configures when the Watch returns to the clock.",
    "unimplemented", 4))
A(f("settings.background-refresh", "Settings & accessibility", "Background refresh",
    "The iPhone configures Watch background refresh.",
    "unimplemented", 4))
A(f("settings.privacy", "Settings & accessibility", "Privacy settings",
    "The iPhone reviews Watch privacy and sensor permissions.",
    "unimplemented", 4))
A(f("settings.reset", "Settings & accessibility", "Reset settings",
    "The iPhone resets Watch settings while keeping pairing where supported.",
    "unimplemented", 6))
A(f("settings.erase", "Settings & accessibility", "Erase all content and settings",
    "The iPhone erases the Watch after confirmation.",
    "unimplemented", 6, notes="Owner-authorized erase only."))
A(f("settings.accessibility-other", "Settings & accessibility", "Other accessibility settings",
    "The iPhone exposes the remaining accessibility options (contrast, tap-to-speak, etc.).",
    "unimplemented", 4))

# ---------------------------------------------------------- Secure & Apple-service
A(f("secure.apple-id", "Secure & Apple-service", "Apple ID sign-in",
    "The iPhone holds the owner's Apple ID and passes the authorised account state to the Watch.",
    "blocked", 5, blocker="B-APPLE-ACCOUNT", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("secure.icloud-sync", "Secure & Apple-service", "iCloud sync",
    "The iPhone syncs Watch-scoped data through iCloud.",
    "blocked", 5, blocker="B-APPLE-ACCOUNT", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("secure.activation-lock", "Secure & Apple-service", "Activation Lock status",
    "The iPhone can show whether Activation Lock is enabled for the Watch.",
    "blocked", 5, blocker="B-ACTIVATION-LOCK", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("secure.activation-lock-bypass", "Secure & Apple-service", "Activation Lock bypass",
    "No iPhone feature does this; it is a theft/abuse path.",
    "blocked", 5, blocker="B-ACTIVATION-LOCK", evidence=["docs/cleanroom/BLOCKERS.md"],
    notes="Refused by policy, never implemented. Listed so the refusal is auditable."))
A(f("secure.apple-pay", "Secure & Apple-service", "Apple Pay",
    "The iPhone provisions and manages Apple Pay cards for the Watch.",
    "blocked", 5, blocker="B-APPLE-PAY", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("secure.wallet", "Secure & Apple-service", "Wallet passes",
    "The iPhone adds and manages passes shown on the Watch.",
    "blocked", 5, blocker="B-APPLE-PAY", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("secure.transit", "Secure & Apple-service", "Transit cards",
    "The iPhone provisions transit cards and express travel state.",
    "blocked", 5, blocker="B-APPLE-PAY", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("secure.secure-element", "Secure & Apple-service", "Secure element provisioning",
    "The iPhone provisions Apple-controlled applets into the Watch secure element.",
    "blocked", 5, blocker="B-APPLE-PAY", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("secure.cellular-provisioning", "Secure & Apple-service", "Cellular plan provisioning",
    "The iPhone activates a Watch eSIM with the carrier.",
    "blocked", 5, blocker="B-CELLULAR-PROVISIONING", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("secure.appstore", "Secure & Apple-service", "App Store account",
    "The iPhone authorises Watch App Store account access.",
    "blocked", 5, blocker="B-APP-STORE-DRM", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("secure.imessage", "Secure & Apple-service", "iMessage",
    "The iPhone relays iMessage conversations to the Watch.",
    "blocked", 5, blocker="B-APPLE-ACCOUNT", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("secure.facetime", "Secure & Apple-service", "FaceTime",
    "The iPhone relays FaceTime calls to the Watch.",
    "blocked", 5, blocker="B-APPLE-ACCOUNT", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("secure.findmy-network", "Secure & Apple-service", "Find My network",
    "The iPhone uses Apple's Find My network for Watch location.",
    "blocked", 5, blocker="B-APPLE-ACCOUNT", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("secure.health-icloud", "Secure & Apple-service", "Health iCloud sync",
    "The iPhone syncs Health data through iCloud.",
    "blocked", 6, blocker="B-APPLE-ACCOUNT", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("secure.continuity-unlock", "Secure & Apple-service", "Continuity unlock",
    "The Watch unlocks a Mac or contributes to continuity authentication.",
    "blocked", 6, blocker="B-CONTINUITY-UNLOCK", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("secure.account-2fa", "Secure & Apple-service", "Apple account two-factor authentication",
    "The iPhone handles account 2FA prompts for the Watch.",
    "blocked", 5, blocker="B-APPLE-ACCOUNT", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("secure.stolen-device", "Secure & Apple-service", "Stolen device support",
    "No legitimate iPhone Watch feature supports operating a device the user does not own.",
    "blocked", 5, blocker="B-ACTIVATION-LOCK", evidence=["docs/cleanroom/BLOCKERS.md"],
    notes="Refused by policy. The row exists so the refusal is visible and testable."))
A(f("family.setup", "Secure & Apple-service", "Family Setup",
    "The iPhone can set up a Watch for a family member without that person owning an iPhone.",
    "blocked", 6, blocker="B-APPLE-ACCOUNT", evidence=["docs/cleanroom/BLOCKERS.md"],
    notes="Requires a family Apple Account and Apple service authorization; not a bypass path."))
A(f("family.schooltime", "Secure & Apple-service", "Schooltime",
    "The iPhone manages a restricted school-hours mode for a family Watch.",
    "blocked", 6, blocker="B-APPLE-ACCOUNT", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("family.reports", "Secure & Apple-service", "Family reports",
    "The iPhone shows authorised activity and location reports for a family member.",
    "blocked", 6, blocker="B-APPLE-ACCOUNT", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("family.limits", "Secure & Apple-service", "Family restrictions",
    "The iPhone applies contacts, communication and content restrictions.",
    "blocked", 6, blocker="B-APPLE-ACCOUNT", evidence=["docs/cleanroom/BLOCKERS.md"]))
A(f("family.services", "Secure & Apple-service", "Family Apple services",
    "The iPhone enables family-scoped Apple services on the Watch.",
    "blocked", 6, blocker="B-APPLE-ACCOUNT", evidence=["docs/cleanroom/BLOCKERS.md"]))

# ------------------------------------------------- About, privacy & evidence
A(f("about.privacy-policy", "About, privacy & evidence", "Privacy policy",
    "The iPhone Watch app links privacy information for Health and account data.",
    "implemented_unverified", 6, impl=["docs/privacy.md", "app/src/main/kotlin/org/opencrown/app/ui/screens/PrivacyScreen.kt"],
    evidence=["docs/evidence/a52-privacy-screen.md"],
    notes="Documented and rendered on the A52 with live permission state. Not a legal review."))
A(f("about.security-notes", "About, privacy & evidence", "Security notes",
    "Not a stock iPhone screen; Open Crown documents its borrowed privilege and agent surface.",
    "implemented_unverified", 6, impl=["docs/security.md", "SECURITY.md"],
    evidence=["docs/security.md"],
    notes="Trust boundaries, secret handling, privileged payload quarantine and supply-chain gaps documented."))
A(f("about.evidence", "About, privacy & evidence", "Evidence and blockers",
    "Not a stock iPhone screen; Open Crown shows what is verified, what is unverified and what is blocked.",
    "implemented_unverified", 1, impl=["app/src/main/kotlin/org/opencrown/app/ui/screens/EvidenceScreens.kt"],
    evidence=["docs/cleanroom/FEATURE_REGISTRY.md"], notes="Registry browser with filters, blocker detail and feature rows; physically exercised on the A52."))
A(f("about.open-source", "About, privacy & evidence", "Open-source licences",
    "The iPhone Settings app shows software licences.",
    "implemented_unverified", 6,
    impl=["app/src/main/kotlin/org/opencrown/app/ui/screens/LicencesScreen.kt", "docs/THIRD_PARTY.md"],
    evidence=["docs/evidence/a52-licences.md"],
    notes="Physical screen and inventory match. Licence texts and a release SBOM are explicitly still open gates."))
A(f("about.blockers", "About, privacy & evidence", "Blocker transparency",
    "No equivalent; the product must not hide blocked iPhone parity behind a generic error.",
    "implemented_unverified", 1, impl=["docs/cleanroom/BLOCKERS.md", "app/src/main/kotlin/org/opencrown/app/ui/screens/EvidenceScreens.kt"],
    evidence=["docs/cleanroom/BLOCKERS.md"], notes="Blocked rows name a catalog entry with dependency and reason in the app."))

# ------------------------------------------------------ Operations & quality
A(f("ops.cleanroom-audit", "Operations & quality", "Clean-room similarity audit",
    "Not applicable to the iPhone app; required for this reimplementation.",
    "implemented_unverified", 6, impl=["docs/cleanroom/audit/phase-0-baseline.md", "docs/cleanroom/audit/phase-1-similarity.md", "tools/cleanroom/check-boundary.py", "tools/cleanroom/similarity-audit.py"],
    evidence=["docs/cleanroom/inventory/similarity-plan.md", "docs/cleanroom/audit/phase-1-similarity.md"]))
A(f("ops.quarantine-guard", "Operations & quality", "Reference quarantine guard",
    "No iPhone equivalent; keeps the quarantined reference out of tracked files and build inputs.",
    "implemented_unverified", 6, impl=["tools/cleanroom/check-boundary.py"],
    evidence=["docs/cleanroom/REFERENCE_BOUNDARY.md"]))
A(f("ops.license-audit", "Operations & quality", "Licence audit",
    "Not applicable; required because the reference has no licence.",
    "unimplemented", 6))
A(f("ops.secret-scan", "Operations & quality", "Secret and identifier scan",
    "Not applicable; Open Crown must keep pairing keys and device identifiers out of logs and commits.",
    "implemented_unverified", 6, impl=["tools/cleanroom/secret-scan.py", "tools/lib/opencrown_tools/secrets.py"],
    evidence=["tools/tests/test_secret_scan.py"],
    notes="Scans tracked text for private-key blocks, provider tokens and signing-material suffixes without echoing matches; wired into CI. Device serials are not pattern-scanned."))
A(f("ops.update-path", "Operations & quality", "In-place app update",
    "A companion must be updatable without losing local state or requiring a new pairing.",
    "verified", 6,
    impl=["app/src/main/kotlin/org/opencrown/app/data/PairRecordStore.kt", "app/src/main/kotlin/org/opencrown/app/data/OnboardingStore.kt", "app/src/main/kotlin/org/opencrown/app/notifications/NotificationPolicy.kt"],
    evidence=["docs/evidence/a52-update-path.md"],
    notes="Verified on the A52 with the same debug key: onboarding and per-app mute survived install -r, and a new muted notification was not captured. No pair record existed, so sealed-state preservation is not proven."))
A(f("ops.recovery-drill", "Operations & quality", "Recovery drill",
    "The iPhone recovers from reboot, unpair, watch reset and app reinstall.",
    "implemented_unverified", 6, impl=["app/src/main/kotlin/org/opencrown/app/MainViewModel.kt", "app/src/main/kotlin/org/opencrown/app/data/OnboardingStore.kt"],
    evidence=["docs/evidence/a52-recovery-reinstall.md"],
    notes="App reinstall and reboot tested on the A52: onboarding reset, listener access absent, core loads. Watch unpair/reset and populated-store recovery are not testable without a Watch."))
A(f("ops.release-build", "Operations & quality", "Release build and install path",
    "The iPhone app ships through the App Store; Open Crown ships an APK with reproducible checks.",
    "implemented_unverified", 6,
    impl=["app/proguard-rules.pro", "app/build.gradle.kts", "app/src/main/cpp/CMakeLists.txt", "app/src/main/kotlin/org/opencrown/app/root/PageCacheRelayChain.kt"],
    evidence=["docs/evidence/a52-release-build.md", "docs/evidence/a52-release-no-relay.md", "docs/evidence/a52-release-reproducibility.md", "docs/evidence/a52-release-signing-gate.md", "docs/evidence/a52-release-sbom.md"],
    notes="R8 release starts and loads UniFFI on the A52. Environment-based signing and tools/release/verify-artifact.sh enforce signature, minSdk 33 and exclusion of the quarantined relay/payload; CI runs the gate with a throwaway key. Two clean builds at b0c62b0 were byte-identical. Production key custody, SBOM and distribution are not verified."))
A(f("ops.negative-tests", "Operations & quality", "Fuzz and negative tests",
    "Not user-visible; required for protocol robustness.",
    "unimplemented", 6))


def blocker_meta() -> dict:
    text = BLOCKERS.read_text(encoding="utf-8")
    meta = {}
    current = None
    for line in text.splitlines():
        if line.startswith("### "):
            current = line[4:].strip()
            meta[current] = {"dependency": "", "reason": ""}
        elif current and line.startswith("**Dependency:**"):
            meta[current]["dependency"] = line.split("**Dependency:**", 1)[1].strip().replace(":", ": ")
        elif current and line.startswith("**Why it is blocked:**"):
            meta[current]["reason"] = line.split("**Why it is blocked:**", 1)[1].strip()
    for value in meta.values():
        if not value["dependency"]:
            value["dependency"] = "Unspecified dependency"
        if not value["reason"]:
            value["reason"] = "See docs/cleanroom/BLOCKERS.md."
    return meta


def blocker_ids() -> set:
    return set(blocker_meta().keys())


def validate(rows):
    errors = []
    ids = [r["id"] for r in rows]
    if len(ids) != len(set(ids)):
        errors.append("duplicate feature ids")
    known = blocker_ids()
    for r in rows:
        prefix = r["id"] + ": "
        if r["status"] not in STATUSES:
            errors.append(prefix + "invalid status " + str(r["status"]))
        if r["category"] not in CATEGORIES:
            errors.append(prefix + "invalid category " + str(r["category"]))
        if r["status"] == "blocked":
            if not r["blocker"]:
                errors.append(prefix + "blocked without a blocker id")
            elif r["blocker"] not in known:
                errors.append(prefix + "unknown blocker " + r["blocker"])
            if not r["evidence"]:
                errors.append(prefix + "blocked without evidence")
        elif r["blocker"]:
            errors.append(prefix + "non-blocked row names a blocker")
        if r["status"] == "verified":
            if not r["physical_verified"]:
                errors.append(prefix + "verified but physical_verified is false")
            if not r["evidence"]:
                errors.append(prefix + "verified without evidence")
        if r["status"] != "verified" and r["physical_verified"]:
            errors.append(prefix + "physical_verified true on non-verified row")
        if not r["iphone_behavior"].strip():
            errors.append(r["id"] + ": missing iphone_behavior")
        if not isinstance(r["phase"], int) or r["phase"] < 0 or r["phase"] > 6:
            errors.append(r["id"] + ": invalid phase")
    return errors


def counts(rows):
    out = {s: 0 for s in STATUSES}
    cats = {}
    for r in rows:
        out[r["status"]] += 1
        cats.setdefault(r["category"], {s: 0 for s in STATUSES})
        cats[r["category"]][r["status"]] += 1
    return out, cats


def render_md(rows):
    c, cats = counts(rows)
    lines = []
    lines.append("# Feature registry")
    lines.append("")
    lines.append("Generated from [feature-registry.json](feature-registry.json) by")
    lines.append("tools/feature-registry/build.py. Do not edit this file by hand.")
    lines.append("")
    lines.append("Status vocabulary:")
    lines.append("")
    for s, desc in STATUSES.items():
        lines.append("- **" + s + "** - " + desc)
    lines.append("")
    lines.append("A row may be unimplemented: it is targeted work, not a claim. The")
    lines.append("release gate requires no unimplemented rows remain; at that point every")
    lines.append("row is verified, blocked, or out_of_scope with recorded approval.")
    lines.append("")
    lines.append("## Counts")
    lines.append("")
    lines.append("| Status | Count |")
    lines.append("| --- | ---: |")
    for s in STATUSES:
        lines.append("| " + s + " | " + str(c[s]) + " |")
    lines.append("| **total** | **" + str(len(rows)) + "** |")
    lines.append("")
    lines.append("## By category")
    lines.append("")
    header = "| Category | " + " | ".join(STATUSES) + " | total |"
    lines.append(header)
    lines.append("| --- | " + " | ".join("---:" for _ in STATUSES) + " | ---: |")
    for cat in CATEGORIES:
        row = cats.get(cat, {s: 0 for s in STATUSES})
        lines.append("| " + cat + " | " + " | ".join(str(row[s]) for s in STATUSES) + " | " + str(sum(row.values())) + " |")
    lines.append("")
    lines.append("## Rows")
    lines.append("")
    for cat in CATEGORIES:
        cat_rows = [r for r in rows if r["category"] == cat]
        if not cat_rows:
            continue
        lines.append("### " + cat)
        lines.append("")
        lines.append("| Id | Capability | Status | Blocker | Phase |")
        lines.append("| --- | --- | --- | --- | ---: |")
        for r in cat_rows:
            b = "[" + r["blocker"] + "](BLOCKERS.md#" + r["blocker"].lower() + ")" if r["blocker"] else ""
            lines.append("| " + r["id"] + " | " + r["title"] + " | " + r["status"] + " | " + b + " | " + str(r["phase"]) + " |")
        lines.append("")
    lines.append("## Blocked rows and why")
    lines.append("")
    for r in rows:
        if r["status"] == "blocked":
            lines.append("- " + r["id"] + " - [" + r["blocker"] + "](BLOCKERS.md#" + r["blocker"].lower() + "): " + (r["notes"] or r["iphone_behavior"]))
    lines.append("")
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="validate without writing")
    args = ap.parse_args()
    errors = validate(F)
    if errors:
        print("feature registry: FAILED", file=sys.stderr)
        for e in errors:
            print("  " + e, file=sys.stderr)
        return 1
    payload = {
        "schema": 1,
        "updated": "2026-11-22",
        "source_of_truth": "tools/feature-registry/build.py",
        "status_vocabulary": STATUSES,
        "categories": CATEGORIES,
        "blockers": blocker_meta(),
        "features": F,
    }
    if not args.check:
        OUT_JSON.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        OUT_MD.write_text(render_md(F), encoding="utf-8")
    c, _ = counts(F)
    print("feature registry: " + str(len(F)) + " rows; " + ", ".join(k + "=" + str(v) for k, v in c.items() if v))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())