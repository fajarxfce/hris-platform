# Private object storage

`core/storage` provides a framework-free repository contract and an S3-compatible adapter. It supports bounded binary writes, metadata, exact conditional ranges, and idempotent deletion. Feature use cases remain responsible for company/resource authorization, upload lifecycle, validation, retention, and publication. The document API is tracked separately in [delivery](delivery.md).

Objects use opaque server-assigned keys. The adapter uses path-style requests, fixed-length SigV4 uploads, and SHA-256 metadata. Garage 2.4.1 was tested directly: it accepts ordinary writes but ignores `If-None-Match` on PUT. Feature code must allocate a different key for each write attempt and retain only the accepted key in its transaction. A preliminary HEAD check does not make a later write atomic. Reads verify the observed ETag, Content-Range, and exact byte count.

Each object is limited to 5 MiB and eight concurrent calls per client, without a waiting queue. Large documents use multiple bounded objects. Connections have a three-second connection timeout and eight-second socket timeout; requests have a twenty-second budget and one SDK attempt. Body consumption stays inside the SDK call and has its own progress/deadline checks, so a trickling response cannot extend a read indefinitely. Failed or cancelled body reads abort the Apache 5 connection before stream closure, preventing SDK draining from extending the deadline. Its connection pool has eight slots, a 100 ms acquisition limit, bounded lifetime/idle age, and no background reaper. Buffers, streams, permits, and SDK clients have explicit owners. Cancellation and interruption propagate; transport details, keys, credentials, and raw provider messages are excluded from application diagnostics.

The data module supplies Spring configuration for consumers that compose it. Enable it with `HRIS_STORAGE_ENABLED=true` and configure `HRIS_STORAGE_ENDPOINT`, `HRIS_STORAGE_REGION` (default `garage`), `HRIS_STORAGE_BUCKET`, `HRIS_STORAGE_ACCESS_KEY`, and `HRIS_STORAGE_SECRET_KEY`. HTTPS is required unless `HRIS_STORAGE_ALLOW_PLAINTEXT=true` explicitly allows a trusted internal endpoint. Endpoint user information, paths, queries, and fragments are rejected. Disabled configuration fails with a typed unavailable result. This boundary does not create buckets or grant public access.

Use private bucket credentials restricted to the intended bucket. Encryption of storage volumes, backups, and production key management belongs to deployment configuration. No real document or cloud account is used by the tests.

References: [Garage quick start](https://garagehq.deuxfleurs.fr/documentation/quick-start/), [Garage S3 compatibility](https://garagehq.deuxfleurs.fr/documentation/reference-manual/s3-compatibility/).

## Private document services

`compose.documents.yml` adds Garage 2.4.1 and ClamAV 1.5.4 to the base deployment. Garage runs as a single node with a private default bucket. The scanner and signature updater run as separate foreground processes, so a process exit is visible to the container restart policy. Their ports are available only on the Compose network. The API and worker use that trusted internal network; public access still goes through authorized document endpoints.

Before enabling the add-on, set `HRIS_GARAGE_RPC_SECRET` and `HRIS_STORAGE_SECRET_KEY` to separately generated 32-byte hexadecimal values (`openssl rand -hex 32`). Set `HRIS_STORAGE_ACCESS_KEY` to `GK` followed by a separately generated 16-byte hexadecimal value, and choose `HRIS_STORAGE_BUCKET`. Keep these values in the ignored `.env` or a deployment secret manager. After building both application jars, the deployment command is:

```sh
docker compose -f compose.yml -f compose.documents.yml up --build -d
```

Use both files for subsequent Compose operations. The add-on explicitly enables storage and scanning for the API and worker. It initializes the configured bucket/key only when absent; changing environment values is not a credential-rotation procedure for existing storage. Rotate an existing key through Garage administration and update consumers deliberately. The single-node configuration provides no storage replication or high availability.

The initial signature download requires external DNS/HTTPS access and can take several minutes. The scanner waits for the main and daily databases, then checks for updated databases every minute. Freshclam checks for updates twelve times per day. Its health check proves that database files exist, not that they are recent; monitor update logs and signature age. A failed or unavailable scanner leaves document validation pending/retryable or failed according to its bounded job policy. It cannot publish an unchecked file.

Both ClamAV containers use a non-root UID, a read-only root filesystem, and no Linux capabilities. The scanner permits two scans and a four-command queue, with a 60-second scan budget, bounded archive expansion/recursion, and alerts for encrypted content or exceeded limits. Its temporary filesystem is capped at 768 MiB and its container at 3 GiB; the updater is capped at 2 GiB to accommodate signature validation. Garage is capped at 1 GiB. These are configured ceilings, not measured maximum-file or full-signature memory requirements. Load-test the intended workload before adjusting them.

Persist and back up PostgreSQL, Garage metadata, and Garage content as one consistent recovery set, with the necessary credentials stored separately and protected. Signature files use a separate persistent volume and can be downloaded again. Do not remove volumes during an upgrade. Document retention rules and storage inventory reconciliation are tracked separately from temporary-object cleanup.

`python3 tool/check_document_services.py` validates the merged configuration and starts only uniquely labelled temporary test containers. It checks Garage bucket initialization, the exact configured health commands, and clean/EICAR scan results using an owned minimal signature database. It then removes its own containers. It does not start the deployment, contact a real bucket, or download production signature databases.

## Temporary-object cleanup

The API and worker now compose the storage module. `object_cleanup_queue` records each temporary key, expected byte count, owning company/resource, and earliest deletion time. Features must register keys before external writes, retain the accepted keys in the publication transaction, and leave rejected/abandoned attempts scheduled for deletion. Accepted keys are removed from the queue only while unleased; publication must reject an already claimed or expired candidate. This infrastructure does not infer document retention policy.

A worker-only database function claims bounded leases without exposing company business tables. Claims allow four active deletions across the deployment and two per company, with a bounded scan. One lifecycle-owned poller processes at most two objects per pass. Physical S3 deletion runs outside SQL transactions; acknowledgement and audit commit together. A lost acknowledgement may repeat deletion, which is idempotent. Expired tokens cannot acknowledge another worker's lease. Cleanup remains possible after the uploader loses access.

Unavailable storage backs off for five to 300 seconds and stops after eight attempts. Other failure categories stop for review. Repeated process crashes also produce an audited failed entry. `GET /companies/{companyId}/storage-cleanup` requires `jobs.read` and uses bounded pagination; it excludes storage keys. `POST .../{id}/retry` requires `jobs.retry`, an expected version, reason, and idempotency key. Retry is explicit and audited. Shutdown cancels pending work and releases the owned polling timer; outstanding keys remain recoverable after their leases expire. The container grace period covers the independently bounded Batch, mail, and cleanup shutdown budgets.
