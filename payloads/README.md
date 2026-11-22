# Payloads

Named payload packages, each with its own manifest and specification limits.

- [org.opencrown.payload.pagecache-relay](org.opencrown.payload.pagecache-relay/)
  — page-cache init relay; verified on SM-A528B, candidate for Samsung 5.4 qgki.

Adding support means adding device/kernel rows to the manifest and verifying
the target spec on hardware. The app auto-runs only a device row marked
`verified`; a specification-only match is shown as compatible and untested.
