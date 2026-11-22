# Page-cache Relay native library

`pagecache_relay.c` resolves `_ZN7android4base10LogMessageD2Ev` in the target
`libbase.so` at runtime, writes the 576-byte relay into the file-backed page
cache, and verifies the write. It contains no A52 file offset.

Build is driven by `app/src/main/cpp/CMakeLists.txt`:

```sh
./gradlew :app:assembleDebug
```

The library does not decide whether a device is supported. The payload
manifest in `payloads/org.opencrown.payload.pagecache-relay/manifest.json` and
the Kotlin matcher do that before the native call is made.
