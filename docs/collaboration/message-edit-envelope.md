# Encrypted message replacement envelope

The native Matrix gateway accepts an optional exact `m.relates_to` object with
`rel_type: m.replace` and `event_id`. The target uses the existing event-ID bound:
2–255 printable ASCII characters, starting with `$`, without spaces. A replacement
and a quoted reply cannot share the same outer relation. Mixed shapes, unknown
fields, explicit nulls and plaintext `m.new_content` are rejected before provider
access. Ordinary messages and quoted replies retain their existing wire shapes.

Only the opaque target reference leaves the encrypted payload. The replacement's
new text, logical message ID, operation ID and reply/forward context belong inside
Megolm ciphertext. The gateway does not fetch the target, grant target access, or
claim to enforce policy that depends on decrypted contents. Native session,
device, room/participant and sender-key admission still apply, including admission
after a provider response. Clients separately admit the original sender, target,
content and edit window before changing their local display revision.

The provider receives the exact relation without additional fields. A stable
transaction ID deduplicates replacement publication across repeated requests and
same-device bearer rotation. Native revocation while the provider is responding
prevents disclosure of the returned acknowledgement.

[Acceptance evidence](../measurements/collaboration-edit-envelope-2026-11-05.json)
records 166 accepted cases across 30 suites, zero failures/errors and one existing
opt-in elapsed-retention skip. The final gate ran 157 fresh cases in 409.09 seconds;
nine domain/parser/mapping/Synapse cases remained unchanged and up-to-date from
the first attempt. Both report hashes and source manifests were verified. The
first two attempts exposed only omissions in the newly extended HTTP fixture:
its edit route and request-count expectations. Those corrections are included.
Worker tests, formatting and architecture checks passed.

The real Synapse fixture checks event identity, transaction deduplication, bearer
rotation and peer-history ciphertext/relation. Its ciphertext is synthetic; it
does not establish actual SDK edit encryption/decryption, optimistic authoring,
offline recovery or a delivered product feature. Full release CI, Mobile edit
authoring/reception proof and the separate group-branch port remain. No schema,
provider configuration or live runtime change is part of this commit.
