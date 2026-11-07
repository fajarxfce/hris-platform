# Non-execution evidence for original room requests

Status: [V138 storage and database exclusion](room-request-fence-storage.md) are now
locally validated. No owning fence endpoint, live migration or replacement authority
is activated. The remaining integration follows this design. The accepted
stable request/receipt contract remains [separate](native-room-stable-intent.md).

## Problem and result

A read returning no receipt can race the original writer. A different native parent
may recover an existing receipt, but it cannot replay the old device as if that
device belonged to the new parent. Replacing the operation after an empty read could
allow both the delayed original and its successor to run.

Add an explicit actor-owned operation fence. Under the SAME original stable v2
operation lock, it returns either the original setup receipt or immutable evidence
that this original request can no longer execute. Existing queued/committed work is
never relabelled as non-execution. A fence says nothing about SDK messages, room
readiness or content admission.

## Required boundaries

- Keep `OperationRepository` and its `MutationReceipt` protocol unchanged. Never
  encode a fake resource/version as non-execution evidence.
- A dedicated room-request fence repository maps a raw datasource. Its immutable
  record identifies account, original operation, space, conversation, stable input
  fingerprint and a server-generated fence identity/time. Current authentication
  and native session versions are not part of the original business fingerprint.
- Key tombstones by account/original operation within the stable v2 namespace,
  so the same original operation cannot be reused for another room or payload.
  Do not expire/delete a tombstone while an original request could be replayed.
  Enforce owned global scope through transaction-local RLS and immutable history.
- Add a separate migration after V137 only after validating schema constraints,
  generated bindings and both runtime roles. No production rollout is implied.

## Transaction and admission

The writer and fence use the original `collaboration.space_room_setup.v2`
operation lock, then the existing room/account/current-native ordering. The fence
uses CURRENT parent/client and current group/participant admission. It must not
require or revive the original device, native parent, provider credential or SDK.
Lookup failures, payload conflicts and existing results stay undisclosed until
current admission, and final deadline/admission checks guard transaction completion.

If the original already committed, return its exact typed receipt. If it did not,
insert immutable non-execution evidence under the original operation lock. Every
stable writer must check that evidence before recording a receipt or queuing work.
A late writer then rejects the original explicitly. Legacy v1 fingerprints and
legacy writes remain separate; a v2 fence cannot cancel a v1 operation with the
same UUID. Failed or cancelled transactions must leave neither partial evidence
nor an executable replacement. If a response is lost, the same original descriptor
recovers the exact retained result without another fence identity.

Keep this separate from successor creation. The owning Mobile flow first retains
and validates the exact typed fence response, then atomically archives the original
uncertainty with its successor link using the existing command handoff primitive.
A missing receipt, transport timeout, stale credential or plain denial is not that
evidence. Message transaction IDs and original private keys stay unchanged.

## Acceptance before activation

Use real PostgreSQL transactions and application contexts: writer commits first,
fence commits first, a competing uncommitted writer later commits/rolls back,
interruption/cancellation before final commit, response loss/replay, changed payload,
another account/space/room, peer removal, current-parent replacement/revocation,
client minimum build, deadline/clock rollback, exact original receipt, and legacy/v2
UUID isolation. Assert queue, receipt and fence counts and current denial ordering.
Validate API plus FULL worker DI if shared constructors change. Native HTTP tests
must cover schema/bounds/no-store and disabled routes. Mobile tests must preserve
uncertainty through lost persistence, cancellation and successor preparation.

This work does not by itself activate organization routing or solve measured phone
latency. Original SDK delivery/history, local projections and current recipient
admission remain required before product activation.
