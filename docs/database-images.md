# Reproducible PostgreSQL and timezone rules

The `postgres-image` version in `gradle/libs.versions.toml` pins PostgreSQL by
digest. Gradle's Kotlin convention passes that exact image to every database test;
`tool/container_images.py` supplies the same version to code generation, workload,
dashboard and recovery integration fixtures. Code generation tracks both files as
inputs. The standalone Compose and native proof manifests retain explicit matching
pins; `containerImageCheck`, included in `check`, detects drift between them.

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
