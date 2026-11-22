# Registry coverage against the Lane A claim inventory

Date: 2026-11-22
Inventory: 253 capability claims in 26 groups
Registry: 266 capability rows after this pass

## Method

The Lane A inventory is claim-grained (G01.pin, G02.…). The product registry is
capability-grained: one row can cover several closely related claims, and adds
Foundation/Operations rows the iPhone does not expose. This report maps by group
and names gaps rather than pretending the two ID spaces are identical.

A group is **represented** when at least one registry row owns its capability.
**Grouped** means the group is represented but its individual claims are not all
split into separate rows. **Gap** means a claim has no owning row and must be
added before the registry can be called exhaustive.

## Coverage

| Group | Claims | Registry representation | State |
| --- | ---: | --- | --- |
| G01 Setup and pairing | 10 | pair.discovery, advertisement-decode, selection, control-security, pin, optical, security-code, bond-persist, activation, setup-*, face-visible, reconnect, unpair, watch-reset, cancel-retry, multi-watch, identity-epoch | Covered |
| G02 Transport | 9 | foundation.ble-discovery/connect, l2cap, smp, link-encryption, networkrelay, ids, ids-session-rules, nwsc | Covered |
| G03 Device state | 11 | foundation.observations/device-profile, settings.battery/storage/device-info, pair.pairedsync | Covered |
| G04 App background behaviour | 6 | apps.background-refresh, settings.background-refresh, lifecycle.first-run | Covered |
| G05 Notifications | 12 | notifications.mirror, ingress, actions, reply, attachments, sounds, per-app, clear-sync | Covered |
| G06 Phone communication | 11 | comm.calls-incoming/outgoing, sms, contacts, voicemail, walkie-talkie, find-phone/watch, emergency-sos, fall-detection, screen-time, schooltime, focus | Covered |
| G07 Apple communication | 5 | secure.imessage, facetime, findmy-network | Grouped |
| G08 Time and settings | 16 | settings.* (33), time.alarms/timers/stopwatch/world-clock/bedside | Covered |
| G09 Watch faces | 11 | faces.* (17) | Covered |
| G10 Everyday data | 8 | time.*, apps.weather/stocks/calendar/reminders/mail/notes/maps | Covered |
| G11 Standalone tools | 9 | apps.compass/noise, time alarms/timers/stopwatch/world-clock | Covered |
| G12 Activity and workouts | 15 | health.activity-rings, workouts-live, workout-history, awards | Grouped |
| G13 Health measurements | 15 | health.heart-rate, resting-hr, hrv, ecg, blood-oxygen, temperature, sleep, respiratory, cycle, medications, mindfulness | Covered |
| G14 Health storage | 10 | health.import/export/anchors/privacy/provenance, foundation.health-mirror | Covered |
| G15 Ultra and navigation | 11 | apps.maps/compass, health.temperature, ultra.gps/waypoints/backtrack/offline-maps/depth/water-temperature/dive-log/action-button/siren | Covered (unimplemented rows) |
| G16 Media | 11 | media.now-playing, remote-control, music-library, podcasts, audiobooks, radio, camera-remote/preview, photos-sync, photo-album-select, home-controls | Covered |
| G17 Camera and remote | 5 | media.camera-remote/preview, comm.find-phone | Covered |
| G18 Apps | 7 | apps.inventory/install/update/remove/layout/watchos/version/store/drm/complications | Covered |
| G19 Siri and intelligence | 8 | apps.siri, siri.dictation/translate/android-actions/network/workout-buddy/message-translation/summaries | Covered |
| G20 Finding devices | 8 | comm.find-phone, comm.find-watch, secure.findmy-network | Covered |
| G21 Security and accessibility | 14 | settings.voiceover, zoom, reduce-motion/transparency, text-size, bold-text, accessibility-other, passcode-status | Covered |
| G22 Apple account and cloud | 11 | secure.apple-id, icloud-sync, health-icloud, account-2fa, continuity-unlock, cloud.cloudkit/home/handoff/subscriptions/regional | Covered |
| G23 Wallet and payments | 8 | secure.wallet, transit, apple-pay, secure-element, wallet.keys, wallet.cash, wallet.card, wallet.bank | Covered (blocked rows) |
| G24 Cellular | 8 | secure.cellular-provisioning, settings.cellular-status/carrier, cellular.plan, number-sharing, dual-sim, calls, data, modem | Covered (blocked rows) |
| G25 Family modes | 5 | family.setup, schooltime, reports, limits, services | Covered (all blocked by B-APPLE-ACCOUNT) |
| G26 Maintenance | 9 | ops.recovery-drill, foundation.recovery/diagnostics/evidence-export, pair.unpair, settings.erase, apps.watchos-update, ops.watch-backup, ops.watch-restore, ops.ota-verify | Covered (backup/restore unimplemented, OTA verify blocked) |

## Result

- 26 of 26 claim groups are represented at least once.
- The Family group was completely absent before the first coverage pass; five
  rows were added as documented Apple-account blockers.
- The wallet, cellular, maintenance, Ultra, Siri and cloud gaps are closed with
  blocked or unimplemented rows. No group-level gap remains.
- 253 claims do not equal 266 rows: the registry adds Foundation/Operations
  rows and groups some claims. Granularity is deliberate, not one-to-one proof.

This report discharges the **group-level** coverage check but not the
exhaustive-registry gate: each remaining row must still be verified or blocked,
and 173 rows are unimplemented. No row is promoted by this audit.
