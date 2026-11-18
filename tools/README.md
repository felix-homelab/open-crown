# Tools

Build-time and research tooling. None of it is implemented yet; each directory
has its own README with the intended interface.

| Tool | Purpose | Phase |
| --- | --- | --- |
| [capture-import](capture-import/) | Normalise HCI snoop and pcap into fixtures | 1 |
| [fixture-diff](fixture-diff/) | Diff two captures, highlight changed messages | 1 |
| [spec-lint](spec-lint/) | Require spec sections and at least one vector | 1 |
| [vector-gen](vector-gen/) | Capture to golden vector with redaction | 1 |
| [hci-echo](hci-echo/) | Phase 0 harness: own the controller, echo bytes | 0 |

Tools are not part of the shipped APK. They may be written in Rust, Python, or
shell; prefer the smallest thing that does the job.
