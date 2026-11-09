# Scheduled organization readiness delivery

The shared collaboration graph now binds the typed organization readiness queue,
its combined Matrix/PUSH fanout use case and the existing Matrix control transport.
When that transport is absent, an explicit disabled implementation returns the
stable unavailable result without I/O. The existing concrete Matrix datasource
already implements the control contract; its provider configuration is unchanged.

The existing readiness worker now accepts an opaque typed lease. Its lifecycle is
unchanged and remains shared with company readiness. Business admission, retries,
transaction boundaries and durable progress remain in the fanout use case.
Organization MATRIX and PUSH have separate qualified task/timer pools and explicit
channel claims. Each pool has two workers, no waiting executor queue, a 20-second
task deadline and bounded shutdown. Timers remove cancelled work and do not run
delayed or periodic work after shutdown.

Both channels require organization rollout, collaboration, Matrix and worker flags.
MATRIX additionally requires readiness-control delivery. PUSH additionally requires
readiness, collaboration push, push delivery and FCM. Disabled channels do not claim
work. The existing push queue and delivery use case continue to own cloud delivery;
a completed readiness PUSH publication means durable handoff, not FCM acknowledgement.
No new credential, schema migration, provider refresh or mobile endpoint is added.

The new production-context test opens ten configurations: all enabled, then each
required flag disabled or absent. It uses the normal worker database role and real
queue SQL, checks exact independent channel claims, verifies disabled transport,
and closes and verifies all owned pools. It is one JUnit case, not ten. Its temporary
fictional credentials enable graph construction only; no cloud call is made.

Validation:
- Focused contexts and lifecycle: 8 cases, zero failures/errors/skips, 101.486 seconds.
- Six actual API context/contract classes: 174 cases.
- Complete unfiltered worker suite: 151 cases, including the new context case.
- Final gate: 325 cases, zero failures/errors, one existing opt-in elapsed-retention
  skip, 775.848 seconds. Format and architecture checks pass.
- All 8,729 source hashes remain identical across both gates. Every fresh XML hash
  matches; the worker report set is the previous complete suite plus the new case.

[Retained measurement](../measurements/collaboration-space-readiness-workers-2026-11-09.json).
The group rollout remains disabled in the live deployment. This increment does not
validate native callbacks, device wake or phone performance. Next is durable Mobile
organization readiness receipt and current-runtime wake ownership. All 54 broader
communications acceptance groups remain open.
