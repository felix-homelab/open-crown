# A52 file-broker probe: init cannot read app data

Date: 2026-11-22 15:06 Europe/Berlin
Device: Samsung SM-A528B (a52sxq), Android 14 / SDK 34, SELinux enforcing.
Relay: in-tree page-cache relay, live on boot 73.
Serial: withheld.

## Question

The app cannot connect to the init-domain relay socket. Before declaring the
broker dead, test a different direction: can the init-domain relay read a file
inside the app's private data directory? If yes, a file queue could be a broker
without an SELinux change.

## Setup

A 0644 test file owned by the app uid was created through run-as:

~~~text
$ adb shell "run-as org.opencrown.app sh -c 'echo CROWN_FILE_BROKER_TEST > files/broker-test.txt; chmod 644 files/broker-test.txt'"
CROWN_FILE_BROKER_TEST
~~~

The relay was armed through the app's consent-gated Diagnostics action and
triggered with the documented property write. The socket was live.

## Result

The shell-domain probe connects to the relay, then asks the relay (running as
init) to openat the app file read-only:

~~~text
mode=readfile sock=@dfi_init
selinux_context=u:r:shell:s0
connect(@dfi_init): OK
peer_selinux_context=u:r:init:s0
openat: raw=-13 frame_marker=0xd1
openat: errno=13(Permission denied)
~~~

## Conclusion

- The denial is at openat, not at DAC: the file is 0644 and owned by the app
  uid, and the relay runs as root. SELinux denies init access to app_data_file.
- A file queue in the app's data directory is therefore not a broker.
- Combined with the denied socket direction, there is **no app-domain channel**
  to this relay: not a socket, not an app-data file.
- This does not test a shared path owned by another domain; no such path exists
  that the app can write without a policy or ownership change, and speculative
  paths would be policy circumvention, not a product design.

## Recovery

The relay was cleared with the research-only reboot RPC and the phone was
verified to come back with no relay and no notification-listener access.
