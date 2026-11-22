# Privacy policy

Status: current for the private research build (versionName 0.1.0). This file is
normative for the app's data handling. It is not a claim that the app is
distribution-ready.

## 1. Summary

Open Crown is local-first. There is no account system, no analytics, no
advertising SDK, and no server operated by the project. Nothing is uploaded by
the app in this build.

## 2. Data inventory

| Data | Where it lives | Retention | Leaves the phone? |
| --- | --- | --- | --- |
| Onboarding state | App-private SharedPreferences | Until app data is cleared | No |
| Pairing metadata | App-private store sealed with Android Keystore (AES-GCM) | Until explicit deletion or app data clear | No |
| Pairing keys | Not implemented yet. When added, sealed in Keystore and never logged | N/A | No |
| Notification content | In memory only, capped at 40 entries, title 120 chars / text 400 chars | Until process death or Clear | No |
| Diagnostics | Generated on screen from live probes; not persisted | Per screen session | No, unless the user taps Export |
| Diagnostics export | Redacted text file in the app cache | Until cache is cleared or evicted | Only to the share target the user chooses |
| Health data | No sample store exists in this build | N/A | No |
| Apple account material | Never requested or stored | N/A | No |
| Device serial | Never read into the app | N/A | No |
| Analytics / crash reports | None | N/A | No |

## 3. Permissions

| Permission | Why | If denied |
| --- | --- | --- |
| Nearby devices (BLUETOOTH_SCAN, BLUETOOTH_CONNECT) | Observe Apple advertisements; no location requested | Discovery shows the permission state; the rest of the app remains usable |
| Notification listener access (system grant, not a runtime permission) | Read notification content for local capture | Notifications screen explains the grant; nothing is captured |
| Internet | The in-tree page-cache chain uses an IPsec/UDP socket; it sends no application traffic | Chain cannot run; no other feature depends on it |
| Camera | Not requested in this build | Optical pairing is blocked anyway |

## 4. What the app deliberately does not do

- No Apple ID, iCloud, Find My, iMessage, FaceTime or Siri credential entry.
- No health sample collection, inference or upload.
- No notification content in logs, crashes or diagnostics.
- No advertising identifiers, analytics identifiers or device serials in the app or the exported report.
- No Activation Lock bypass, stolen-device support, or operation of a watch the
  user cannot activate with their own account.
- No silent background collection: the notification listener must be granted in
  Android settings, and capture stops when the process ends.

## 5. User controls

- Revoke notification access in Android Settings → Notifications → Device &
  app notifications, or from the app's Privacy screen.
- Revoke Nearby devices in Android app settings.
- Clear captured notifications from the Notifications screen; content is
  memory-only.
- Mute individual apps on the Notifications screen; muting removes that app's
  captured rows and suppresses new ones.
- Clear all app data or uninstall to remove onboarding and sealed records.
- There is no selective pair-record deletion yet because unpair is not
  implemented.
- Diagnostics can be exported from the Diagnostics screen. The report is
  redacted and only leaves the app through a share target the user picks.

## 6. Security contact

See [SECURITY.md](../SECURITY.md). Do not include pairing keys, device serials,
Apple account material or raw notification content in a report.

## 7. Changes

This policy will change when pairing, health import or the export format changes.
Those changes must update this file and the in-app Privacy screen together.
