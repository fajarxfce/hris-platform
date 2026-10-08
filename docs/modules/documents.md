# Documents

Own company-scoped metadata, document revisions, upload state, validation, retention, and private content access.

Documents identify owner, category, classification, business resource, and version. Use resumable uploads; validate size/type/content before ready. Authenticate every read and support bounded Range/ETag streaming.

Garbage collection expires abandoned uploads/temporary exports without deleting retained business evidence. Technical processing failure is inspectable and never silently publishes an unvalidated document.

Screens: employee documents, upload progress, revision history, processing status.

Acceptance: offset/lost-response recovery, wrong version/range, permission revocation, cross-company IDs, size limits, interrupted streaming, and finalization replay.

## Resumable upload API

The upload/status/cancellation API is implemented. Validation, publication, and downloads are the next slice; uploading all bytes does not make a document readable.

Company routes use `/api/v1/companies/{companyId}/documents`. `POST /uploads` accepts stable document/revision UUIDs, employment ID, title, classification, expected document version, file name, media type, size, SHA-256, and reason. Use `Idempotency-Key` for every command. Metadata/history use `GET /{documentId}`, `GET /{documentId}/revisions`, and `GET /revisions/{revisionId}`. Lists require an `employmentId` and support bounded cursors.

Send `POST /revisions/{revisionId}/chunks` as `application/octet-stream` with `Upload-Offset`, `Upload-Checksum-Sha256` (lowercase hex), and a stable idempotency UUID for that chunk. Chunks are exactly 1 MiB except the final chunk. After a lost response, repeat the same chunk/key or fetch `uploadedBytes`; the original receipt remains stable even after later chunks commit. A mismatched offset or command payload returns a conflict. An active attempt returns `document_chunk_in_progress` with `fields.retryAt`. A failed attempt can be reclaimed after its 120-second lease using the same operation ID. Eight unsuccessful attempts require cancelling and starting a new revision; the server does not retry uploads silently.

File declarations are restricted to PDF, JPEG, and PNG, with a matching extension, safe file name, expected digest, and a 100 MiB maximum. An upload expires after 24 hours. Limits are ten active uploads per actor, 100 per company, 100 revisions per document, 10,000 document headers, and a 10 GiB company reservation budget. Every physical attempt counts toward the budget until cleanup acknowledges deletion. Four binary requests can run concurrently per API instance. Both declared and streamed bodies are bounded before allocation; body progress cannot extend the 30-second read budget, and socket inactivity is limited to five seconds.

`POST /revisions/{revisionId}/cancel` takes an expected version and reason. Cancellation prevents a pending writer from committing and schedules temporary objects for deletion after a five-minute grace period. Starting a replacement closes an expired upload without rewriting its metadata/history. Every write attempt has a different storage key; registration precedes external I/O, and progress, command receipts, and audit commit atomically afterwards. File I/O holds no database transaction. See [temporary-object cleanup](../storage.md#temporary-object-cleanup).

Generic reads require `documents.read` and `people.profile.read`; writes require `documents.manage` and `people.profile.manage`. Self-service uses `documents.self.read` / `documents.self.upload` for the account linked to the employment. `HR_ONLY` documents are excluded from self-service. Finance access to claim receipts belongs to the expense workflow's resource authorization. Current credentials, membership, and grants are checked again before accepting pending writes. Responses contain metadata, never physical storage keys.

## Content inspection adapter

The content inspection adapter combines private storage chunks, Apache Tika 4.1 type detection, and ClamAV's INSTREAM protocol. It reports observed size, whole-file SHA-256, media type, scanner version, and verdict. The domain workflow decides whether that evidence permits publication. A clean scanner response alone does not establish a matching document type or digest.

Set `HRIS_DOCUMENT_SCANNER_ENABLED=true`, `HRIS_DOCUMENT_SCANNER_HOST`, and `HRIS_DOCUMENT_SCANNER_PORT` (default 3310) for a managed private ClamAV endpoint. The protocol is unencrypted and unauthenticated; keep that endpoint on the private service network. It must not be exposed as a public upload API. Disabled/unavailable configuration cannot return a successful inspection.

The adapter permits two sessions per process without a waiting queue, checks at most 100 one-MiB parts, and sends them incrementally. Detection uses at most 64 KiB. Connections allow three seconds to connect, ten seconds per write, seventy seconds for a final scanner response, and 120 seconds for the complete inspection. Response frames are limited to four KiB. Nonblocking socket/selector loops check deadlines and interruption; every session, permit, stream, and selector has an owner. Existing storage failures retain their classification even if scanner cleanup also fails.

Configure the daemon for at least the document limit (`StreamMaxLength 105M`, `MaxFileSize 105M`), finite scan/archive limits, and `AlertExceedsMax yes`. Enable encrypted-document alerts if those files cannot be inspected under the organization's policy. Keep signature databases current and persistent. Integration tests use ClamAV 1.5.4 with a deliberately minimal, owned EICAR signature database; they do not establish production signature coverage or update operations. Durable validation/publication is tracked in the next slice.

References: [ClamAV releases](https://github.com/Cisco-Talos/clamav/releases), [ClamAV Docker configuration](https://github.com/Cisco-Talos/clamav-docker/blob/main/clamav/README-alpine.md), [clamd protocol](https://docs.clamav.net/manual/Usage/Scanning.html#clamd), [Apache Tika](https://tika.apache.org/).
