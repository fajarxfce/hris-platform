# Documents

Own company-scoped metadata, document revisions, upload state, validation, retention, and private content access.

Documents identify owner, category, classification, business resource, and version. Use resumable uploads; validate size/type/content before ready. Authenticate every read and support bounded Range/ETag streaming.

Garbage collection expires abandoned uploads/temporary exports without deleting retained business evidence. Technical processing failure is inspectable and never silently publishes an unvalidated document.

Screens: employee documents, upload progress, revision history, processing status.

Acceptance: offset/lost-response recovery, wrong version/range, permission revocation, cross-company IDs, size limits, interrupted streaming, and finalization replay.

## Resumable upload API

The upload/status/cancellation and durable validation APIs are implemented. Uploading all bytes does not publish a revision; only a completed, accepted inspection sets `READY`. Downloads are the next slice.

Company routes use `/api/v1/companies/{companyId}/documents`. `POST /uploads` accepts stable document/revision UUIDs, employment ID, title, classification, expected document version, file name, media type, size, SHA-256, and reason. Use `Idempotency-Key` for every command. Metadata/history use `GET /{documentId}`, `GET /{documentId}/revisions`, and `GET /revisions/{revisionId}`. Lists require an `employmentId` and support bounded cursors.

Send `POST /revisions/{revisionId}/chunks` as `application/octet-stream` with `Upload-Offset`, `Upload-Checksum-Sha256` (lowercase hex), and a stable idempotency UUID for that chunk. Chunks are exactly 1 MiB except the final chunk. After a lost response, repeat the same chunk/key or fetch `uploadedBytes`; the original receipt remains stable even after later chunks commit. A mismatched offset or command payload returns a conflict. An active attempt returns `document_chunk_in_progress` with `fields.retryAt`. A failed attempt can be reclaimed after its 120-second lease using the same operation ID. Eight unsuccessful attempts require cancelling and starting a new revision; the server does not retry uploads silently.

File declarations are restricted to PDF, JPEG, and PNG, with a matching extension, safe file name, expected digest, and a 100 MiB maximum. An upload expires after 24 hours. Limits are ten active uploads per actor, 100 per company, 100 revisions per document, 10,000 document headers, and a 10 GiB company reservation budget. Every physical attempt counts toward the budget until cleanup acknowledges deletion. Four binary requests can run concurrently per API instance. Both declared and streamed bodies are bounded before allocation; body progress cannot extend the 30-second read budget, and socket inactivity is limited to five seconds.

`POST /revisions/{revisionId}/cancel` takes an expected version and reason. Cancellation prevents a pending writer from committing and schedules temporary objects for deletion after a five-minute grace period. Starting a replacement closes an expired upload without rewriting its metadata/history. Every write attempt has a different storage key; registration precedes external I/O, and progress, command receipts, and audit commit atomically afterwards. File I/O holds no database transaction. See [temporary-object cleanup](../storage.md#temporary-object-cleanup).

Generic reads require `documents.read` and `people.profile.read`; writes require `documents.manage` and `people.profile.manage`. Self-service uses `documents.self.read` / `documents.self.upload` for the account linked to the employment. `HR_ONLY` documents are excluded from self-service. Finance access to claim receipts belongs to the expense workflow's resource authorization. Current credentials, membership, and grants are checked again before accepting pending writes. Responses contain metadata, never physical storage keys.

## Content inspection adapter

The content inspection adapter combines private storage chunks, Apache Tika 4.1 type detection, and ClamAV's INSTREAM protocol. It reports observed size, whole-file SHA-256, media type, scanner version, and verdict. The domain workflow decides whether that evidence permits publication. A clean scanner response alone does not establish a matching document type or digest.

Set `HRIS_DOCUMENT_SCANNER_ENABLED=true`, `HRIS_DOCUMENT_SCANNER_HOST`, and `HRIS_DOCUMENT_SCANNER_PORT` (default 3310) for a managed private ClamAV endpoint. The protocol is unencrypted and unauthenticated; keep that endpoint on the private service network. It must not be exposed as a public upload API. Disabled/unavailable configuration cannot return a successful inspection.

The adapter permits two sessions per process without a waiting queue, checks at most 100 one-MiB parts, and sends them incrementally. Detection uses at most 64 KiB. Connections allow three seconds to connect, ten seconds per write, seventy seconds for a final scanner response, and 120 seconds for the complete inspection. Response frames are limited to four KiB. Nonblocking socket/selector loops check deadlines and interruption; every session, permit, stream, and selector has an owner. Existing storage failures retain their classification even if scanner cleanup also fails.

Configure the daemon for at least the document limit (`StreamMaxLength 105M`, `MaxFileSize 105M`), finite scan/archive limits, and `AlertExceedsMax yes`. Enable encrypted-document alerts if those files cannot be inspected under the organization's policy. Keep signature databases current and persistent. Integration tests use ClamAV 1.5.4 with a deliberately minimal, owned EICAR signature database; they do not establish production signature coverage or update operations. The worker runs this adapter through the validation workflow described below.

References: [ClamAV releases](https://github.com/Cisco-Talos/clamav/releases), [ClamAV Docker configuration](https://github.com/Cisco-Talos/clamav-docker/blob/main/clamav/README-alpine.md), [clamd protocol](https://docs.clamav.net/manual/Usage/Scanning.html#clamd), [Apache Tika](https://tika.apache.org/).

## Validation and publication

After every chunk is committed, call `POST /revisions/{revisionId}/validate` with `expectedVersion`, a reason, and an idempotency key. The response is the revision mutation receipt; its status response exposes `validationJobId`. Observe that job through the existing company job API. `GET /revisions/{revisionId}/validation-attempts` returns the bounded, immutable attempt history to authorized document readers.

The worker checks current credentials, company membership, document permission, upload expiry, and its lease before and after storage/scanner I/O. It performs no external I/O inside a database transaction. A complete manifest, matching whole-file size/SHA-256/type, and a clean verdict are required. An accepted revision becomes `READY` and advances the document's `currentRevisionId`. A rejected revision becomes `REJECTED` with a stable failure code; the previous ready revision remains current. The job succeeds when inspection reaches either verdict. Technical processing failures use `VALIDATION_FAILED` and remain unavailable for content access.

Accepted object retention, inspection evidence, publication, job checkpoint/completion, and audit commit together. Losing any accepted cleanup registration fails the entire publication. Ready revisions remain immutable and count against company storage quota even after their temporary cleanup rows are removed. Cancelled, expired, and rejected revisions cannot become ready.

Transient infrastructure failures use the existing job retry policy with at most eight lease attempts. An operator can explicitly call `/validate` again after the prior job failed or was cancelled; a revision allows at most eight validation jobs. Crashed/exhausted jobs are projected as failed processing without rewriting their history. An expired upload requires a new revision. Cancelling the upload also requests cancellation of its active validation job. Late results and stale leases cannot publish; a worker can finalize cleanup even after the initiating account loses access.
