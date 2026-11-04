# Group sync protocol and cursor ownership

The group provider adapter projects the complete original/published DIRECT room union.
Original rooms remain readable before adoption, including queued encrypted to-device
frames from their original peer identities. A published LEGACY room overlaps only its
exact original tuple and appears once. Current peer aliases in separate group rooms
remain distinct from original aliases. The provider filter is never an ACL.

`MatrixSyncReader` shares bounded protocol acquisition with the company adapter while
keeping identity, per-room participants, actual creator, direct metadata and history
cursor sealing explicit. Every join/invite/leave bucket is restricted to owned rooms;
only encrypted message/key frames and allowed metadata survive projection. Initial/full
sync hydrates the immutable creation event when needed. The existing stable provider
filter, separate finite connection pool, response bounds, deadline, cancellation and
close ownership remain in force. Both canonical `HRIS_` and `HRISG_` device namespaces
can own sync connections; pool and duplicate-owner limits still apply.

Group sync positions have a distinct authenticated purpose over six coordinates:
space, account, group device, server, original/current Matrix user and Matrix device.
The provider since/filter and complete-scope digest are encrypted; no plaintext position
is returned. Group positions cannot be replayed through the company decoder or another
device. A canonical completed-retained identity may authenticate its exact original
company/device cursor, then receives a null position to initialize the complete new
scope/filter. It never carries that old since/filter into a changed scope. Fresh devices
cannot inherit the original cursor. Missing crypto keys and cancellation do not trigger
legacy fallback. Current native/completion admission belongs to the use case and is not
established by successful cursor decryption.

Actual Synapse tests preserve original ciphertext, sender/device IDs, creation metadata
and pending encrypted to-device envelopes across provider restart, original-only scope,
exact adoption and incremental acknowledgement. A fresh HRISG device on an original
user reads the owned room through its isolated cursor and does not inherit the other
device's pending queue. These are opaque provider protocol tests; SDK Olm/Megolm
verification, decryption and durable-store acknowledgement remain separate acceptance.

This change has no schema or DI graph change and does not enable group sync HTTP yet.
Next connect fixed native admission and full inventory revalidation around both provider
outcomes and cursor sealing, then validate API and FULL worker contexts. Historical peer
key authentication, metadata/push, Mobile ownership transition and native hard-parent
recovery remain open. Group rollout is disabled; all 54 PRD acceptance groups are open.

See [accepted gate evidence](../measurements/collaboration-space-sync-protocol-2026-11-04.json).
