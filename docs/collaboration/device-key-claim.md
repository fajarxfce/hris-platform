# Native Matrix one-time key claim

`POST /_matrix/client/v3/keys/claim` uses the same opaque native HRIS gateway
bearer and bounded strict JSON reader as [key query](device-key-query.md).
The only request fields are `one_time_keys` and optional integral `timeout`
(0–30,000 ms). Each of 1–100 canonical company/server users selects 1–5 explicit
HRIS device IDs with algorithm `signed_curve25519`. Wildcards, unsigned algorithms,
nulls, duplicate fields, trailing input and URL credentials are rejected.

Current self/direct account authority and confirmed recipient devices determine
the actual selection. Unknown, unpublished, removed or inaccessible devices
produce empty entries without provider consumption. Short access/MFA freshness
of an offline peer is not its hard device eligibility. This selection does not
add browser-parent, group or resource-context authority.

Before consuming keys, a fixed explicit provider key query verifies each eligible
device's canonical self signature and immutable HRIS fingerprint. The subsequent
fixed claim accepts at most one signed key per requested device. Its signature
must verify against that retained device identity. The signed `fallback` field is
preserved exactly: missing, false and true are distinct canonical signed objects.
Missing/exhausted keys remain empty rather than inventing a key or successful
message delivery. Foreign users/devices, malformed keys, multiple keys per device,
nonempty provider failures and substituted signatures invalidate the response.

Each provider exchange separately checks current caller admission and the selected
recipient set before and after I/O. These checks include native parent/security,
company/member/CHAT policy, device, gateway head and captured deadline. The shared
ordered account guards are released before network/signature work. A current
caller denial or changed recipient set wins over a late provider failure/result.
No key result survives a failed completion check.

A claim **consumes a provider one-time key**. A lost response or revoked admission
after consumption cannot undo that effect. The gateway does not add an automatic
retry, replay receipt, provider logout or key-store reset. A fresh SDK claim may
consume the next key; once ordinary keys are exhausted, the provider may return
the retained signed fallback key. This behavior needs actual provider evidence,
not an assumption that a POST can safely replay the same consumed key.

Requests remain bounded at 64 KiB. Provider query and claim responses are bounded
at one MiB; fixed origin/no redirects, five-second HTTP timeout, shorter captured
deadline and 32 immediate transport permits remain shared. The provider advisory
timeout is 1,000 ms. These limits are not a large-group throughput guarantee.

Responses expose only validated public `one_time_keys` and empty `failures` with
`Cache-Control: no-store`. Participant private keys remain in SDK stores. Claim
and device discovery do not establish to-device delivery, scoped sync/history,
durable outbox, recipient decryption or product chat acceptance.
