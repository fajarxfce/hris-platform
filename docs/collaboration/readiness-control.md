# Recipient readiness on the active Matrix connection

The controlled native test on 8 November showed first-key publication and its FCM
job completing promptly, but Google Play Services delivering the accepted hint
about 297 seconds later. The unchanged sender then sent its original once, about
2.6 seconds after OS dispatch. The 140-second gate failed. This is evidence for
an independent foreground signal, not for restarting transport requests on a timer.

## Protocol boundary

`dev.fajar.hris.recipient_ready` is a fixed, clear **to-device control event**. It
does not create a room event, message, read receipt or encrypted key. The internal
sender derives the target's own Matrix user/device from admitted HRIS coordinates;
it uses that target's existing provider credential. No service bot, new login,
replacement device or private-key reset is needed. The public Matrix gateway
continues to accept only its existing encrypted to-device send shape.

The nine-field V2 content contains the immutable publication event and revision,
target company/account/native session/device, publication time and its one-day expiry.
All values have canonical bounded encodings. The existing sync projection accepts
only this exact shape, from the syncing user's own identity, for its exact current
company/account/device. It discards foreign or malformed controls without losing
the rest of the encrypted sync response. Native-parent/time admission remains
required in the consumer. The hint grants no chat content or send authority.

V116.4 increments a company-scoped counter while capturing the control journal.
That counter's row lock lasts through the publication transaction's commit or
rollback, after its own device/key guards and audit insert. No further repository
guard is acquired after the outbox insert. Revisions therefore follow commit
order even when publication timestamps are reversed. A received higher revision
subsumes lower committed publications in one full owned recovery sweep. The client
must durably retain that wake before advancing its local requested revision.

The deterministic transaction includes the publication and target device IDs.
Synapse's transaction cache can disappear on provider restart; provider acceptance
is not an exactly-once guarantee. The client must deduplicate retained controls and
reconcile existing local originals. It can retain a bounded checkpoint per owned
company/device/native family instead of an ever-growing event-ID set. A missed callback must be recoverable through
startup/connectivity work over those same originals.

## Independent delivery ownership

V116.3 captures a separate Matrix checkpoint in the first-publication transaction,
alongside the existing FCM checkpoint. V116.4 adds immutable commit-ordered revisions
without changing V116.3's checksum or assigning historical revisions. Legacy V1
journals remain retained and are excluded from V2 claims; the due index has the same
predicate. The earlier V1 transport was never enabled on the live development server.
Keeping progress
independent prevents an unavailable provider from delaying FCM, or notification
permission from excluding an active device. Its restricted view selects published
devices of pre-existing direct peers, with at most eight targets per stable page.
The reader receives public coordinates only, not key fingerprints or credentials.

`FanOutCollaborationReadinessControl` acquires current company/member/account/native
and device/gateway inputs under ordered guards, checking current client policy and
key publication before acquiring an existing provider credential. Provider I/O
happens outside transactions, under a finite deadline, followed by current-owner
and credential-generation checks and exact lease/checkpoint advancement. Timeouts
and lost replies retain the original event and transaction. This worker does not
create or refresh credentials; the active SDK owns that lifecycle.

The Matrix queue has a separate global capacity of 16 leases, eight failed claims
without progress, 60-second leases, one-day expiry, two-second deferral and a
20-second page budget. A committed target checkpoint resets its consecutive
failure budget. FCM keeps its existing independent capacity of 16. The application
owns two execution threads with no waiting task queue and two timer threads per
enabled readiness worker. Explicit `HRIS_COLLABORATION_READINESS_CONTROL_ENABLED`
defaults off, requires schema116.4, and also requires worker, collaboration and Matrix. Push/FCM is not
required. Keep it disabled until source/context/migration/native gates pass.

Mobile0fb27978 connects the existing SDK5 listener before sync starts and releases it
with the exact client; source gates pass5,911 cases. Native delivery remains unproven. Callbacks must not invoke UI, networking policy, a ViewModel,
or the network inspector. An application-owned collector invokes the domain wake
use case; that use case validates the current parent and durably records/coalesces
work before scheduling. Duplicate controls and obsolete callbacks cannot create
repeated sweeps, resend an original, or wake a replacement account. Background FCM
and interrupted-activation/startup/connectivity recovery remain necessary.

## Acceptance boundaries

Transport tests exercise exact mapping, malformed/foreign controls, actual Synapse
sync wake, unacknowledged replay, same-device bearer replacement and provider restart.
These do not establish current-identity delivery admission, durable job behavior,
SDK callback lifetime, product delivery latency, or physical phone acceptance.
Those remain separate gates. The existing device stores and all original messages
must be preserved throughout further acceptance.


## Prepared development rollout

[Staging evidence](../measurements/collaboration-readiness-control-staging-2026-11-08.json)
binds both image JARs to the accepted e558a686 source. The isolated116.2→116.4
upgrade preserves all204 existing tables and migration history, creates no old
publication backfill and keeps restricted runtime roles. Both retained old images
and both new images pass isolated startup, health and scheduled database access.
The fixtures expose no host ports and cannot deliver FCM/mail/chat externally.
All temporary fixtures are removed and live services remain unchanged.

The reviewed next step applies only116.3/116.4 and replaces both API and worker
images, with the Matrix readiness-control flag still OFF. The old API cannot
project V2 controls. Preserve an online pre-migration backup and both rollback
image tags; restoring those images keeps the already-compatible additive schema.
Do not apply117–138 or replace device keys. After native77 retains its account and
public identity, explicitly enable the worker for controlled development proof
with that fictional sender's push registration disabled. This preparation is not
native signal-delivery acceptance or a communications PRD completion.
