# 11 — Glossary

| Term | Meaning |
| --- | --- |
| **Activation** | The Apple service step that makes a Watch usable. It is **not** the same as an Apple ID session; device activation is demonstrated by prior art, account binding is an open question. |
| **Agent** | The privileged process that holds the Bluetooth controller for one boot. A daemon while a watch is paired. |
| **Albert / Buddy** | Internal names for the Watch setup UI and its completion observers. |
| **Bond** | The long-term Bluetooth security relationship between phone and Watch. Survives reboots; the link key lives in the controller. |
| **Borrowed privilege** | `uid 0` obtained from a payload for one boot, plus the SELinux domain needed to hold the controller. Not an installation. |
| **Bridge** | A desktop host that runs the protocol when the phone has no payload. A fallback and a research tool, not the primary architecture. |
| **Capability** | A permission the agent grants for a boot: `root`, `domain`, `controller`, `shim`, `diag`. |
| **Catalog** | The signed, versioned collection of payloads the app can run. |
| **Checkpoint** | A durable, ordered marker of session progress. Resuming never replays a non-idempotent step. |
| **Class-C / Class-D** | NetworkRelay link classes used before IDS comes up. |
| **Clean room** | Here: provenance discipline — reimplement from specs and captures without copying expression. Not a two-team legal clean room. |
| **Crown** | The project's name for the companion/session layer. |
| **Discovery** | Finding and identifying a Watch via BLE advertising. Needs no privilege. |
| **Domain** | The SELinux context a process runs in. `uid 0` without the right domain cannot hold the controller. |
| **ERTM** | Enhanced Retransmission Mode, a Classic L2CAP mode used by the Watch's data channels. |
| **HAL** | Android's Bluetooth Hardware Abstraction Layer. Owning it is transport shape A. |
| **HCI** | Host Controller Interface: the command/event boundary between the OS and the controller. Owning the user channel is transport shape B. |
| **IDS** | The Watch's paired-link transport and control service family: control and application streams over the encrypted link. |
| **K2a** | The gate: a payload holds the controller under enforcing SELinux. |
| **K2b** | The gate: a payload acquires `uid 0` on the target build with a public n-day. |
| **Manifest** | The signed JSON describing a payload's identity, targets, capabilities, and artifacts. |
| **n-day** | A publicly disclosed vulnerability. The project's supply model. |
| **NetworkRelay** | The pre-IDS relay layer over the encrypted link. |
| **NPS / PairedSync** | The sync-state machinery and its preferences domain. |
| **NWSC** | The control stream inside IDS. |
| **Optical pairing** | A pairing path that uses a visual code instead of a typed PIN. |
| **PBBridge** | The setup/activation message family, including initial properties and sync preparation. |
| **Pair record** | The app's durable record of a Watch: identity, state, checkpoints, key references. |
| **Payload** | A signed artifact that grants a capability; usually an n-day chain plus the agent. |
| **Per-boot** | Privilege or state that lasts only until the next reboot. |
| **Preflight** | Checks before a payload runs: target match, battery, storage, no active session. |
| **Protocol drift** | A watchOS change that alters messages or ordering; handled by versioned specs. |
| **Resident agent** | The agent stays alive while a watch is paired, behind a foreground service. |
| **Retry budget** | Three payload attempts per boot, then cooldown. |
| **Revocation** | Removing a payload's permission to run, immediately and everywhere. |
| **SPAKE2+ / PPK** | The password-authenticated key exchange used in the Watch pairing path. |
| **SRP** | Secure Remote Password, a candidate for Apple ID sign-in. Not yet chosen. |
| **Supported device** | A model + kernel build with a passing hardware test on the current release. |
| **T0/T1/T2/T3** | Feature tiers: baseline, core, secondary, best-effort telephony. |
| **Transparency log** | Append-only public record of payload hashes and signatures. |
| **uIKE** | The authenticated key-exchange and control phase of the Watch security handshake. |
| **UMH** | usermode-helper: a kernel mechanism a payload can use to start a privileged process. |
| **Vector** | A golden test: exact input bytes and expected output, derived from a capture. |
| **watchOS** | The Watch's operating system; its version gates protocol support. |
| **Zero-install** | The phone keeps nothing across a reboot; not "the phone stays unprivileged". |
