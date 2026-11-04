# Organization conversation identity and original-room inventory

V129 gives direct conversations their own organization-space identity, independent
of the currently selected HR company. A conversation is an immutable, unsigned-sorted
pair of account IDs with its original creator, creation time and version zero. The
space/pair uniqueness constraint and transaction-owned pair guard let opposite
participants converge on one intent. Intent can exist before a peer initializes
Matrix; it does not represent an encrypted room or a deliverable message.

## Current membership and retained ownership

The public member view deduplicates an account's eligible company affiliations and
optionally includes its already selected canonical Matrix user. The directory's
restricted non-login owner receives only five public identity columns. Selection
does not imply provider-user existence, private-key possession, a receiving device,
or room/key-query authority. Neither the view nor repository selects another
account's identity. Missing selection remains explicit.

Only a root-scoped pair participant may read its retained group conversation.
Insertion additionally requires the current actor to be its creator and both
accounts to be currently eligible in the organization directory. Reads of retained
intent survive offboarding and ordinary native-parent purge. No update or deletion
is admitted, including by the creator; a trigger also prevents owner mutations.
Finite creation timestamps cannot be ahead of the database clock.

The use case must separately admit the current native session, client/security
policy and group, acquire operation then pair then ordered account then native
guards, and recheck both participants before publishing a result. Do not acquire
company/member guards after account/native guards. These storage ports perform no
implicit transaction, retry, native authentication or provider call. Receipt and
journal writes belong in the caller's transaction with final current admission.

## Original rooms remain explicit

The separate legacy view exposes only an eligible caller's original owned company
conversation and optional bound Matrix room metadata. Original company ID and
conversation ID are both required coordinates. No conversation, room, provider
identity, background-job request or client crypto namespace is rewritten. Parent
purge does not remove this inventory; loss of group eligibility hides the projection
without deleting the source records or granting another account access.

The non-login legacy view owner receives column-only conversation/room privileges,
with actor-owned RLS. It gains no job payload, native-session credential, provider
token or encryption-key access. Ordinary application roles cannot assume either
view-owner role. Original tables keep their company-scoped application authority.

A missing binding is retained as an unbound conversation. A present binding is
historical metadata, not an adopted room grant. Future adoption must verify the
exact original provider identities against both canonical participants, current
encryption/membership and application authority. Other historical identities stay
explicit and must not be silently merged or re-encrypted. Never route a space ID
through a company-scoped job or gateway contract.

## Typed acquisition and finite pages

The datasource performs raw jOOQ I/O. The repository validates all acquired metadata,
including nullable identity/room tuples, original scope/ownership, immutable pair
shape, version zero and finite microsecond times. Insert requires exact readback in
the same safe failure boundary. Mapping failure, failed results, cancellation and
interruption roll back the caller's transaction rather than publish false success.

Both inventories accept 1–50 rows with at most one extra row. Group cursors order
unsigned conversation IDs; legacy cursors order original company then conversation
IDs. Every fetched row, including overfetch, is validated for scope and strict
progress. Duplicate, reversed, oversized or stale pages fail as a whole. A cursor
is returned only when the extra row exists; the final and empty pages have none.
These are metadata discovery pages, not Matrix timeline or recent-chat ordering.
Newly inserted IDs before an existing cursor appear on a new traversal.

Recovery schema 129 retains the V126 encrypted-envelope projection unchanged and
rejects unknown schema 130. Archive restoration must preserve group pairs, original
bound rooms, restricted view ownership, RLS and immutable triggers together.

Native open/read/list commands, explicit room adoption/provisioning, recipient key
query/claim/to-device access and content/push routing remain required integration.
An offline recipient must not require an unexpired short native/provider token to
retain its public-key availability. Group remains disabled with no Mobile product
caller or live rollout from this storage increment.


[Accepted evidence](../measurements/collaboration-space-conversation-storage-2026-11-04.json)
records all86 database and6 typed PostgreSQL cases passing in 838.663s on 8,360
unchanged inputs with format/architecture and19 recovery checks. Actual concurrent
openers, transaction rollback, finite pages, view privileges, original bound-room
publication guards, parent purge and archive restoration are covered. No API, provider
room adoption or product rollout is claimed by these storage checks.
