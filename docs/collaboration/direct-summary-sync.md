# Incremental direct-summary metadata

Storage capture, bounded projection and the dedicated native metadata feed are
accepted locally. The live endpoint still acquires UUID pages. Deployment and the
atomic Mobile consumer remain pending; changed-only acquisition is not yet delivered.


## Accepted native feed

Native Android/iOS clients read `/api/v1/companies/{companyId}/collaboration/direct-conversations/summary-sync/bootstrap`
and `/changes`. The bootstrap cursor is optional only for the initial request;
changes always requires one. Limits are 1–50. Bootstrap returns ordered metadata
facts, either `nextPage` or `changesCursor`, `pending`, and `serverTime`. Changes
returns ordered publication positions with current versioned facts, `nextCursor`,
`hasMore`, `pending`, and `serverTime`. Null summaries are retained tombstones.
Message bodies remain in the independent Matrix transport.

Opaque authenticated cursors bind company, account, stable native parent,
credential/company/membership versions, journal epoch and phase. Ordinary access
rotation preserves the parent. Invalid framing/owner is rejected; scope changes,
expired retention and out-of-range journal positions require a new bootstrap.
Bootstrap expires after 15 minutes and is bounded to 1,000 pages. Changes captures
a finite upper position for each round and renews its seven-day lifetime only at
the round boundary. Source record revisions are independent of canonical version0.

One batch hydrates at most50 distinct conversation identities per change page.
Returning the current higher revision at an earlier reference prevents an old
withdrawal from erasing a later restoration. The initial snapshot captures a
publication boundary; clients must catch up afterward and wait for `pending=false`
before replacing their visible generation. One coherent pending query includes
relevant account fanout, owned pair refresh and unpublished owned references.
Current policy, company/member/account and native-parent admission are checked
before acquisition and again after either its successful or failed result. Cursor
time is rechecked after I/O. Explicit owner predicates supplement company RLS.

[Feed evidence](../measurements/collaboration-summary-feed-2026-11-05.json):
focused correction passes10 fresh HTTP cases74.113s; the broad gate passes139
fresh cases432.482s with zero failures/errors and one existing opt-in worker skip.
The full worker application graph, 16 native HTTP cases, format and architecture
pass on8,240 unchanged inputs. Six new policy/codec cases and three existing
Matrix cursor cases passed earlier and remain unchanged. Failed fixture and
compile attempts are retained in the evidence. No live deploy or Mobile delta
acceptance is claimed.

## Accepted projection worker

One global transaction locks up to four pending account rows, reads finite
continuations and enqueues at most200 distinct targets in one company/conversation
order before advancing those accounts. Each invocation then projects at most200
pairs in up to four explicit company transactions, with one source query and one
current-record query per batch. Only changed owner records advance their metadata
revision; unchanged labels produce no journal entries. Account queue reads select
only identity/continuation; PostgreSQL's infinite due sentinel stays in SQL.

Queue locks precede MVCC source reads, without source SHARE/UPDATE guards. Existing
Identity/CredentialStore account mutations use NO KEY UPDATE, allowing immutable
foreign-key references without reversing source/queue lock order. Tests exercise
that compatibility, concurrent workers, late name capture,205-pair fanout across
five companies, failed completion rollback and cancellation. The fixed-delay timer
belongs only to the worker and stops with its owner. It maintains existing metadata
independently of interactive module availability; every reader still requires
current admission.

[Worker verification](../measurements/collaboration-summary-worker-2026-11-05.json):
focused correction passes13 fresh cases97.671s; all-worker gate passes123 fresh
cases378.467s with zero failures/errors and one existing opt-in elapsed-retention
case skipped. Six new PostgreSQL worker cases and seven native API context cases
pass; four pure policy cases passed earlier and remain unchanged.8,226 source
inputs are unchanged in each run. Format/architecture and complete worker/API DI
checks pass. The first three failing queue reads remain recorded. No deployment.

## Accepted storage foundation

V116.1 follows the shipped V116 prefix and precedes the unshipped group V117–137
range. No existing migration is edited or renumbered. jOOQ generation now orders
Flyway numeric version components, rejects duplicate equivalent versions and
declares the helper as a Gradle input. The recovery verifier explicitly admits
116.1 with the same encrypted-envelope query as116; unknown intermediate/future
versions remain rejected. Group integration must retain this ordering helper.

An account display-name change captures one raw account identity, without scanning
dependent conversations inside the account transaction. A dedicated non-login,
non-bypass capture role can read only direct-pair identity columns. A worker-only
function exposes at most201 ordered target identities across companies. It reads
neither account names nor private profiles. API and worker logins do not inherit
that capture role. A source insertion separately queues its pair, so later inserts
behind a fanout continuation will not be missed.

The worker will lock an account queue item while advancing its finite continuation
and inserting per-company refresh identities atomically. A concurrent rename waits
and then resets the continuation; completion cannot erase the later invalidation.
Projection execution must use separate explicit company transactions and bounded
source reads, without acquiring source guards after queue locks.

Owner-scoped summary records retain peer identity and a separate monotone label
revision. Canonical conversation identity and its version0 stay immutable. Worker
updates must change the public projection; identical writes are rejected. A null
label is a versioned tombstone and retains identity. Raw tests cover withdrawal
and restoration, but no new product withdrawal policy/command is introduced.
Peer offboarding alone does not remove owned history or metadata.

Record writes capture `COLLABORATION_DIRECT` references into the existing journal
with an explicit owner account. Existing after-commit publication assigns positions;
neither UUID ordering nor write timestamps act as change positions. Failed record/
queue transactions roll back together. API readers cannot mutate summary records
or clear raw work. The new collection is absent from generic sync selection;
native account/session/company/client-policy admission must protect its own reader.

The follow-up needs a captured publication boundary, finite initial snapshots,
opaque owner-scoped cursors, no-change responses, tombstones, cursor expiry recovery,
atomic local facts plus cursor and pending-publication reconciliation. Pending
account fanout, per-company refreshes and unpublished owned references must be
observed in one coherent query before declaring an initial replacement complete.
Ordinary age and credential refresh must not erase the published owned view.

[Verification](../measurements/collaboration-summary-schema-2026-11-05.json): full
database and existing sync domain/data regressions pass64cases/229.29s, zero failures/
errors/skips,8,216 unchanged source inputs. Migration ordering, database recovery,
recovery-set, format and architecture checks pass. Actual116→116.1 upgrade waits
for a preceding writer and retains its pair; pg_dump/pg_restore preserves queues,
tombstone, journal positions, RLS, function ownership and mutation guards. Tests
also cover bounded cross-company fanout, rename races, rollback and late commit
publication. The first negative-fixture expectation failures remain recorded.

No live migration, worker timer, native endpoint, phone change or throughput claim
is part of this storage acceptance. Live remains11a98115/schema116.

Independent Collaboration control42090785206 passes exact d03fb472:706 cases across
113 suites, zero failures/errors. Seven existing opt-in cases are skipped: six
native APK interop cases and the elapsed-retention workload. All nine new summary
HTTP cases execute successfully. Both backend/control and company-settings jobs
pass; downloaded artifact digest is verified. [CI evidence](../measurements/collaboration-summary-feed-ci-2026-11-05.json).
Full release CI42091460931 and live migration/deployment remain pending.

## Retained-data migration rehearsal — 5 November 2026

[Evidence](../measurements/collaboration-summary-upgrade-2026-11-05.json): a private
MVCC backup of the running schema116 database was restored to isolated PostgreSQL
and migrated/validated to116.1. All200 prior business tables retain exact counts
and content hashes; original migration rows remain unchanged. Seven retained pairs
enter the bounded refresh queue. Runtime roles remain restricted and constraints
valid. No live service or data changed. Runtime image compatibility and final
deployment remain separate gates; full CI42091460931 is rerunning only the timed-out
dashboard-integration job after slow Ubuntu font downloads exhausted its20min budget.
