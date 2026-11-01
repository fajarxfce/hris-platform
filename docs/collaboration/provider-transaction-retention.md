# Provider transaction retention gate

Source inspection and actual provider tests on 31 October 2026 establish an
offline-delivery boundary in pinned Synapse 1.164.1. [Recorded evidence](../measurements/collaboration-transaction-interop-2026-10-31.json)
separates the real HTTP observations from fixture aging/cleanup.

`get_event_id_from_transaction_id_and_device_id` looks up the tuple room, sender,
Matrix device and transaction ID. Replacing an internal bearer while preserving
the Matrix device preserves this deduplication scope: actual login replacement
followed by the same room/send transaction returned the original event ID.

The same source registers `_cleanup_old_transaction_ids` every five minutes.
That method deletes `event_txn_id_device_id` mappings whose `inserted_ts` is more
than 24 hours old. Durable client transaction IDs alone cannot establish that
an uncertain send retried days later will remain a single event.

Actual retention-boundary experiment:

- Send one opaque fictional `m.room.encrypted` event, issue another bearer on the
  same Matrix device, and repeat its transaction: exactly the same event ID.
- In the owned disposable SQLite database, age only that mapping and execute the
  pinned cleanup predicate. Restart Synapse to clear its in-memory response cache.
  This does not claim a real 24-hour wait or acceptance of the five-minute timer.
- Issue another bearer for that same user/device and repeat the transaction:
  a different event ID is returned. Actual room history contains both identical
  ciphertext events. This is a reproducible duplicate provider effect.
- The originating device sees `unsigned.transaction_id` on both events even after
  restart. An invited peer sees both events but receives no transaction IDs.
  Sender-only transaction metadata cannot deduplicate recipient presentation.

The payload is deliberately opaque fixture content, not a valid participant
ciphertext. These cases prove transaction/retention behavior, not E2EE. Existing
actual E2EE browser/native evidence remains separate. The refactored shared
provider fixture also re-runs the five-second token expiry/key-retention and
expired-token whole-device logout experiment unchanged.

Implementation decision for P1: persist a stable logical message ID before
handing a draft to the SDK's durable send queue, include it inside encrypted
content, and keep the mapping to every observed provider event ID. Scope the ID
by conversation and authenticated sender. Recipient ingestion must coalesce
identical retries, reject conflicting reuse, and preserve aliases/tombstones for
receipts, replies, edits and deletion. An ID copied from another sender must not
hide that sender's message. Unknown/older clients retain event-ID behavior.

The native experiment now proves this extra field through actual Android SDK
26.10.23 ↔ browser E2EE in both directions. `Timeline.sendWithExtraContent` retains
`org.hris.message_id`; neither the body nor ID appears in provider ciphertext.
The SDK queue pauses, emits its local transaction, then resumes and reports the
same transaction with its server event ID after ordinary credential refresh.
[Queue evidence](../measurements/collaboration-native-logical-queue-2026-10-31.json)
records both passing scenarios (29.090s), source/APK hashes and corrected failures.

The earlier synchronous `Room.enableSendQueue` failure is superseded by the
[two-room queue proof](../measurements/collaboration-room-queue-2026-11-01.json).
First access to the pinned Rust room queue can spawn tasks. Keep the parent default
paused and initialize the queue through async FFI (`Room.timeline`) before using
the synchronous room controls. The product writer now follows this order.

Final Android run `837dfb7f8f154058a06d65faa46229a6` sends only the admitted room
with `Room.enableSendQueue(true)`, then pauses it. A second durable queue survives
process death and remains disabled with its exact original transaction after both
encrypted directions complete; the HRIS trace observes no send attempt for that
room. Global disable remains valid for owner shutdown/revocation. Global enable
is not room admission and must not substitute for it. Swift exposes related APIs
but has not executed this scenario.

Owned logical intents, common staging and native restart mapping now exist.
The native per-room delivery lifetime is now implemented and
[verified through the product port](../measurements/collaboration-delivery-2026-11-01.json).
Mobile `8d8fb233feee480e6e7dc7bc0430195ccf906005` now implements the encrypted outbound
receipt journal and offline command-state reconciliation, including every observed
alias and immutable mapping. [Receipt evidence](../measurements/collaboration-receipt-2026-11-01.json)
records real SQLite/IndexedDB recovery and ownership/concurrency cases. The journal
must precede `Sent` projection; its absence never proves delivery.

The common live-admitted sender still needs to connect native results to the journal.
Incoming recipient coalescing and history reconciliation remain to implement,
including a crash before native ACK reaches the application journal. Do not add
a second send retry owner.

Remaining acceptance must cover restart before/after send/response/local ACK,
long offline reconnect, repeated notification hints, conflicting identity reuse,
edit/forward/redaction references, recovery and recipient presentation. No
procedure-only replacement for these executed checks is accepted.

No offline chat group is accepted yet. This finding does not alter the established
rule that a late/losing login cannot call Synapse logout: that deletes the entire
winning device and all its E2EE keys.

Pinned upstream source:
https://github.com/element-hq/synapse/blob/v1.164.1/synapse/storage/databases/main/events_worker.py

Inspected source SHA-256: `9d9e1b6dad5f3bf1fa112695d7283342a1a74a4afa08a90375b4822f558e0871`.
