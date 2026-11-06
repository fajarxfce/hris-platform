# Reproducible PostgreSQL and timezone rules

The `postgres-image` version in `gradle/libs.versions.toml` pins PostgreSQL by
digest. Gradle's Kotlin convention passes that exact image to every database test;
`tool/container_images.py` supplies the same version to code generation, workload,
dashboard and recovery integration fixtures. Code generation tracks both files as
inputs. The standalone Compose and native proof manifests retain explicit matching
pins; `containerImageCheck`, included in `check`, detects drift between them.

Testcontainers 2.0.5 does not parse the combined `postgres:tag@sha256:digest`
spelling correctly: it treats `postgres:tag` as the repository and rejects its
compatibility with PostgreSQL. The Gradle convention validates the catalog value
and passes the equivalent `postgres@sha256:digest` spelling to JVM tests. Docker,
Compose and Python fixtures retain the combined spelling. The digest is identical;
no mutable fallback or compatibility override is used.

This also fixes a timezone-data mismatch exposed by Verify
[42120370137](https://github.com/fajarxfce/hris-platform/actions/runs/42120370137).
Backend shards 3 and 7 failed the existing offboarding/onboarding repeated-midnight
HTTP cases. Ten other backend/dashboard jobs passed; the two dependent integration
jobs were skipped. Both failures returned a database constraint conflict, rather
than accepting a scheduled instant that differed from the Java calculation.

The mutable `postgres:18.6-alpine` tag changed during development. Its
`80df0884d0d3b7f0f7b2c37f273b1c12d5fd66e25aaef2e19ebf95908d620f11`
digest maps Havana midnight on 1 November 2026 to 04:00 UTC. The retained live/native
proof image `10a5c69a83035ab5239f5620d708520e8b28f5aa1f2b20143e6024532edadfb5`
maps it to 05:00 UTC, matching the Java 21 rules used by these runs. Separate
network-isolated disposable PostgreSQL instances reproduced those SQL results;
both images use `/usr/share/zoneinfo`. Their containers were removed afterward.

The pin preserves the already deployed timezone snapshot. It is not a statement
that old timezone rules remain correct indefinitely. A planned timezone update
must update Java and PostgreSQL together, review future scheduled boundaries and
their retained authorization, and rerun the scheduling, worker and recovery tests.
Do not loosen the database constraints or rewrite immutable submitted schedules
merely to make an image upgrade pass. Existing runtime/database containers and
business data have not been changed by this build configuration correction.

When changing the image, update the version catalog, Compose and native proof
manifest together. Run `./gradlew containerImageCheck`, the onboarding/offboarding
HTTP and worker cases, database/migration checks, and the full Verify workflow
before deploying the resulting application or database image.

The combined-name CI attempt42126384926 failed all eight backend jobs during
shared database-test initialization; four dashboard jobs passed and downstream
integration jobs were skipped. The corrected digest-only convention passes24
local cases in306.132s: PostgreSQL isolation, both previously failing repeated-
midnight HTTP cases, and worker context/lifecycle. Image consistency, formatting
and architecture also pass. Full Verify on the corrected commit remains required
before deployment. [Evidence](measurements/postgres-image-pin-2026-11-05.json).

Recovery manifests retain the exact Docker image reference observed by the backup
writer. The reader accepts legacy PostgreSQL18.x-alpine tags, digest-only names,
and combined18.x-alpine tag/digest names without normalizing or dropping the
digest. Other repositories/major versions, malformed hashes and appended arguments
remain rejected before Docker. This closes the recovery-integration failure in
42134116564 after all eight backend and four dashboard shards passed.

[Recovery evidence](measurements/backup-image-manifest-2026-11-06.json):46 unit tests
and an isolated real backup/restore passed on schema116.1, including104 objects,
separate retained encryption keys, wrong-key rejection and document metadata scope/
constraints. The fixture used verified exacta678 API/worker artifacts in a detached
checkout, leaving the main build outputs and live services alone. A first local
attempt lacked the CI-created result directory and is recorded as unaccepted; the
second completed and removed its owned resources. Full CI on the corrected source
is still required before deployment.
