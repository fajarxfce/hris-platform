# Original-device publication preflight

Before an existing private SDK store is durably assigned to an organization device,
Mobile must verify that the provider still publishes that exact original identity.
`POST /api/v1/collaboration/spaces/{spaceId}/native-device/legacy-runtime/publication`
accepts `deviceId`, `expectedDeviceVersion` (0–999998), and the lowercase64-character
`fingerprint` of the retained original device. It is a fresh observation and does
not require an idempotency key. Freeze/adoption commands remain separate.

The use case admits the current native account, client build, organization access,
MFA proof and exact selected PROVISIONING legacy device. Existing immutable original
parent provenance must match. An absent/LEGACY runtime is allowed, as is the already
frozen exact target. A revoked/different parent or target never gains authority.

Provider login and own-device key reading occur outside SQL transactions. Complete
current admission and original identity/device/runtime snapshots are rechecked after
both provider outcomes, including failure, and once more after signature validation.
The operation retains its original30-second deadline, intersected with current access
and candidate expiry. Candidate credentials remain private, bounded and canonical.
No losing same-device login is logged out: provider logout could remove winning keys.

The response contains only `spaceId`, `deviceId`, `deviceVersion`, original company/
device coordinates, fingerprint, `observedAt` and `expiresAt`, with private/no-store
cache headers. It is not proof of private-key possession or an authorization token.
It uploads no keys, claims no one-time keys, registers no device, writes no runtime,
freezes nothing and activates nothing. It also does not retire an old SDK queue.
Mobile must independently possess the original private identity, stop/join its owner,
stage the exact target and perform admitted freeze/drain/adoption. A fresh preflight
cannot replace those later checks. Hard-parent recovery remains separate work.

Real PostgreSQL tests cover reserved-but-actually-published keys, exact frozen replay,
wrong actor/build/version/target, revoked admission during successful and failed I/O,
concurrent freeze, invalid/missing/signature-mismatched keys, invalid credentials,
original deadline and interruption. HTTP tests cover current transport/payload
admission, provider outage with no freeze, disabled mode and the OpenAPI contract.
Shared DI is tested in both API and complete worker contexts. Exact results and any
setup failures belong in the matching measurement record; the feature stays disabled
until the owning Mobile migration and rollout acceptance are complete.

Accepted local evidence:186 domain/data cases in81.721s and236 API/full-worker cases
in552.463s, zero failures/errors, one existing elapsed-retention opt-in skip. Format
and architecture pass;8,613 final inputs remained unchanged. Initial fixture compile
and cookie-CSRF expectation failures are retained in the
[measurement](../measurements/collaboration-legacy-publication-preflight-2026-11-07.json).
No feature activation or live data change follows from this gate.
