# Reference feature inventory — every capability the reference claims

**Status:** audit of the quarantined copy, read-only.
**Scope:** all capability claims in `README.md` and the reference's own feature registry / coverage plan, plus the claim-bearing prose documents under `docs/`.
**Method:** each claim is recorded with the reference's own status grade, the physical proof that would settle it, and the reference's admission where it makes one. Capability text is a paraphrase in Open Crown vocabulary, not a transcription. The registry is a queue, not a compatibility claim: its own preamble says it is "a queue of concrete functions, not a statement of 100% compatibility", and that status R/I do not count as working functions.

## 1. Evidence pointers used in this file

| Pointer | Meaning |
|---|---|
| README §… | Section of the reference `README.md` (English half; line numbers given where useful) |
| REG | The reference feature registry (`docs/WATCH_FEATURE_REGISTRY.md`), group and row ID |
| PLAN | The reference coverage plan (`docs/WATCH_FEATURE_COVERAGE_PLAN.md`) |
| RESULT / CHAIN / 388 / 90 / 51 / 39 | Named prose reports under `docs/`, cited by name only |

**Status grades used by the reference (translated):** `R` = research only; `I` = implemented or partially checked; `H` = narrow hardware acceptance for that exact row; `E` = confirmed external dependency. An `H` row does not close neighbouring rows or the group.

Each group below names its **Pointer** (the README section/line, registry group and plan stage it is drawn from) and the **admission IDs** from §2 that govern it. Where a group says "none specific", the README makes no completion claim for that group — which is not the same as claiming completeness. The registry's per-row "acceptance/remaining check" text is the source of the proof column; it is a requirement, not evidence that the requirement was met.

## 2. Admissions of non-parity, by location

These are the reference's own statements that its coverage is incomplete. Each is paraphrased and pinned to its location; the wording is ours, the admission is theirs. They matter because each one is a claim we must not repeat as fact.

| # | Location | Admission (paraphrased) |
|---|---|---|
| 1 | README line 33 | The banner limits testing to watchOS 26.2, build 23S303. |
| 2 | README line 57 | The document is a dated snapshot; source versions are not installed versions. |
| 3 | README line 59 | Development continues; Watch.app parity, full iPhone feature parity and all-model compatibility are incomplete; the macOS pairing → activation → IDS path is unfinished. |
| 4 | README line 93 | Release is an experimental RC; application services are Android-only unless stated; desktop exposes connection/setup controls only. |
| 5 | README line 98 | Activation, the UI reaching its end state, and the pair being ready for operational use are three different gates; native evidence and the owner's confirmation are tracked apart. |
| 6 | README line 102 | Face edits require read-back; family and preview coverage are described as evolving. |
| 7 | README line 103 | Visual parity and optimisation remain open; the swatch work is research, not integrated production UI. |
| 8 | README line 105 | Notification delivery exists, but every app, attachment, sound and action needs its own acceptance. |
| 9 | README line 107 | Health work does not establish complete import, correct anchors or all data types. |
| 10 | README line 108 | Calls/SMS/contacts are research codecs; two call methods are unimplemented; call audio is unverified. |
| 11 | README line 109 | Replicator snapshot/file reception and durable persistence are unfinished. |
| 12 | README line 110 | macOS physical pairing/IDS waits on a bootstrap transport the project does not have. |
| 13 | README line 111 | Linux Wi-Fi credentials and application services are incomplete. |
| 14 | README line 112 | Windows was verified on one controller; other hardware and most services are unverified. |
| 15 | README line 351 | A decoded image is not an authenticated pair. |
| 16 | README line 355 | The camera reader is a transitional local firmware backend; an independent portable detector is unfinished; non-arm64 ABIs refuse to start. |
| 17 | README line 405 | An activation notification, response 18, an app ACK and sent progress do not by themselves establish a visible clock. |
| 18 | README line 462 | Reconnect backoff is 15/30/60 s; an IDS policy refusal can forbid reconnect outright. |
| 19 | README line 590 | The paired-sync route must be Class-C; inheriting Class-D is called incorrect. |
| 20 | README line 650 | A delta transaction can end UNKNOWN; read-back times out at 180 s. |
| 21 | README line 675 | Swatch validation is offline research; production option UI integration remains open. |
| 22 | README line 732 | A test bulletin does not establish per-app, icon, attachment, Focus or haptic behaviour. |
| 23 | README line 744 | A Wi-Fi archive ACK proves receipt, not that the Watch joined the network. |
| 24 | README line 759 | Ciphertext delivery and decoding are not native acceptance or an anchor commit. |
| 25 | README line 782 | Replicator file persistence/ACK is unimplemented; snapshot delivery is unconfirmed. |
| 26 | README line 1586 | The README declines to grant a blanket licence for third-party material. |
| 27 | CHANGELOG "Unreleased" | macOS pairing/IDS, desktop services, Health and face parity are incomplete; signing, CI, package audits and hardware acceptance remain required. |
| 28 | `docs/BUILD_AND_RELEASE.md` "Remaining release gates" | Permission to ship firmware-derived assets is unresolved; a clean Companion build is blocked by an absent firmware input. |
| 29 | `docs/SETUP_COMPLETION_388.md` | The delivered hardware run relied on owner-confirmed completion and does not claim strict native clock/reconnect evidence. |
| 30 | `docs/live-20261006-optical-pairing/RESULT.md` "Product code and remaining work" | Lists still-outstanding work: PSK modules wired to the transport, replacement backup, production scan→pair→activation on hardware, and an independent spatial port. The document is chronologically layered, so this list precedes a later live-run summary that claims a completed camera pair. |

## 3. Claimed parity by group

Registry groups are the reference's own decomposition. Each group header carries the native services it names and the README admission that governs the group. Proof is what a third party would need; "owner report" never counts as protocol evidence.

### G01 — Setup and pairing (stage 1)

**Pointer:** README §Fresh pairing, step by step (lines 296–365) and §Activation and Setup completion (368–433); REG G01; PLAN stage 1.

Native services: registry, IDS, PBBridge, preference sync; bidirectional. README admission: #5, #16, #17.

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G01.pin | Pair a fresh Watch with a six-digit code, right and wrong | I | Reproducible fresh pair in an approved test; wrong-PIN rejection |
| G01.activation | Activate the current pair | H | Owner-confirmed activation; a repeatable full chain separately |
| G01.setup | Complete setup up to a visible face | I | Automate the successful chain, not on the current pair |
| G01.wifi_credentials | Send Wi-Fi credentials | I | All suites, saved networks, change hooks, early-setup hook, UI |
| G01.wifi_current_wpa2 | Join one WPA2-PSK/CCMP network without typing a password | H | User-verified network on the Watch; other modes separately |
| G01.persist | Keep an activated pair across stop/start | H | Same pair identity and committed data after restart |
| G01.reconnect | Reconnect durably | I | 20 cycles and 24 h across offline/process/phone states |
| G01.multiple | Hold and select several pairs | R | Switch two approved pairs without data crossing |
| G01.transfer | Move a pair to a new host | R | New host reads the same data and preserves identity |
| G01.restore | Restore a pair | R | Restore an agreed secret/state set in an approved test |

### G02 — Transport (stages 1–2)

**Pointer:** README §Protocol stack (260–293) and §Operational reconnection (436–464); REG G02; PLAN stages 1–2.

Native services: controller interface, ERTM, relay, IDS; bidirectional. README admission: #11 (replicator), #19 (class routing).

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G02.bluetooth | Controller ownership, ACL, relay | I | Bounded queues, ACL credits, loss/MTU, long background run |
| G02.ids | IDS Class-C/D and protection classes | I | Every required service; lock/unlock; wrong-class refusal |
| G02.wifi | A Wi-Fi path for Watch traffic | R | Real IDS/data exchange while Bluetooth is away |
| G02.internet | Watch internet through the phone | R | Real Watch fetch through the phone's network |
| G02.dns | DNS for the Watch | R | Native resolve and response delivery, errors, timeouts |
| G02.network_change | Survive a phone network change | R | Wi-Fi↔cellular without losing pair or transfer |
| G02.queue | Queues, correlation, deadlines | I | UUID/epoch/deadline; retry, late ACK/response |
| G02.resources | Large resource transfer | R | Hash match, bounded multipart, realistic payload sizes |
| G02.resume | Resume transfer | R | Interrupt mid-resource, resume without duplication or loss |

### G03 — Device state (stage 2)

**Pointer:** README line 100 (Battery / About row); REG G03; PLAN stage 2.

Native services: system settings About request/response, relay device info. README admission: #9-adjacent (line 100: "absent values remain unknown").

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G03.battery | Read charge percentage | H | Native value to app to UI; visual UI separately |
| G03.charging | Read charging flag | H | Native false, then a true/false transition |
| G03.battery_health | Battery health/capacity | R | Native capacity/health; absent field must stay absent |
| G03.model | Model identity | I | Device-info chain through the full UI/readback |
| G03.version | OS build string | I | Build observed from device to UI |
| G03.storage | Free storage | H | Native About value to Companion; change and visual separately |
| G03.apps_count | Count of user apps | H | Native About count with no invented installed list |
| G03.songs_count | Count of songs | H | Native About count; media transfer separately |
| G03.photos_count | Count of photos | H | Native About count; photo transfer separately |
| G03.connection | Link state in the UI | I | Real subscription; live/stale/disconnect states |
| G03.errors | Diagnostics export | I | Bounded export, correlated list/archive, full live sysdiagnose |

### G04 — App background behaviour (stage 2)

**Pointer:** README §Keys, storage, and ownership (785–805); REG G04; PLAN stage 2.

README admission: none specific; platform claims are Android-only.

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G04.fgs | Foreground service holds the session | I | Keyguard, screen off, 24 h background with no Activity |
| G04.status | Status notification | I | Correct running/connected/error and stop action |
| G04.process_death | Survive process death | I | Restore without replaying setup or a side effect |
| G04.boot | Restore after phone reboot | R | Same pair restored after reboot |
| G04.power | Power budget | R | Measure wakelocks/battery/idle over 24 h |
| G04.restrictions | Explain Android restrictions | I | Permission denial/background limits explained; link recovers |

### G05 — Notifications (stage 3)

**Pointer:** README §Notifications (718–732); REG G05; PLAN stage 3.

Native service: bulletin distribution (Class-C) and bulletin settings (Class-D). README admission: #8, #22.

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G05.delivery | Mirror all-app notifications | I | Two different apps, several chats, real cards on Watch |
| G05.group | Group/summary handling | R | Grouping matches Android |
| G05.update | Update a card | I | Revision changes; old action does not apply |
| G05.remove | Dismiss/remove both ways | I | Exact publisher identifier both ways; real Watch tap |
| G05.icons | App icons | R | Native resource shown; missing icon not substituted |
| G05.attachments | Attachments | R | Photo/resource, hash, size, upload error |
| G05.per_app | Per-app notification settings | I | Native settings readback and filter effect |
| G05.sound | Sound policy | R | Actual sound and silent policy |
| G05.haptics | Haptic policy | R | Actual haptic and silent policy |
| G05.focus | Focus modes | R | Modes, exceptions, two-way changes |
| G05.actions | Actions | I | Real Watch tap reaches the same Android revision; restart/dedupe |
| G05.reply | Quick replies | I | Real Watch text reaches the right chat's input, not a neighbour |

### G06 — Phone communication (stages 3–4)

**Pointer:** README line 108 and §Flutter methods without a current Android implementation (552–561); REG G06; PLAN stages 3–4.

Native service: telephony (Class-C) named in the route table. README admission: #10.

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G06.contacts | Contacts sync | R | Name/number/photo; add/edit/delete/conflict |
| G06.sms | SMS send/receive | R | Real send, receive, status, history |
| G06.history | Message/call history | R | IDs and timestamps; deletion; repeat without duplicates |
| G06.reply | Reply to a message | R | Watch compose/reply reaches the correct recipient |
| G06.incoming_call | Incoming call | R | Real Android call shows name/number/state |
| G06.outgoing_call | Outgoing call | R | Watch dial starts the correct Android call |
| G06.accept | Accept call | R | Real Watch action makes the Android call active |
| G06.reject | Decline call | R | Real Watch action stops ringing |
| G06.end | End call | R | Real Watch action ends the active call |
| G06.audio | Call audio path | R | Real audio/mic both directions, route and disconnect |
| G06.messengers | Third-party messenger actions | I | Supported actions/replies with a real Watch result per app |

### G07 — Apple communication services (stage 9)

**Pointer:** README line 59; REG G07; PLAN stage 9.

README admission: #3 (every iPhone feature incomplete).

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G07.imessage | iMessage | R | Authorised real send/receive/history |
| G07.facetime | FaceTime audio | R | Real call/audio/signalling |
| G07.walkie | Walkie-Talkie | R | Invitation, availability, real audio peer |
| G07.checkin | Check In | R | Session/status/delivery in a safe scenario |
| G07.namedrop | NameDrop | R | Real contact exchange over a confirmed native path |

### G08 — Time and settings (stages 2, 5)

**Pointer:** README §Watch settings (680–688); REG G08; PLAN stages 2 and 5.

README admission: #4 (line 104: only three settings keys establish native writes — wrist, invert, 24-hour).

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G08.time | Time exchange | I | Native exchange exists; correct time/change on Watch |
| G08.zone | Time zone | R | Real change, native readback, Watch clock |
| G08.language | Watch language | I | Native domain/type/consumer/readback and real UI |
| G08.region | Region | I | Two-way native readback and regional effect |
| G08.units | Units | I | Native value and change in the target app |
| G08.orientation | Wrist/crown orientation | I | Crown/wrist readback and real UI |
| G08.brightness | Brightness | R | Native setting/readback/visible effect |
| G08.aod | Always On | R | Screen/mode/readback and power state |
| G08.text | Text size/style | R | Native readback and visible text |
| G08.sound | System sound | R | Native volume/silent and real sound |
| G08.haptics | System haptics | R | Native setting and physical effect |
| G08.gestures | Gestures | R | Setting and real action |
| G08.action | Action button | R | Native mapping/readback and the button |
| G08.layout | App layout | R | Native layout and real launcher |
| G08.airplane | Airplane mode | R | Readback, disconnection, return without losing the pair |
| G08.low_power | Low power mode | R | Readback, effect, reconnect |

### G09 — Watch faces (stage 5)

**Pointer:** README §Native faces (593–676); REG G09; PLAN stage 5.

Native service: clock-face sync (Class-D) and native face collection. README admission: #6, #7, #20, #21.

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G09.collection | Read the real face collection | I | Historically a full 209/END was confirmed; current read 135/no-END is a regression |
| G09.refresh | Refresh the collection | I | Fresh full END; original timestamp not substituted |
| G09.select | Select the active face | I | Native delta to a new complete selection; stale guard |
| G09.add | Add/duplicate a face | I | Real duplicate produces new UUID/order/config and END readback |
| G09.update | Edit a face | I | Encoder/plan exist; IPC/UI and real config/readback missing |
| G09.remove | Delete a face | I | Encoder/plan exist; IPC/UI and real absence/readback missing |
| G09.order | Reorder faces | I | Encoder/plan exist; IPC/UI and real permutation/readback missing |
| G09.photo | Photo face | R | Resources/hash/real render; replaced and deleted photo |
| G09.complications | Complications | I | Descriptors read; real provider/resource/update on Watch missing |
| G09.share | Share faces | R | Native export/import with dependent bundles/resources |
| G09.stack | Smart Stack | R | Order/data/widgets/live updates/readback |

### G10 — Everyday data (stage 5)

**Pointer:** REG G10; PLAN stage 5. The README has no dedicated section.

README admission: none specific; all rows research.

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G10.calendar | Calendar | R | Event/recurrence/time zone/edit/delete both ways |
| G10.reminders | Reminders | R | Create/complete/edit/delete/recurrence/conflict |
| G10.notes | Notes | R | Text/edit/delete/search/conflict |
| G10.mail | Mail | R | Authorised inbox/body/actions/read/delete |
| G10.weather | Weather | R | Location/time/unit/native provider and refresh |
| G10.stocks | Stocks | R | Symbol/time/quotes/native display |
| G10.tides | Tides | R | Coordinates/time/units/native display |
| G10.alarms | Alarms and schedules | R | Create/enable/recurrence/sound/delete/readback |

### G11 — Standalone tools (stage 5)

**Pointer:** REG G11; PLAN stage 5. The README has no dedicated section.

README admission: none specific; all rows research.

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G11.alarm | Alarm | R | Standalone trigger/snooze/stop plus G10.alarms |
| G11.timer | Timer | R | Start/pause/stop/multiple and signal |
| G11.stopwatch | Stopwatch | R | Start/lap/stop/reset, standalone result |
| G11.world_clock | World clock | R | Cities/time zones/DST and native control |
| G11.calculator | Calculator | R | Real input/computation |
| G11.flashlight | Flashlight | R | Real screen/modes and native control |
| G11.tips | Tips | R | Launch/content/network |
| G11.memoji | Memoji | R | Create/edit/display/send where supported |
| G11.input | Text input | R | Keyboard/scribble/dictation into a real recipient |

### G12 — Activity and workouts (stage 6)

**Pointer:** README line 107; REG G12; PLAN stage 6.

README admission: #9 (Health/Activity import not complete).

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G12.steps | Steps | R | Real samples/UUID/time/units and a deduplicated sum |
| G12.distance | Distance | R | Real quantity/unit/source and aggregation |
| G12.energy | Energy | R | Active/basal quantities with original time/units |
| G12.rings | Rings/goals | R | Real values and native goal changes |
| G12.load | Training load | R | Native inputs/result/history |
| G12.types | All applicable workout types | R | One leaf scenario per available type, not one enum test |
| G12.plans | Workout plans | R | Create/sync/edit/start, real plan |
| G12.intervals | Intervals | R | Stages/repeats/signals and a recorded result |
| G12.zones | Heart-rate zones | R | Native zones/change/live/history |
| G12.running | Running metrics | R | All available quantities and session linkage |
| G12.cycling | Cycling metrics | R | All quantities/sensors/session linkage |
| G12.swimming | Swimming metrics | R | Length/stroke/distance/time/unit/session linkage |
| G12.routes | GPS routes | R | Coordinates/timestamps/session, gaps, offline |
| G12.sensors | External sensors | R | Real pairing/live samples/units/source |
| G12.gymkit | GymKit | R | Authorised real equipment sync/history |

### G13 — Health measurements (stage 6)

**Pointer:** README §Health (746–759); REG G13; PLAN stage 6.

README admission: #9.

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G13.heart | Heart rate live/history | R | Real samples, source/time/units; not a single message ID |
| G13.ecg | ECG | R | Real waveform/classification/metadata/export |
| G13.oxygen | Blood oxygen | R | Variant applicability, real readings/history |
| G13.temperature | Temperature | R | Native quantity/baseline/unit/time |
| G13.sleep | Sleep | R | Stages/intervals/source/time zones |
| G13.sleep_score | Sleep score | R | Native result/date/history |
| G13.apnea | Sleep apnoea | R | Applicability/authorisation/native results |
| G13.vitals | Vitals | R | Native series/ranges/history |
| G13.cycle | Cycle tracking | R | Samples/edit/deletion/private sync |
| G13.medications | Medications | R | Schedule/log/reminders/edit/deletion |
| G13.noise | Noise | R | Real samples/units/history/alerts |
| G13.mindfulness | Mindfulness | R | Session/log/metadata and sync |
| G13.daylight | Daylight | R | Real quantities/time/source |
| G13.handwashing | Handwashing | R | Real events/duration/history |
| G13.alerts | Health alerts | R | Each applicable alert as a separate safe native scenario |

### G14 — Health storage (stage 6)

**Pointer:** README §Health (746–759); REG G14; PLAN stage 6.

README admission: #9. These rows carry the reference's most specific numbers; they are reported, not independently verified here.

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G14.auth | Health authorisation/identity | I | Signed/decrypted restore finished v2, version 17, sequence 1 matched; durable identity and reconnect accepted; sample grants still open |
| G14.anchors | Anchors/change sets | I | Manager preflight, then engine sequence/range/dependency/speculative/version policy; 3 received/5 validated; 44 retained events/72 historical changes held stale; native data + anchors, partial failure, reply, resume open |
| G14.uuid | Object UUIDs/sources | I | Seven unique sample UUID observations scoped to an authenticated peer; original source/provenance encrypted; cross-reference/type/clinical import unaccepted |
| G14.units | Units/time | I | Exact HealthKit catalogue/build/code/class gates; one phone query matched 6 quantities and 1 category; NSDate-reference seconds retained; conversion/display semantics unaccepted |
| G14.corrections | Corrections/deletions | R | Tombstones/updates/versioning/conflicts both ways |
| G14.crypto | Encrypted health data | I | Local AES-GCM/AAD/HMAC database with separate keystore keys; narrow replay/query/restart; logs counts only; native grants/locked/offline unaccepted |
| G14.dedupe | Deduplication | I | Three sequences, repeated replay, upgrade, force-stop give the same 13 encrypted observations; clinical correction/deletion/anchor dedupe unaccepted |
| G14.database | App-side database | I | Bounded read-only query authenticates cells/AAD/variant and object HMAC; 13 records after force-stop; clinical query/native atomic commit unaccepted |
| G14.export | Export | R | Export count/UUID/units/hash match the database |
| G14.health_connect | Health Connect | R | Each supported mapping/permission/dedupe/deletion |

### G15 — Ultra and navigation (stages 5–6)

**Pointer:** REG G15; PLAN stages 5–6. The README has no dedicated section.

README admission: #3 (model compatibility).

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G15.gps | GPS | R | Real fix/precision/time/offline records |
| G15.compass | Compass | R | Heading/calibration/units/real sensors |
| G15.waypoints | Waypoints | R | Create/edit/delete/readback/navigation |
| G15.backtrack | Backtrack | R | Record/return/route/offline |
| G15.maps | Maps/routes | R | Native maps/route/turn cues/delivery |
| G15.offline | Offline maps | R | Download/hash/use/delete without network |
| G15.depth | Depth | R | Applicable safe sensor log |
| G15.water_temperature | Water temperature | R | Real sensor quantity/unit/time |
| G15.dives | Dive log | R | Original records/time/depth/units/history |
| G15.action | Action button mappings | R | Native settings/readback and the real button |
| G15.siren | Siren | R | Setting/control and an approved real effect |

### G16 — Media (stage 7)

**Pointer:** REG G16; PLAN stage 7. The README has no dedicated section.

README admission: none specific; all rows research.

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G16.nowplaying | Now Playing from Android | R | Correct active player/metadata/artwork |
| G16.playpause | Play/pause | R | Real Watch action changes the selected media session |
| G16.seek | Seek | R | Native position/duration and real playback |
| G16.volume | Volume | R | Real route volume/readback |
| G16.music | Music | R | Transfer/hash/play/delete; DRM separately |
| G16.podcasts | Podcasts | R | Episodes/files/play/resume/delete |
| G16.audiobooks | Audiobooks | R | Chapters/files/play/bookmark/delete |
| G16.photos | Photos | R | Transfer/hash/native album/display/delete |
| G16.voice_memos | Voice memos | R | Real Watch recording to the right phone file/hash |
| G16.recognition | Music recognition | R | Real capture/result and service dependency |
| G16.bluetooth_audio | Bluetooth audio devices | R | Real pairing/route/playback/reconnect |

### G17 — Camera and remote control (stages 7, 9)

**Pointer:** REG G17; PLAN stages 7 and 9. The README has no dedicated section.

README admission: #3.

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G17.preview | Camera remote preview | R | Real Android camera frames/render/latency |
| G17.shutter | Camera shutter | R | Real Watch tap saves a real photo |
| G17.remote | Remote control | R | Authorised command to the target device/readback |
| G17.shortcuts | Shortcuts | R | Native request to an available Android action and result |
| G17.home | Smart home/other devices | R | Each adapter/action/authorisation/readback separately |

### G18 — Apps (stages 8–9)

**Pointer:** REG G18; PLAN stages 8–9. The README has no dedicated section.

README admission: #3.

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G18.catalog | Installed-app catalogue | R | Real bundle/version/storage, not the About count |
| G18.storage | App storage | R | Native size/readback/delete effect |
| G18.install | Install | R | Authorised signed app installed and launched |
| G18.update | Update | R | Native version changes; data and pair preserved |
| G18.remove | Remove | R | Real absence and dependent data handled |
| G18.connectivity | Companion data channels | R | Message/file/userInfo/context/reachability/replay |
| G18.store | Native App Store | R | Authorised catalogue/download/install/account |

### G19 — Siri and intelligence (stages 7, 9)

**Pointer:** REG G19; PLAN stages 7 and 9. The README has no dedicated section.

README admission: #3; rows marked E require a compatible iPhone/Intelligence, not Android.

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G19.siri | Standalone Siri commands | R | Each native intent and real result |
| G19.dictation | Dictation | R | Real text/language/recipient |
| G19.translate | Translate | R | Native language/input/output/offline support |
| G19.network | Siri over network | R | Real request/response/account/error |
| G19.android | Siri to Android actions | R | Native authorised intent to the exact action/result |
| G19.workout_buddy | Native Workout Buddy | E | Compatible iPhone/Intelligence requirements; Android path separate |
| G19.message_translation | Native message translation | E | Requirements from the plan; no real authorised message result |
| G19.summaries | Native summaries | E | Requirements from the plan; no real notification summary |

### G20 — Finding devices (stages 2, 9)

**Pointer:** README §Find Watch / Find Phone (734–738); REG G20; PLAN stages 2 and 9.

README admission: #4 (line 106: receipts and physical effects are distinct).

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G20.watch_ping | Find Watch | I | Native didPlay=true; exact correlation and audible sound |
| G20.phone_ping | Find Phone from Watch | I | Local speaker/flash/replay verified; real Watch tap missing |
| G20.stop | Stop the phone signal | I | Real Companion stop verified; Watch-originated session missing |
| G20.people | Find My people | R | Authorised location/share/readback |
| G20.devices | Find My devices | R | Authorised real device location/action |
| G20.airtag | AirTag | R | Authorised real tag detection/location |
| G20.lost | Lost Mode | R | Authorised mode/readback without destructive test |
| G20.precise | Precise finding | R | Real supported UWB route/result, model applicability |

### G21 — Security and accessibility (stages 5, 9)

**Pointer:** REG G21; PLAN stages 5 and 9. The README has no dedicated section.

README admission: none specific; all rows research.

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G21.passcode | Passcode | R | Authorised set/lock/unlock/readback |
| G21.wrist | Wrist detection | R | Real lock/sensors/setting/readback |
| G21.medical_id | Medical ID | R | Private fields sync/display/edit without loss |
| G21.fall | Fall detection | R | Setting/native status and a safe documented test |
| G21.crash | Crash detection | R | Setting/native status; safe test without a crash |
| G21.sos | SOS | R | Settings/transport in a safe test, never a real emergency |
| G21.assistive | AssistiveTouch | R | Native setting and real gestures/actions |
| G21.voiceover | VoiceOver | R | Native setting and real speech/navigation |
| G21.zoom | Zoom | R | Native setting and real zoom |
| G21.rtt | RTT | R | Authorised test text-call route and real data |
| G21.live_listen | Live Listen | R | Real authorised audio route |
| G21.braille | Braille | R | Applicable keyboard/device and real input/output |
| G21.keyboard | Accessibility keyboard | R | Setting/native input/action |
| G21.mirroring | Mirroring | R | Real frames/input/authorisation/latency |

### G22 — Apple account and cloud (stage 9)

**Pointer:** README line 59; REG G22; PLAN stage 9.

README admission: #3; Apple ID session ownership is an open question in our own charter too.

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G22.login | Apple Account sign-in | R | Real authorisation/session/error/revoke |
| G22.2fa | Two-factor | R | Real approved challenge; secrets never logged |
| G22.state | Account state | R | Real login/token validity/expiry/logout |
| G22.icloud | iCloud | R | Each applicable data function with a real cloud result |
| G22.cloudkit | CloudKit | R | Authorised container/records/conflicts/deletion |
| G22.home | Home | R | Authorised native home/accessory/read/action |
| G22.handoff | Handoff | R | Native authorised continuity session |
| G22.unlock_mac | Unlock a Mac | R | Real authorised proximity/auth/readback |
| G22.unlock_phone | Unlock an iPhone | R | Native applicability and authorised outcome |
| G22.subscriptions | Subscriptions | R | Each available service entitlement/result |
| G22.regional | Regional apps | R | Applicability/availability/function per app |

### G23 — Wallet and payments (stage 9)

**Pointer:** REG G23; PLAN stage 9. The README has no dedicated section.

README admission: #3. Highest-risk group: secure element and payment networks.

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G23.passes | Passes/tickets | R | Signed pass import/display/update/delete |
| G23.transit | Transit | R | Authorised issuer provisioning/native use/readback |
| G23.keys | Keys | R | Authorised issuer/device provisioning and supported use |
| G23.apple_pay | Apple Pay | R | Confirmed issuer/account/SE chain and authorised result |
| G23.cash | Apple Cash | R | Applicability/account/auth/authorised operation |
| G23.card | Apple Card | R | Applicability/account/auth/authorised operation |
| G23.bank | Bank provisioning | R | Issuer challenge/provision/readback with no invented token |
| G23.se | Secure-element lifecycle | R | Real attestation/keys/authorised provisioning/revoke |

### G24 — Cellular (stage 9)

**Pointer:** REG G24; PLAN stage 9. The README has no dedicated section.

README admission: #3; only cellular-capable models apply.

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G24.esim | eSIM | R | Authorised profile provisioning/native modem/readback |
| G24.entitlement | Operator entitlement | R | Real operator API/auth/entitlement |
| G24.tariff | Plan | R | Real supported plan/status |
| G24.number | Number sharing | R | Real operator number-sharing and real calls both ways |
| G24.dual_sim | Dual SIM | R | Applicability/selection/native status/routes |
| G24.calls | Calls without the phone | R | Real modem audio/call/signalling |
| G24.data | Data without the phone | R | Real Watch internet with no phone connection |
| G24.modem | Modem state | R | Native signal/operator/profile/roaming/errors |

### G25 — Family modes (stage 9)

**Pointer:** REG G25; PLAN stage 9. The README has no dedicated section.

README admission: #3.

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G25.setup | Family Setup / For Your Kids | R | Separate authorised setup while keeping an ordinary pair |
| G25.schooltime | Schooltime | R | Real schedule/enforcement/readback |
| G25.reports | Family reports | R | Authorised real child data/source/time |
| G25.limits | Restrictions | R | Native policies/enforcement/changes |
| G25.services | Family Apple services | R | Each applicable authorised service separately |

### G26 — Maintenance (stage 8)

**Pointer:** REG G26; PLAN stage 8. The README has no dedicated section.

README admission: #3; destructive rows require approved tests.

| ID | Capability claimed | Status | Physical proof needed |
|---|---|---|---|
| G26.backup | Backup | R | Native consistent snapshot/hash/private data/identity |
| G26.restore | Restore | R | Authorised restore of a verified set and real readback |
| G26.ota_download | OTA download | R | Real manifest/payload/hash/resume |
| G26.ota_verify | OTA verify | R | Signature/version/device compatibility/rollback gates |
| G26.ota_install | OTA install | R | Separate authorised real update with pair preserved |
| G26.recovery | Recovery | R | Exact native flow in a separate approved test |
| G26.diagnostics | Diagnostics export | I | Real bounded native inventory/archive with hash/privacy |
| G26.unpair | Unpair | R | Only explicit owner action; real removal/readback |
| G26.erase | Erase | R | Only explicit owner action in a separate test |

## 4. Cross-cutting claims and what they are worth

| Claim | Where | Assessment |
|---|---|---|
| "TESTED ONLY ON watchOS 26.2 (23S303)" | README line 33 | A self-imposed scope limit. It is the single most useful admission in the document: nothing else is claimed. |
| Hardware: Apple Watch Ultra 2 `Watch7,5` | README line 57 | One model, one OS build. Ultra 1 SMP timeout remains open (CHANGELOG). |
| JVM suites cited between 164 and 188, with 714–1,110 tests; Flutter 60–81 tests | README, RESULT, 388, 387 | Test counts measure the reference's own code, not protocol correctness. The reports cite different counts for adjacent builds; treat counts as build-local. |
| "Full Watch.app parity" | README line 59 | Explicitly denied in the same sentence. |
| Owner-confirmed face / activation | 388, RESULT | The strongest physical evidence the reference has is an owner's report, not an instrumented observation. That is legitimate for a usability claim and insufficient for a protocol claim. |
| Native binaries reconstructed | SETUP_CHAIN, README line 385 | Reverse-engineering results are build-specific; the reference itself warns not to reuse addresses across builds. |
| Statistically bounded UI work | 51, 90 | Offline research validation, explicitly not production UI. |

## 5. Evidence-pointer integrity (audit finding)

The quarantined copy cannot substantiate many of its own citations. The reference's `.gitignore` publishes only two `docs/live-*` folders; the rest of `docs/live-*` is excluded. As a result, **dozens of linked reports are absent from the copy**, including `SUMMARY.md`, `WIFI-376-HARDWARE-EVIDENCE.md`, `CLOCKFACE-357-COMPANION-EVIDENCE.md`, `CLOCKFACE-358-NATIVE-DELTA-EVIDENCE.md`, `DEVICE-347-ABOUT-UI-EVIDENCE.md`, `PING-348-HARDWARE-EVIDENCE.md`, `PHONE-PING-349-EVIDENCE.md`, all `HEALTH-361…374` reports, `NATIVE-SETTINGS-351-EVIDENCE.md`, the `CLOCKFACE-352…356` series, and the bridge/companion refactor reports. Local link check on the surviving prose: 36 distinct missing targets, 48 references, across 9 documents.

Consequences for us:
- Every "H" row that cites an absent report is an **unverifiable claim in this copy**, not evidence.
- Our specs must rely on the surviving prose (README, SETUP_CHAIN, RESULT, 387, 388, 375, 346, 345, 39, 51, 90, 72, 73) and on our own captures.
- Do not cite absent documents as if we had read them.

## 6. UNKNOWN

- **UNKNOWN:** how many of the 253 rows the reference would itself grade as working; it refuses to compute a percentage ("no finished denominator", registry preamble).
- **UNKNOWN:** whether any row graded I was ever exercised on a second Watch model. Only one model is named as tested.
- **UNKNOWN:** whether the H rows were run once or reproducibly; most reports describe a single session.
- **UNKNOWN:** the status of rows whose cited evidence document is absent from the copy (see §5).
- **UNKNOWN:** whether the 1,000+ test counts cover wire format or only local policy; the reports describe both without separating them.
