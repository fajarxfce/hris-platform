# Private object storage

`core/storage` provides a framework-free repository contract and an S3-compatible adapter. It supports bounded binary writes, metadata, exact conditional ranges, and idempotent deletion. Feature use cases remain responsible for company/resource authorization, upload lifecycle, validation, retention, and publication. The document API is tracked separately in [delivery](delivery.md).

Objects use opaque server-assigned keys. The adapter uses path-style requests, fixed-length SigV4 uploads, and SHA-256 metadata. Garage 2.4.1 was tested directly: it accepts ordinary writes but ignores `If-None-Match` on PUT. Feature code must allocate a different key for each write attempt and retain only the accepted key in its transaction. A preliminary HEAD check does not make a later write atomic. Reads verify the observed ETag, Content-Range, and exact byte count.

Each object is limited to 5 MiB and eight concurrent calls per client, without a waiting queue. Large documents will use multiple bounded objects. Connections have a three-second connection timeout and eight-second socket timeout; requests have a twenty-second budget and one SDK attempt. Body consumption stays inside the SDK call and has its own progress/deadline checks, so a trickling response cannot extend a read indefinitely. Buffers, streams, permits, and SDK clients have explicit owners. Cancellation and interruption propagate; transport details, keys, credentials, and raw provider messages are excluded from application diagnostics.

The data module supplies Spring configuration for consumers that compose it. Enable it with `HRIS_STORAGE_ENABLED=true` and configure `HRIS_STORAGE_ENDPOINT`, `HRIS_STORAGE_REGION` (default `garage`), `HRIS_STORAGE_BUCKET`, `HRIS_STORAGE_ACCESS_KEY`, and `HRIS_STORAGE_SECRET_KEY`. HTTPS is required unless `HRIS_STORAGE_ALLOW_PLAINTEXT=true` explicitly allows a trusted internal endpoint. Endpoint user information, paths, queries, and fragments are rejected. Disabled configuration fails with a typed unavailable result. This boundary does not create buckets or grant public access.

Use private bucket credentials restricted to the intended bucket. Encryption of storage volumes, backups, and production key management belongs to deployment configuration. No real document or cloud account is used by the tests.

References: [Garage quick start](https://garagehq.deuxfleurs.fr/documentation/quick-start/), [Garage S3 compatibility](https://garagehq.deuxfleurs.fr/documentation/reference-manual/s3-compatibility/).
