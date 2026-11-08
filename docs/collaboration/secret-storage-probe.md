# Matrix secret-storage discovery

The native SDK checks its own `m.secret_storage.default_key` account-data event
while initializing encryption recovery. The HRIS gateway previously rejected the
route with `403 M_FORBIDDEN`, even when the provider simply had no recovery setup.

The gateway now supports this exact GET for the current native device's Matrix
user. Admission checks the current account, company, membership, native session,
device, client policy and gateway credential. A peer or another company's user
cannot be queried. Provider I/O occurs outside the admission transaction; live
authority is checked again before publishing either content or an absent result.

The datasource uses the existing fixed-origin, bounded, cancellable HTTP owner.
It reads at most 4 KiB, does not redirect or retry, and recognizes absence only
from an exact `404 M_NOT_FOUND`. An empty event remains `{}`; a configured event
returns only its bounded public key identifier. Malformed content, authorization
failures, limits and transport errors do not become fabricated absence. No
encryption private key, secret content or caller-supplied account-data type is
read or written. Other secret-storage routes remain unavailable.

This read is compatibility work, not implementation of backup/recovery. It does
not change recipient eligibility or acknowledge messages. The reported pending
messages and repeated requests require separate device observation: recent logs
also contain `room_send` rejections for unavailable recipients. Bima's original
enrolled device and published key remain eligible while its emulator is stopped.
See [investigation](../measurements/matrix-secret-storage-investigation-2026-11-08.json).
