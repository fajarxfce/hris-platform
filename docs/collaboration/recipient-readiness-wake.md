# Committed recipient readiness wake

Implementation candidate, not validated or deployed. The current API reports
`recipientReady` and blocks encrypted submission to an unavailable recipient.
The next step must wake an already waiting sender after the recipient's first
public-key publication commits, without restarting a timed transport loop.

## Reuse the committed outbox

`AccessNativeCollaborationMatrixDevice.publishKeys` already records
`collaboration.device_keys_published` in the same transaction as key publication.
`PostgresChangeJournalDataSource` appends the matching audit and outbox rows together.
Consume that outbox event rather than adding another API callback, emitting a fake
Matrix message, or granting the publisher access to another account's private data.
The worker cannot observe an uncommitted event. A failed key/journal transaction
must leave neither published readiness nor an executable event.

Retain one fanout checkpoint per event, linked to the immutable company, publisher
account and device. Worker claims need bounded batches, expiring leases with an
opaque token, exact-token completion and durable retry limits. API roles must not
claim or complete another account's fanout. The queue-wide read grants only the
coordinates needed for subsequent scoped admission.

## Bounded fanout and duplicate prevention

Visit published push bindings belonging to existing direct-conversation peers in
the event's company. A stable account/device cursor and an immutable publication
cutoff bound the scan. Do not broadcast to the employee directory or enumerate
private registry rows under the publisher's ordinary role. Use a restricted metadata
projection and preserve the original role's RLS.

For each target, revalidate the captured fanout lease/checkpoint and current target
binding in a short transaction. Queue an opaque, event-null collaboration push hint
and advance the exact checkpoint atomically. Keep provider I/O outside this transaction.
Existing `HandleCollaborationPush` owns actual delivery and its current session,
membership, company, policy and native-registration checks. A stale target must not
reuse another native parent or device's push binding.

V116 coalesces an unclaimed, unattempted pending hint. That is not durable fanout
deduplication: after delivery or claim, appending again creates a new revision.
Atomic target checkpoint plus queue append is therefore required. A lost response
must reread the original checkpoint, never reset it or allocate another publication.
Concurrent workers, expired claims and late acknowledgements must be fenced.

## Client and missed-hint recovery

The push contains no message body or permission. On receipt, existing recovery
rechecks current transport before resuming the original local command and SDK
transaction. The sender must not create a new message or reorder its local intent.

App-owned activation creates first keys on admitted native sign-in/company selection;
established private-key loss requires recovery. Activation requests device sync before
any room is opened. A Matrix device-change callback can arrive before the HRIS key
publication commits, so it cannot be the only wake source. A post-commit outbox wake
closes that race. App startup and explicit connectivity recovery must also cover
expired/missed push delivery and bindings created after a fanout cursor has passed.
They must preserve the distinction between new recovery events and routine admission
renewal; do not wake every blocked message on every sync response or token refresh.

## Required validation and rollout

- Actual PostgreSQL rollback, RLS, wrong publisher/company/device, finite pages,
  concurrent claims, cancelled transactions, lost responses and late lease completion.
- Queue append/checkpoint failure in either direction; a delivered push followed by
  replay of the same publication must not append a second target hint.
- Binding/session replacement and revoked membership while a target waits for its turn.
- Publication racing an earlier provider device-change event; recipient publication
  wakes the same waiting sender once after the authoritative commit.
- Interrupted first activation, process restart, missing push and offline originals.
- Worker and API dependency-injection contexts, alongside existing push delivery tests.

Live schema is116.1. Any readiness migration must be isolated and compatible with
that schema; migrations117–138 remain undeployed. A source change or migration file
is not rollout approval evidence. Preserve original keys, messages and device data
through native acceptance. Calls, meetings and cloud key-backup recovery remain separate.

## Candidate source — 8 November 2026

V116.2 captures a first-publication outbox row in the original transaction and
retains one job per company/device. It does not backfill earlier publications.
A restricted metadata view lists only existing direct peers with push bindings
published before the immutable cutoff. The view owner has no pusher-secret column
grants; an actual worker session plus publisher/company scope is required.

`FanOutCollaborationReadiness` acquires at most four jobs and processes one page of
eight targets per lease. Each target's opaque queue append and exact checkpoint
advance share that target's transaction. Current push admission remains owned by
`HandleCollaborationPush`. Successful progress resets the job's retry count; eight
consecutive failed claims, a one-day expiry, 60-second token-fenced leases and a
20-second task budget bound recovery. A publisher cannot extend or replace an
active lease. Queue-wide capacity is16, and each worker owns two execution slots
with no waiting task queue.

The new worker is disabled unless `HRIS_COLLABORATION_READINESS_ENABLED=true` and
all existing chat/provider/push-delivery flags permit it. This flag must remain off
until isolated V116.2 and source/native acceptance succeed. No117–138 rollout is
implied. Source currently includes unexecuted pure transaction/cancellation/lost
response tests, PostgreSQL migration/RLS/cursor/queue tests and actual worker
context/lifecycle tests. Formatter, compiler and test gates have NOT run because
Mobile owns the sole heavy-process slot. Client missed-hint/interrupted activation
recovery and actual original-sender post-commit resumption remain unfinished.
