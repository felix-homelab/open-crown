# 11 — Glossary

| Term | Meaning |
| --- | --- |
| **Activation** | The Apple service step that binds a Watch to an Apple ID and makes it usable. Relayed by Open Crown, never forged. |
| **Agent** | The small root process that owns the Bluetooth controller for one session. |
| **Bond** | The long-term Bluetooth security relationship between phone and Watch. Must survive reboots. |
| **Borrowed root** | uid 0 obtained by an exploit for one session and released at reboot. Not an installation. |
| **Bridge** | A desktop host that runs the protocol when the phone has no payload. A fallback, not the primary architecture. |
| **Capability** | A permission the agent grants to the app for a session: `root`, `hci`, `hal`, `diag`. |
| **Catalog** | The signed, versioned collection of payloads the app can run. |
| **Checkpoint** | A durable, ordered marker of session progress. Resuming never replays a non-idempotent step. |
| **Clean room** | Re-implementing from specifications and captures, without copying source or assets. Here: ideas allowed, expression not. |
| **Crown** | The project's name for the companion/session layer. |
| **Discovery** | Finding and identifying a Watch via BLE advertising. Needs no privilege. |
| **ERTM** | Enhanced Retransmission Mode, a Classic L2CAP mode the Watch's data channels use. |
| **Exploit payload** | A signed, device- and kernel-matched chain that yields uid 0 for a session. |
| **Family** | A reusable exploit technique (page-cache write, futex/UAF, …) with per-device calibration. |
| **HCI** | Host Controller Interface: the command/event boundary between the OS and the Bluetooth controller. |
| **HAL** | Android's Bluetooth Hardware Abstraction Layer; owning it is one way to control the radio. |
| **IDS** | Apple's identity/control service family carried over the paired link. |
| **L2CAP** | Logical Link Control and Adaptation Protocol; the channel layer the Watch's protocols ride on. |
| **Manifest** | The signed JSON describing a payload's identity, targets, capabilities, and artifacts. |
| **Optical pairing** | A pairing path that uses a visual code instead of a typed PIN. |
| **Pair record** | The app's durable record of a Watch: identity, state, checkpoints, key references. |
| **Payload** | Any signed artifact the app runs to gain a capability; usually an exploit plus the agent. |
| **Per-boot** | Privilege or state that lasts only until the next reboot. |
| **Preflight** | Checks performed before a payload runs: target match, battery, storage, no active session. |
| **Protocol drift** | A watchOS change that alters messages or ordering; handled by versioned specs. |
| **Retry budget** | The cap on exploit attempts per boot, to prevent loops. |
| **Revocation** | Removing a payload's permission to run, immediately and everywhere. |
| **Service** | A request/response area over the control channel (faces, notifications, health, …). |
| **Session** | One borrowed-root, agent-backed period of activity, from bootstrap to disposal. |
| **Setup** | The post-activation sequence that ends with a visible watch face. |
| **SRP** | Secure Remote Password, the password-authenticated key exchange used for Apple ID sign-in. |
| **T0/T1/T2/T3** | Feature tiers: baseline, core features, secondary features, best-effort telephony. |
| **Transparency log** | Append-only public record of payload hashes and signatures. |
| **UMH** | usermode-helper: the kernel mechanism an exploit uses to start a root process. |
| **Vector** | A golden test: exact input bytes and expected output, derived from a capture. |
| **watchOS** | The Watch's operating system; its version gates protocol support. |
| **Zero-install** | The phone stays stock; nothing is installed to the system partition. |
