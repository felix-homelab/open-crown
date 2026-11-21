# Phase 0 evidence — K2a and K2b

One file per run. Keep the raw log next to it.

## Session

| Field | Value |
| --- | --- |
| Date | |
| Operator | |
| Phone model | |
| Build fingerprint | |
| Kernel release | |
| Android release | |
| SELinux mode before the run | |
| Payload id | |
| Payload signature | |
| Payload target (model / kernel build) | |
| Probe build id | |

## Stage A — the payload yields uid 0 (K2b)

| Observation | Value |
| --- | --- |
| K2A uid / euid | |
| /proc/self/attr/current | |
| Payload ran without a prior root | yes / no |

Result: pass / fail

If it failed: which stage (match, signature, target, exploit), and the exact
error line.

## Stage B — the controller bind (K2a)

| Stage | Result | errno | Notes |
| --- | --- | --- | --- |
| socket | | | |
| device_down | | | |
| bind_user_channel | | | |
| read_local_version | | | |
| reset | | | |

Android Bluetooth stack running at the time: yes / no / stopped by the probe

## Denials

Quote the first avc line verbatim. It names the domain and the missing
permission.

    K2A avc=

If /dev/kmsg was unreadable, record the errno.

## Recovery

| Check | Result |
| --- | --- |
| Rebooted successfully | |
| SELinux enforcing again | |
| No probe or agent left | |
| App data intact | |

## Verdict

- [ ] **K2b pass, K2a pass** — Gate G0 open, continue the stock-phone plan.
- [ ] **K2b pass, K2a fail on policy** — worth one design pass; record the gap.
- [ ] **K2b pass, K2a fail on ownership (EBUSY)** — the product cannot coexist
      with Android's Bluetooth stack; the architecture must change.
- [ ] **K2b fail** — no usable payload for this build; bring-your-own-root.

## Notes

What surprised you. What the next run changes.
