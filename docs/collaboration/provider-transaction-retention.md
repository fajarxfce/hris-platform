# Provider transaction retention gate

Source inspection on 31 October 2026 found a required offline-delivery boundary
in the pinned Synapse 1.164.1. This is a source finding; the aged-retry behavior
has not yet been reproduced through the running provider.

`get_event_id_from_transaction_id_and_device_id` looks up the tuple room, sender,
Matrix device and transaction ID. Replacing an internal bearer while preserving
the Matrix device should therefore preserve this deduplication scope. It must
still be verified through the actual provider.

The same source registers `_cleanup_old_transaction_ids` every five minutes.
That method deletes `event_txn_id_device_id` mappings whose `inserted_ts` is more
than 24 hours old. Durable client transaction IDs alone cannot establish that
an uncertain send retried days later will remain a single event.

Required implementation/proof before accepting offline messaging:

1. Send through the real pinned provider, replace the private bearer on the same
   Matrix device, then repeat the exact transaction and verify the same event.
2. Exercise retry after the transaction mapping's retention boundary. Clearly
   distinguish an owned fixture aging/cleanup action from waiting for real time.
3. Implement durable logical-message identity and receipt/reconciliation behavior
   for uncertain sends. Do not reset the client transaction ID after token refresh,
   process death or response loss. Never blindly resend an old uncertain operation
   on an assumption of unlimited provider deduplication.
4. Verify restart before/after send/response/local ACK, long offline reconnection,
   repeated notification hints, edits/forwarding/redaction references, and recipient
   presentation so a duplicate provider effect does not become duplicate user work.

No offline chat group is accepted yet. This finding does not alter the established
rule that a late/losing login cannot call Synapse logout: that deletes the entire
winning device and all its E2EE keys.

Pinned upstream source:
https://github.com/element-hq/synapse/blob/v1.164.1/synapse/storage/databases/main/events_worker.py

Inspected source SHA-256: `9d9e1b6dad5f3bf1fa112695d7283342a1a74a4afa08a90375b4822f558e0871`.
