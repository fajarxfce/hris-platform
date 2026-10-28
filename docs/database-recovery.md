# Database backup and recovery verification

`tool/database_recovery.py` creates a logical backup of the existing Compose
database and verifies it in a separate PostgreSQL instance. It does not restore
over the development database or start an API or worker against copied records.
Use Linux, Python 3.11 or newer, and Docker with the same user that owns the backup.

## Create a backup

Run from the repository root. Choose a new directory outside the repository;
existing directories are rejected. Keep the same Compose files as the running
stack, including private document and WireGuard overlays when enabled.

```sh
install -d -m 0700 "$HOME/.local/state/hris-platform/backups"
backup_dir="$HOME/.local/state/hris-platform/backups/20261016T120000"

python3 tool/database_recovery.py backup "$backup_dir" \
  --compose-file compose.yml \
  --compose-file compose.documents.yml \
  --compose-file compose.wireguard.yml
```

The command locates one running `database` service, uses `pg_dump --format=custom`,
and validates its archive directory. It also saves `pg_dumpall --roles-only
--no-role-passwords`: PostgreSQL roles are cluster objects and are not included in
the database archive. Acquisition has finite process/query deadlines and produces:

- `database.dump`: schema, records, ownership and access rules.
- `roles.sql`: role attributes and memberships, without login passwords.
- `manifest.json`: format, database identity, observed schema, image reference,
  lengths and SHA-256 digests. It is written after both payloads complete.
- `backup.log`: private diagnostic output.

The directory uses mode `0700`; files use `0600`. Incomplete payloads are removed
after failure or cancellation. A failed run is not an acknowledged backup.
Do not commit, attach to CI artifacts or publish these files: the database contains
personnel records and credential hashes even though role passwords are omitted.

Ordinary writes may continue during PostgreSQL's consistent database snapshot.
Avoid schema and role changes while collecting its separate role definitions.
For a deployment checkpoint, gracefully stop the API and worker before the final
backup so no accepted commands are left outside that checkpoint. Preserve the
named database volume and all existing Compose overlays.

## Verify restoration

```sh
python3 tool/database_recovery.py verify "$backup_dir"
```

To rehearse an upgrade using the current checkout's migrations:

```sh
python3 tool/database_recovery.py verify "$backup_dir" --migrate
```

Verification checks file ownership, permissions, regular-file types, manifest
format, lengths and digests before starting Docker. It restores on a private
internal network with no published ports, a dedicated PostgreSQL container, and
temporary credentials. The database has a 1 GiB memory limit and a 768 MiB data
tmpfs; larger recovery exercises require separately provisioned storage rather
than this bounded local verifier. Archives above 16 GiB are not accepted.

The original bootstrap role name is retained. Its existing creation statement is
omitted from the role restore, while attributes, capability roles and original
grantors remain intact. This matters on PostgreSQL 18: restoring grants as a
different bootstrap role can fail even when that role is a superuser. Original
runtime passwords are not needed for this isolated verification.

The archive restores in one transaction with failure termination. Checks cover
Flyway failures, unvalidated constraints and runtime roles without superuser,
`BYPASSRLS`, role-creation or database-creation rights. `--migrate` also applies
and validates current Flyway migrations inside this temporary database.

Every run owns its resource names and labels. Cleanup checks ownership before
removal, including after a lost creation response, cancellation or failed restore.
It attempts remaining cleanup after an individual failure and records success
only after cleanup completes. A Docker outage can prevent confirmation of removal;
inspect the private log and resources bearing that run's `hris.recovery-run` label.
Never use a global Docker prune as recovery cleanup.

A successful run adds a `verification-<id>.json` report and private log. The report
records the restored schema and any migration result. It does not establish a
recovery time objective, filesystem backup, object-storage recovery or successful
application login.

## Deployment recovery

Keep a verified database backup with the matching application image references
and deployment configuration. Identity/synchronization encryption keyrings,
database login secrets, external-provider configuration and private object bytes
need their own protected backup. The utility deliberately does not copy `.env`,
Firebase credentials or Garage data.

For the configured single-node Garage deployment, [recovery sets](recovery-set.md)
coordinate this database backup with stopped metadata/content and a separate key
pack. Their isolated verifier checks every object and decrypts retained identity
envelopes with the actual application cipher before acknowledging the checkpoint.

Before upgrading, build and validate the application artifacts, retain the old
images, rehearse migrations against a backup, drain the application roles, and
take a final checkpoint. Apply migrations before starting the new API and worker.
Then verify process startup, API health, private network bindings and the relevant
database constraints. See [development](development.md#local-runtime) for the
Compose configuration.

A database restore after accepting new writes would discard those writes.
Choose and review a recovery point before any real cutover. Application rollback
also requires schema compatibility; reverting an image does not revert migrations.
This tool provides isolated verification and has no live-destination option.
