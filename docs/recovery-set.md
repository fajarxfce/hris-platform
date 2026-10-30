# Database, content and key recovery

`tool/recovery_set.py` captures the stopped Compose application's PostgreSQL,
Garage metadata/content and required encryption keys as one verified checkpoint.
It uses two new private directories: a data checkpoint and a separate key pack.
It has no live restore destination and never stops or restarts the source services.

Use Linux, Python 3.11+, Java 21, Docker and curl with SigV4 support. Build the
current API JAR first; its offline key-verification entry point reuses the actual
application cipher without starting Spring, an HTTP server, workers or providers.
The checked-in Garage 2.4.1 single-node SQLite layout and database schemas 59–103
are supported. V103 adds typed document metadata and immutable history, without
a new encrypted record family. New encryption contracts require reviewing the schema bound.

## Capture a checkpoint

Finish application validation and preserve the deployment's image references.
During the checkpoint window, stop new administrative/schema/role changes and
other direct database/storage writers. Gracefully stop API, worker and Garage
using the same Compose files and configuration as the running deployment. Keep
PostgreSQL running and retain every volume. For example:

```sh
docker compose -f compose.yml -f compose.documents.yml -f compose.wireguard.yml stop api
docker compose -f compose.yml -f compose.documents.yml -f compose.wireguard.yml stop worker
docker compose -f compose.yml -f compose.documents.yml -f compose.wireguard.yml stop garage

recovery_root="$HOME/.local/state/hris-platform"
install -d -m 0700 "$recovery_root/recovery-data" "$recovery_root/recovery-keys"
recovery_data="$recovery_root/recovery-data/20261028T120000"
recovery_keys="$recovery_root/recovery-keys/20261028T120000"

python3 tool/recovery_set.py backup "$recovery_data" \
  --keys-directory "$recovery_keys" --project hris-platform
```

The utility requires exactly one labelled container for each source service.
API, worker and Garage must have exited normally (0 or SIGTERM/143), without OOM,
pause or restart in progress. A database snapshot remains internally consistent;
service checks do not replace the operator's exclusive checkpoint window. Source
container identity, image and start/finish timestamps are rechecked before success.

The checkpoint contains the existing [database backup](database-recovery.md),
`metadata.tar`, `content.tar`, `garage.toml`, `inventory.jsonl` and a final manifest.
The manifest records source service image references/IDs and checkpoint timestamps;
retain those images and any separately mounted application artifacts/configuration.
The separate `keys.json` retains current and older identity/sync keys plus the
Garage RPC and bucket credentials actually configured in the source containers.
API/worker identity and storage configuration must agree. No `.env` file, provider
service account or database login password is copied into the key pack.

Both directories must be outside Git checkouts, owned by the caller and private
(0700 directories/0600 files). Archives and key packs are plaintext: store them on
protected backup storage with the deployment's encryption and custody controls.
Garage metadata itself includes storage credential material, so separating the
application key pack does not make the data archive safe to publish.

A backup succeeds only after isolated database/key and S3 verification and cleanup.
The final manifest binds exact lengths/digests, database manifest, key-pack identity,
restored database observations and an inventory of object bytes. Failure removes
the unfinished top-level payloads/key pack and writes no success manifest. Private
diagnostics and a completed nested database-only backup may remain for inspection;
they do not constitute a complete recovery set. Use new directories for a retry.

## Verify a retained checkpoint

```sh
python3 tool/recovery_set.py verify "$recovery_data" \
  --keys-directory "$recovery_keys"
```

Verification checks permissions, regular files, manifest binding, digests and tar
contents before restoration. Tar paths, links, devices, duplicate entries, sparse
files, oversized extension headers and traversal are rejected. Extraction uses
new caller-owned files, never archived ownership or permissive modes.

PostgreSQL restores on an internal Docker network without published ports, using
the original bootstrap-role identity. Verification checks the observed schema,
Flyway status, constraints and restricted runtime roles. It decrypts every retained
identity envelope using its original account/session/challenge/version purpose:
MFA credentials/enrollments, native exchange and refresh replay, credential mail
and push registrations. Missing or incorrect retired keys fail even if someone
recomputes the key-pack checksum. Decrypted values never enter output or reports.
Sync keyring validation preserves its configured current/older keys; this does not
extend existing client cursor deadlines or authorize a previous session.

Garage restores copied metadata/content using the source image ID, the reviewed
single-node configuration and the invoking user's UID. Its internal network has
no published ports. The Linux host reads only that owned container's private IP;
curl proxy configuration is disabled, and SigV4 credentials use a temporary private
file instead of command arguments. No source bucket is a restore target.

The initial backup reads every live object from its isolated copy to establish
the inventory. Later verification checks the same ordered keys, lengths and SHA-256
bytes, including empty objects, across bounded S3 pages. Extra, missing or changed
objects fail. Standalone `.`/`..` key segments are unsupported and rejected before
curl can normalize their URL; ordinary HRIS physical keys do not use those segments.
This checks the storage snapshot's readable objects, not every possible business
reference or the resumption of queued deliveries; document inventory reconciliation
and application acceptance retain their own contracts.

Limits are explicit: 128 MiB metadata tar, 16 GiB content tar and total object bytes,
128 MiB per object, 100,000 archive members/objects/identity envelopes, 100 objects
per listing and a 600-second storage scan. PostgreSQL retains its separate 1 GiB
memory/768 MiB data-tmpfs verifier limits. Garage uses 1 GiB memory and temporary
host disk copies; the offline cipher process uses a 128 MiB heap. Larger exercises
need a reviewed extension or separately provisioned recovery tooling.

Each process, stream, request and temporary service has a deadline and explicit
owner. Failed/interrupted creation retains its generated resource names for
cleanup; removal checks the run label. If Docker cannot confirm Garage removal,
the private extracted bind data is retained for diagnosis. Inspect resources with
that run's `hris.recovery-run` label before removing its private temporary files.
Never use a global Docker prune for this cleanup. Successful reports are written
only after all owned verification resources are removed.

## Recovery and validation limits

A real cutover still needs the reviewed recovery point, compatible API/worker
images, separate runtime/provider credentials, private networking and a deployment
runbook. Resuming copied queued commands can have external effects; this verifier
starts no application runtime against copied personnel records. It neither rewinds
the live stack nor proves an RPO, RTO, production dataset capacity or provider delivery.

`python3 tool/check_recovery_set.py` owns a fictional PostgreSQL/API/worker/Garage
deployment. It creates real MFA and native-session records under an older identity
key, activates a newer key and retains both, then checks paged S3 data including
empty, Unicode/punctuation and multi-block objects. It changes the original storage
after capture to test checkpoint independence and rejects an incorrect retired key
even with a recomputed manifest hash. Only sanitized summaries are CI artifacts;
private archives, key packs, credentials and raw logs remain outside artifact paths.
Two complete local exercises passed at schema 97. The final run verified two
retained encrypted records under old/current keys and 104 objects totalling
2,098,795 bytes, including a second restore after source changes and rejection of
the wrong retired key. All temporary resources and private fixture data were
removed. Twenty recovery-tool tests, 21 existing database-tool tests and three JVM
cipher tests also passed, alongside architecture, formatting and API packaging.
The CI job is configured; these are local results. See the [delivery log](delivery.md).

The subsequent V98 worker-report migration passed the complete exercise again:
schema 98, two encrypted records, 104 objects/2,098,795 bytes, retained old/current
keys, source independence and rejection of a wrong retired key. All owned
resources and private fixture data were removed. Evidence:
`.work/worker-recovery-set-checks.log`; the 20 recovery-tool regressions also pass.


The V99 global-audit projection passed the complete exercise on 29 October 2026:
schema 99, two encrypted records, 104 objects/2,098,795 bytes, retained old/current
keys, independence from later source changes and rejection of a wrong retired
key. All owned temporary resources were removed. No encrypted record family was
added; the verifier ceiling now includes schema 99. Local evidence:
`.work/platform-audit-recovery-set.log`.

The account-access audit increment passed a complete schema-100 exercise: two
encrypted records, 104 objects/2,098,795 bytes, retained old/current keys, source
independence and wrong-retired-key rejection. A fictional source journal captured
through normal V100 triggers is now included, and an additional independent
restore compares all six typed audit columns exactly. No encrypted family was
added; the reviewed ceiling includes schema 100. All owned resources are removed.
Local evidence: `.work/account-audit-recovery-set.log`.

The announcement-delivery increment passed a complete schema-101 exercise with
104 objects/2,098,795 bytes and both retained key generations. A separate fictional
recipient, public employee binding and public announcement request produced the
operational row through the real worker. An independent restore preserved its
exact seven typed fields, forced RLS and denied application-role execution of the
capture trigger, alongside the existing typed account audit. Source storage
changes and an incorrect retired key remained detected. The reviewed schema
ceiling is now 101; all owned resources were removed. This proves restoration,
not external FCM delivery. See [delivery measurements](measurements/announcement-delivery-2026-10-29.json).

The V103 metadata exercise passed on 30 October 2026 in 57.356 seconds. The actual
API creates a fictional passport and two corrections before capture. An independent
restore compares every typed header, history and original upload-revision column;
the restored runtime role enforces company RLS and restored triggers reject history
mutation or a head without matching history. This is metadata/upload-header proof;
the separate 104 S3 objects retain the existing byte-integrity verification. Both
identity key generations, prior account audit/email projections, source independence
and wrong-retired-key rejection remain verified. All owned resources are removed.
The 42 recovery-tool tests pass, including rejection of unknown future schema 104.

The original full CI `41038369597` passed nine jobs, including all actual browser,
worker and private-export steps, but its recovery job rejected schema 103 because
the verifier ceiling was still 102. This correction adds the actual restoration
exercise before raising that bound. [Evidence](measurements/document-metadata-recovery-2026-10-30.json)
retains the original terminal job/step results and unchanged API/worker package
hashes. Replacement full CI remains pending. The private deployment is unchanged.
