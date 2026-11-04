# Organization direct-room sync scope

The bounded owned-room query reads existing V131 intent and binding rows in one
PostgreSQL statement under participant root RLS. It requires explicit space, account
and server coordinates, orders conversations deterministically and returns at most
the requested 1–10,001 rows. The extra row lets the caller reject capacity rather
than silently returning an incomplete scope. No schema or broad cross-company view
is introduced. Original exact room lookup keeps its behavior.

Typed mapping checks the acquired bound, original participants, server, ordering and
unique room IDs inside the shared database failure boundary. Cancellation and
interruption propagate. It does not grant current session or provider authority.

The pure DIRECT snapshot supports at most 10,000 rooms and verifies each immutable
intent/binding with the existing room policy. Duplicate conversations/rooms, invalid
or future metadata, foreign ownership and inconsistent canonical provider identities
fail closed. An empty valid scope is permitted. Its detached sorted list and separate
organization digest retain original Matrix users, actual creator, room identity and
LEGACY provenance. The digest includes immutable publication coordinates; input
ordering cannot change it. Company sync scopes remain distinct.

Owned scope never joins current peer permissions or short-lived receiving credentials.
A peer going offline or being offboarded therefore cannot remove retained room
history. Sending still needs its separate current recipient admission. This scope
must not be reused for removable group/context membership without its own policy.

See [accepted gate evidence](../measurements/collaboration-space-room-sync-scope-2026-11-04.json).

Next: separate group sync cursor/filter semantics, raw provider projection using
original user/device/member/creator coordinates, current native/device admission
around both outcomes, and public fixed sync dispatch. Original company cursor
migration requires explicit completed-device proof and a new group scope/filter;
room-history cursor bridging does not imply sync bridging. Full Mobile adoption,
original SDK-store retention, push and native hard-parent recovery remain required.
This increment introduces no DI graph change, provider sync dispatch or rollout.
