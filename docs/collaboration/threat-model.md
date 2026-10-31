# Collaboration security and failure boundaries

This is a design checklist with required evidence, not a security-audit result.

| Boundary / attack | Required property and exercise |
| --- | --- |
| Stolen/stale session | Check current HRIS admission; revoke mappings, reject future controls and terminate affected live media. Test late retries after revocation. |
| Cross-company room/context | Server derives company membership and context scope; a forwarded resource ID conveys no access. Exercise guessed IDs and company switching during requests. |
| Matrix API bypass | Isolate administrative/login paths; check authenticated client traffic and room operations against HRIS admission. Federation/public registration remain closed. |
| Session bootstrap response loss | Retain/reconcile one bounded device operation; never create uncontrolled identities/devices on retry. Keep grants out of ordinary receipts/logs. |
| Compromised server/storage | E2EE keys stay client-side; metadata is not claimed secret. Verify stored events/files are ciphertext and diagnostics contain no plaintext/private keys. |
| Unknown/substituted device | Cross-signing/verification, visible key-change notices and explicit recovery; do not silently trust replacement keys or server bots. |
| Removed/new participant | Rotate future keys; only admitted participants receive session media keys. History handover is selected and client-side. Test offline old devices and delayed key messages. |
| Extension/tab crypto race | One supported mutation owner per store, cross-process locking where required; test simultaneous foreground/background notification work and browser tabs. |
| Lost/replayed/out-of-order event | Durable transaction IDs, recipient storage-before-delivery ACK, idempotent outcomes and explicit read commands. Test restart before/after each commit/ACK. |
| Call answer/cancel race | Authoritative versioned transition and single recipient-device winner; only winner obtains publishing rights. Stale pushes/acceptances cannot resurrect a terminal call. |
| Forged/expired push | Validate schema/owner/call identity, bounded expiry and current call state; no plaintext conversation body in provider metadata. Push is a hint, not authority. |
| Guest/seat escalation | Host admission and atomic seat/role limits precede scoped media grants. Removal/revocation prevents reconnect with old grants. |
| Private-mode downgrade | Mode immutable per occurrence; reject recorder/AI enrollment and key access. Test client/host/server attempts to switch mode mid-session. |
| Malicious attachment/sticker | Bound bytes, counts, type/dimensions/decoding; encrypt chat files separately from HRIS scanned files. No claim that ciphertext was malware-inspected. |
| Backup rollback/tampering | Authenticate chunks/manifests, check account/schema/current admission and redactions, publish atomically; wrong keys/corruption leave the old store intact. |
| Cloud/provider exposure | Client-side encryption before backup; least-privilege OAuth; private keys and login sessions do not enter backup logs. |
| Recording without notice | Authorized opt-in, persistent indicators and guest acknowledgement; status distinguishes partial/failed/available. Retention and export recheck ACLs. |
| AI prompt injection/error | Treat transcript as untrusted data, no HRIS tools/commands, bounded inputs, provenance and draft review; enforce artifact ACL on retrieval. |
| Resource exhaustion | Admission/rate limits, bounded file/media/queue work, separated pools, deadlines and backoff; test slow clients, reconnect bursts and full rooms. |
| Provider/node failure | Reconcile durable controls, expire stale leases, preserve partial recording evidence and allow media recovery without duplicate calls. |
| Data deletion/recovery | Redactions synchronize and apply after restore. Do not promise recall of plaintext already exported by recipients. |

Device compromise and recipients copying visible content are outside E2EE's
protection. OS force-stop/offline restrictions are not bypassed. iOS device
acceptance may remain explicitly waived; compilation is not provider evidence.
