# Original owned-room inventory for group reads

The existing V129/V130 projection now has two explicit original-namespace operations:
a bounded room list and an exact provider-room lookup. Both require space, account,
original company and Matrix server coordinates. They read original conversation and
binding/actual creator metadata, without inventing group bindings or granting current
native/device/provider authority.

The list acquires only bound rooms in deterministic unsigned conversation order and
accepts 1–10,001 rows for bounded overfetch. The exact room lookup acquires at most two
rows and rejects ambiguity. Typed mapping validates every acquired row, including
company/server/participant ownership, complete room metadata, ordering and unique room
IDs. Oversized, malformed, duplicate and foreign rows fail closed; neither operation
returns a partial scope. Acquisition and mapping share the database failure boundary;
cancellation and interruption propagate.

The existing restricted view preserves participant ownership independently of peer
migration, short-lived credentials or current peer eligibility. HR company selection
does not rewrite the original namespace. Caller eligibility and completed LEGACY
native/device proof remain separate use-case admission. Other accounts cannot read
these rows by substituting an owner's account ID. No migration, broad privilege,
new DI graph or provider request is introduced.

See [accepted gate evidence](../measurements/collaboration-space-original-room-inventory-2026-11-04.json).

Next, combine original and published group room metadata under a currently admitted
owner. Exact overlaps must agree on original company/conversation, members, room and
actual creator; no fake group lease/publication fields may be synthesized. The caller
keeps its original Matrix identity, while a peer can have different old and current
aliases in different rooms. Before provider sync advances, retain queued original
to-device frames and prove their identity lookup/decryption with the actual SDK.
Current group recipient query/claim is not historical peer-key authority, and owned
history must not grant new sends or OTK claims to obsolete devices. Group sync,
retained-history fallback, metadata/push and full Mobile adoption remain required.
