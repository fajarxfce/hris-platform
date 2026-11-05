# Incremental direct-summary metadata

The first increment supplies storage capture for a dedicated native metadata feed.
The current live summary endpoint still acquires UUID pages. Projection execution,
native HTTP cursors and the Mobile consumer are not implemented by this increment.

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
