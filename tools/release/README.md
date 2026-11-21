# Release tooling

| Tool | Purpose |
| --- | --- |
| apk-report.py | SHA-256, size and Gradle metadata for an APK |

    python3 tools/release/apk-report.py app/build/outputs/apk/debug/app-debug.apk --out apk-report.json

The report is what a release note links to: the hash a user can check against
the file they downloaded. It deliberately does not sign anything; signing is a
ceremony with an offline key (ADR 0012).
