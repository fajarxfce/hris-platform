# Native Matrix read receipts

The gateway now accepts exact public `m.read` and private `m.read.private`
receipts for an admitted direct room. `POST /_matrix/client/v3/rooms/{roomId}/receipt/
{receiptType}/{eventId}` uses only the bound SDK bearer, no query and an empty JSON
object (256-byte request bound). An owned, mapped provider-event read precedes the
separate metadata write. Each exchange rechecks current HRIS company, native session,
device and room admission before I/O and before releasing its result. A late denial
withholds acknowledgement even if the remote metadata write already happened.

The datasource sends one encoded request with the existing deadline/concurrency
bounds and a 1-KiB response ceiling; only HTTP200/empty object is successful. No
application retry or transaction record is introduced. Matrix receipt writes are
idempotent, and Synapse retains forward progress when an older receipt follows a
newer one. This is read metadata, separate from durable recipient message delivery.

Classic sync requests only receipt ephemerals. Projection accepts at most32 receipt
events and256 combined event references per room; only the room's two mapped members
may expose public receipts, and private receipts expose only the requesting user.
Threaded receipts and unknown metadata are omitted. Optional timestamps must be safe
nonnegative integers; they are not an application ordering clock. Typing/presence,
account data and unrelated participants remain outside this projection.

The scope digest changes to `direct-sync-v2-receipts`. Old opaque sync positions
therefore use existing scope recovery and register the new filter. SDK keys, retained
history, durable commands and independent history cursors are unchanged.

[Evidence](../measurements/chat-read-receipts-2026-11-02.json): focused99.155s and
broad809.053s checks pass on17 unchanged inputs. Fresh focused results include55
domain/13 data/14 delivery/one HTTP case. Broad execution covers275 data/34 HTTP/117
worker cases (one existing optional elapsed-retention skip), zero failures/errors;
domain/delivery reports are reused from the focused success. Format/architecture
pass. A collector clock-origin mistake was corrected by auditing preserved report
mtimes against UTC start; no application changes or repeated test run.

Actual disposable Synapse verifies own-private/peer-public isolation, repetition
and no backward movement in7.216s. SQL/HTTP tests cover invalid body/target/auth,
foreign ownership, missing event and revocation before/after the write. The native
SDK adapter, persisted application read state, notification suppression/actions,
mute/privacy and replies still need their own acceptance. No whole PRD group is
accepted here. Current private runtime remains dac6fb0d/schema116 with chat push OFF.
