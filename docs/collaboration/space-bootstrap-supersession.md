# Explicit bootstrap replacement — design under validation

Implementation and acceptance are pending. This records the next owning-domain
change; it does not activate organization chat or establish phone-send recovery.

An original bootstrap can remain uncertain after its 30-second credential replay
expires, or after an application update changes the actual client build. A status
read alone cannot exclude a late original request. Recovery must retain the original
intent and a distinct replacement intent, and establish that the original can no
longer publish credentials after the replacement commits.

## Monotonic gateway fence

V126 already provides the necessary durable fence: one gateway ID per device, an
immutable original native parent, strictly increasing versions, terminal retirement,
and prohibited deletion/revival. Bootstrap takes account/native/device/gateway guards,
requires the exact observed gateway version, then creates version zero or advances
exactly once. An original receipt can replay only while its exact gateway version
is current. No new schema or invented receipt for the original is required.

Represent an absent expected gateway as version -1 for comparison only. A replacement
must retain a flat descriptor of the superseded attempt: operation UUID, original
space/account/native-parent/device coordinates, observed device/gateway revisions,
and original platform/build. Its current observed gateway revision must be at least
the original expectation. It must use a distinct operation UUID and current actual
client headers. Original build metadata is a historical fact, never a forged header
or an admission grant. Both attempts remain within the same native parent and SDK
identity; hard-parent recovery is separate.

The replacement commits a version strictly greater than the superseded expectation.
If the original wins the guarded transaction first, a stale replacement observation
fails and must be reconciled explicitly. If the replacement wins first, a late original
fails its original version comparison. If the original already committed, a successful
replacement advances beyond its receipt and closes original credential replay. The
gateway cannot later return to an absent or earlier head. Refreshes and competing
replacements share that same version fence; only the exact observed winner commits.

The response proves supersession of the described attempt, not whether it historically
applied. It must never report a fabricated original acknowledgment. An attempt that was
itself a replacement is described by its own effect coordinates, so repeated lost
responses do not require an unbounded recursively nested request. Its previous link
remains in the original durable operation payload and client record.

The response includes the full superseded effect descriptor. An operation UUID alone
is insufficient evidence: no claim covers a different request with different device
or expected-version coordinates. Mobile must compare every returned field against its
immutable original intent before closing that uncertainty. The server neither asserts
that an absent original ever existed nor changes its receipt. This requires no original
operation lookup, and no credentials or authority outside current normal bootstrap.

## Ownership and persistence

The existing bootstrap use case owns issuance and current admission; its explicit
replacement input adds a pure supersession policy and binds the descriptor into the
new operation payload. Ordinary bootstrap fingerprints remain unchanged. HTTP only
maps the separate replacement request and response. The repository continues to
acquire/store credentials and command receipts; neither it nor a ViewModel decides
when replacement is allowed. There is no second original-operation lock under an
account guard and no additional database lock ordering.

The new receipt, credentials and journal entry commit atomically. A received replacement
must match the retained original descriptor and new operation before local publication.
Mobile must persist the linked replacement intent before HTTP and retain uncertainty
if response persistence fails. Closing the original and publishing replacement
credentials require one durable local transition; expiry during that write requires
fresh authority and cannot erase either fact. An expired replacement can itself be
superseded using a new durable intent and current observed revision.

## Required evidence

- Pure bounds, same-parent/identity/platform matching, distinct operation IDs,
  older actual-build metadata and monotonic revision policy.
- Actual PostgreSQL races: original before replacement, original blocked before the
  account guard while replacement wins, and competing replacements/refreshes.
- Never-applied original with an initially absent gateway; applied original after
  credential replay cleanup; repeated lost responses and later actual builds.
- Original replay after replacement cannot issue or return credentials; exact new
  retry returns its original pair only inside the existing replay window.
- Current admission, offboarding, original hard deadlines, cancellation, clock bounds,
  malformed credential readback and journal/receipt failure retain rollback behavior.
- Actual API mapping, normal bootstrap compatibility and complete worker context.
- Mobile durable link/CAS, process interruption, old-account disposal and original SDK
  namespace before any product activation or claim of end-to-end adoption.
