# Initial HRIS sync transport

Use classic Matrix sync for the initial scoped Android and browser integration.
The actual pinned Kotlin SDK can use `Client.syncV2` with both the client builder
and restored session set to `NONE`. A separate native mode retains `SyncService`
with `NATIVE`. Each client restores its session once; refresh persists rotated
credentials and preserves its signing key. Both modes own and cancel their sync
task/service before closing the room and client, with a five-second cleanup bound.

The same APK passes encrypted text in both directions, a paused SDK queue with
an encrypted logical message ID, standard refresh, and the first message to an
already-enrolled offline browser invitee. The recipient comes back and decrypts
after the sender process has stopped. The native experiment observes actual
SlidingSync servlet requests; classic observes none. Browser fixture control and
negative probes share the metrics, so counts are not an endpoint allowlist.

Classic room summary member counts did not reach the expected values, although
version 12, encryption and invited history were present. Optional summaries are
not membership authority. Both experiments now read the complete SDK member
iterator and require exactly the joined sender and invited peer, with no extra
members, before sending. The iterator is closed and its read has a 15-second bound.
Bounded static progress codes and readiness flags survive failure cleanup; no
exception messages, identifiers, payloads or tokens enter the retained diagnostic.

[Evidence](../measurements/collaboration-dual-sync-2026-10-31.json) retains the
three failed experiments, the corrected compilation/formatting checks and both
successful provider runs. This is protocol feasibility, not product acceptance.

The HRIS directory will own the conversation list. A classic sync gateway still
needs current admission for every room and to-device envelope, bounded long-poll
ownership separate from short-control permits, filtered account data/presence,
device-list eligibility, and cursor recovery when an HRIS room binding becomes
visible after its provider events. Blindly dropping room arrays from an old
cursor would lose initial state. The [actual scope-recovery experiment](sync-scope-recovery.md)
now confirms additional invitation-filter and creation-state behavior. No public
sync/content route is opened here.

Native sync is retained as a comparison and potential optimization. Its ordered
list operations, counts, subscriptions and E2EE/to-device extensions need a
separate scoped contract before exposure. Swift interop, native offline receiving,
encrypted process-restart recovery and measured large-directory performance keep
their own acceptance gates. Neither mode inherits those results from this proof.
