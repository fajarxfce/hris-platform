# History identity on fresh and retained devices

Native registration already supports a fresh group device for an account whose
canonical Matrix user comes from an original company. The user remains `@hris_...`,
while the fresh device is `HRISG_<group-device>`. History previously conflated these
origins and required a fresh device to have a new group user, rejecting that valid
combination even after current native/device/gateway admission.

History now first verifies the exact owned room member and canonical Matrix user
using the existing room-coordinate policy. Device origin then determines only the
device namespace: fresh devices require their exact HRISG_ ID; retained devices still
require their exact original company/device tuple and HRIS_ ID. The original room
must match the caller's canonical original user and nonzero original company.

Fresh devices can read their owned SPACE and LEGACY room content, but cannot inherit
old-device cursor authority. Original company cursor bridging still requires an exact
retained device tuple supplied after completed-transfer admission. Fresh cursors bind
the fresh group/device identity, so both old-company and old-group cursor replay fail
in either direction. No private keys, local stores or provider identities are reset.

See [accepted gate evidence](../measurements/collaboration-space-fresh-history-device-2026-11-04.json).

Actual native registration/activation/bootstrap plus admitted history is tested for
both new and original canonical users. The native test uses explicit immutable room
and provider fixtures; separate existing tests cover real PostgreSQL room ownership.
Actual Synapse covers fresh devices on both users after provider restart, original
message/sender/ciphertext/device identity, context/event reads, isolated cursors and
continued old-device access. Unsigned transaction acknowledgement data can differ
between devices and is not substituted for original event identity.

This proves admitted transport and metadata continuity, not fresh-device Megolm key
recovery or SDK decryption. Combined original/group read scope, queued original key
identity lookup, sync, metadata/push, full Mobile adoption and native hard-parent
recovery remain required. No schema, application DI graph or HTTP parser changed.
