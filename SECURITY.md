# Security policy

Open Crown ships software that borrows root on the user's own phone. A
vulnerability here is not an ordinary bug, and it is treated as critical.

## Reporting

Use the repository's private security advisory form. Do not open a public issue
for a vulnerability, and do not include working payloads or user data.

Include: affected component, version or commit, phone model and build, what you
observed, and a minimal reproduction if you have one.

## What counts

- A way to make the agent execute something the capability policy does not
  grant.
- A way to run an unsigned or non-matching payload.
- A way to persist privilege across a reboot, or to hide the agent from the
  notification.
- A leak of bond material, activation material, or Apple ID input.
- A payload that damages user data or bricks a device.
- A protocol parser that crashes or corrupts memory on untrusted input.

## Our side

- Acknowledgement within 7 days.
- A written assessment within 30 days.
- 90-day coordinated disclosure window, shorter if the issue is being exploited.
- A revoked payload is announced in the app and in the catalog.
- Credit to the reporter unless they ask otherwise.
