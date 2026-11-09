# Independent organization readiness push handoff

One fanout use case now owns the same current-target observation and cursor rules for
Matrix and PUSH. Its name is FanOutCollaborationSpaceReadiness; claims explicitly
select a channel. PUSH additionally requires the push feature flag. Matrix keeps
its existing external send and current credential revalidation.

PUSH reads the current admitted device's exact immutable published binding, including
original/fresh Matrix identity and native parent. The binding must have been published
by the readiness capture time. It atomically appends a quiet hint to the existing
coalesced organization push queue and advances the readiness cursor under the same
account/native/device/gateway/checkpoint guards. Final eligibility and time checks
roll back both mutations on failure. Publication completion means durable handoff,
not FCM delivery acknowledgement.

This branch never decrypts a Matrix bearer, native push token, or push capability.
An expired Matrix bearer does not prevent handoff. HandleCollaborationSpacePush
remains the sole FCM delivery owner, rechecking current native registration before
and after external delivery and preserving exact-token UNREGISTERED cleanup.
No second cloud retry loop or new push envelope is introduced.

Quiet pending wakes coalesce. A readiness hint arriving during an existing FCM lease
advances the wake revision, so the older FCM acknowledgement cannot consume it.
Handoff and cursor progress are atomic; cancellation or a lost local write does not
leave a stray wake. Invalid, absent, unpublished, late or foreign bindings are
suppressed. No hint grants message content authority or proves a message was read.

Existing bounds remain: publication lifetime 24 hours, claims 4/page 8, active leases 8
per channel, lease 60 seconds, task 20 seconds, bounded SQL statements, no-progress
claims 8. A new quiet wake lives at most 300 seconds and never outlives its publication.
The existing FCM queue owns its independent bounded delivery attempts.

Validation covers real worker-role PostgreSQL, actual encrypted binding/native
registration repositories and the existing delivery use case. Only external Matrix
and FCM transport are fake. Cases cover atomic rollback, cancellation, elapsed
time, late group revocation, stale bindings, coalescing, in-flight FCM revisions,
post-handoff native revocation and disabled PUSH, plus existing Matrix and FCM tests.
The final gate passes 256 cases in 463.060 seconds: 205 domain and 51 PostgreSQL
cases, zero failures/errors/skips. Format and architecture pass. All 8,727 source
inputs and every fresh XML report match the retained hashes.
[Measurement](../measurements/collaboration-space-readiness-push-handoff-2026-11-09.json). No cloud, native SDK or phone acceptance is implied.

Next wire the shared DI and bounded scheduled owners, validate API and the complete
worker graph, then add durable Mobile group wake ownership. No new APK or live
rollout is part of this increment. All 54 communications acceptance groups remain open.
