# Tools

Build-time and research tooling. They are not part of the shipped APK.

| Tool | Purpose | Phase | Status |
| --- | --- | --- | --- |
| [capture-import](capture-import/) | Normalise HCI snoop and pcap into fixtures | 1 | implemented |
| [fixture-diff](fixture-diff/) | Diff two captures, highlight changed messages | 1 | implemented |
| [spec-lint](spec-lint/) | Require spec sections and at least one vector | 1 | implemented |
| [vector-gen](vector-gen/) | Capture to golden vector with redaction | 1 | implemented |
| [hci-echo](hci-echo/) | Phase 0 harness: own the controller, echo bytes | 0 | not started |

## Layout

    tools/
    ├── lib/opencrown_tools/   shared modules, no third-party dependencies
    ├── tests/                 unittest suite for the modules
    ├── capture-import/cli.py
    ├── fixture-diff/cli.py
    ├── spec-lint/cli.py
    ├── vector-gen/cli.py
    └── hci-echo/              hardware-only; README describes the interface

## Running

    python3 tools/capture-import/cli.py capture.btsnoop --out fixture.json \
        --name demo --phone-build CPH2653 --watch-model Watch7,5 --watchos 26.2
    python3 tools/fixture-diff/cli.py a.json b.json
    python3 tools/spec-lint/cli.py protocol/
    python3 tools/vector-gen/cli.py fixture.json --name pairing/hello --out vector.json

Tests:

    PYTHONPATH=tools/lib python3 -m unittest discover -s tools/tests -v

Only catalog/tooling/verify.py optionally uses a third-party package
(cryptography) for Ed25519 signature checks. Everything else is stdlib.
