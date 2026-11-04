package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.crypto.collaborationSpaceGatewayPurpose
import dev.fajar.hris.collaboration.data.datasources.CollaborationSpaceGatewaySessionDataSource
import dev.fajar.hris.collaboration.data.mappers.toSpaceGatewaySession
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.datasources.JceIdentityTokenDataSource
import dev.fajar.hris.schema.tables.records.CollaborationSpaceGatewaySessionsRecord
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSpaceGatewaySessionTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun scopedLookupLostResponseKeyRotationAndArchiveRestorePreserveTheExactCommittedPair() {
        val f = SpaceGatewayPostgresFixture(postgres)
        f.migrate()
        val old = f.repository(f.runtimeSource, f.oldKeys)
        val first = f.success(f.tx { old.create(f.session, f.operation, f.at.plusSeconds(30)) })
        assertEquals(
            first.accessToken,
            f.success(f.tx { f.repo.replayBootstrap(f.session, f.operation) })!!.accessToken,
        )
        recoveryPurpose(f, first)
        val anonymous = f.actor.copy(accountId = UUID(0, 0))
        assertEquals(f.session, f.success(f.tx(anonymous) { f.repo.findAccess(first.accessToken) }))
        for (actor in
            listOf(
                f.actor.copy(accountId = UUID.randomUUID()),
                f.actor.copy(companyId = f.company),
            )) {
            assertNull(f.success(f.tx(actor) { f.repo.findAccess(first.accessToken) }))
            assertNull(f.success(f.tx(actor) { f.repo.find(f.space, f.account, f.device) }))
        }
        assertNull(
            f.rawSource.byAccess(JceIdentityTokenDataSource(f.currentKeys).hash(first.accessToken))
        )
        val next = f.session.copy(version = 1, rotatedAt = f.at.plusSeconds(1))
        val pair =
            f.success(f.tx { f.repo.rotateRefresh(next, first.refreshToken, f.at.plusSeconds(31)) })
        assertNull(f.success(f.tx(anonymous) { f.repo.findAccess(first.accessToken) }))
        assertTrue(f.success(f.tx(anonymous) { f.repo.findRefresh(first.refreshToken) })!!.previous)
        assertInstanceOf(
            Result.Failed::class.java,
            f.tx { old.replayRefresh(next, first.refreshToken) },
        )
        recoveryPurpose(f, pair)
        val restored = f.restore()
        val replay =
            f.success(
                f.transactions(restored).run(f.actor) {
                    f.repository(restored).replayRefresh(next, first.refreshToken)
                }
            )!!
        assertEquals(pair.accessToken, replay.accessToken)
        assertEquals(pair.refreshToken, replay.refreshToken)
        assertEquals(pair.replayUntil, replay.replayUntil)
        assertEquals(f.session.expiresAt, replay.expiresAt)
    }

    @Test
    fun postWriteReadbackFailureAndCancellationRollBackCreationRotationAndRetirement() {
        val f = SpaceGatewayPostgresFixture(postgres)
        f.migrate()
        val faults =
            listOf<
                (
                    CollaborationSpaceGatewaySessionsRecord?
                ) -> CollaborationSpaceGatewaySessionsRecord?
            >(
                { null },
                { it?.apply { deviceId = UUID.randomUUID() } },
                { throw IOException("fixture readback") },
                { throw CancellationException("fixture cancellation") },
                { throw InterruptedException("fixture interruption") },
                {
                    Thread.currentThread().interrupt()
                    it
                },
            )
        for (fault in faults) {
            val source = SpaceGatewayReadbackFaultSource(f.rawSource, fault)
            failsOrCancels {
                f.tx { f.repository(source).create(f.session, f.operation, f.at.plusSeconds(30)) }
            }
            assertTrue(source.written)
            assertEquals(
                0,
                f.owner.queryForObject(
                    "select count(*) from collaboration_space_gateway_sessions",
                    Int::class.java,
                ),
            )
        }
        val pair = f.success(f.tx { f.repo.create(f.session, f.operation, f.at.plusSeconds(30)) })
        val original = f.raw()
        val next = f.session.copy(version = 1, rotatedAt = f.at.plusSeconds(1))
        for (fault in faults) {
            val source = SpaceGatewayReadbackFaultSource(f.rawSource, fault)
            failsOrCancels {
                f.tx {
                    f.repository(source)
                        .rotateRefresh(next, pair.refreshToken, f.at.plusSeconds(31))
                }
            }
            assertTrue(source.written)
            assertEquals(original, f.raw())
        }
        f.revoke(f.at.plusSeconds(1))
        for (fault in faults) {
            val source = SpaceGatewayReadbackFaultSource(f.rawSource, fault)
            failsOrCancels {
                f.tx {
                    f.repository(source)
                        .retire(f.space, f.account, f.device, 0, f.at.plusSeconds(2))
                }
            }
            assertTrue(source.written)
            assertEquals(original, f.raw())
        }
        f.success(f.tx { f.repo.retire(f.space, f.account, f.device, 0, f.at.plusSeconds(2)) })
        assertEquals(
            f.session.copy(version = 1, revokedAt = f.at.plusSeconds(2)),
            f.raw().toSpaceGatewaySession(),
        )
        assertNull(f.raw().replayEncrypted)
    }

    @Test
    fun competingRefreshesCommitOnlyOnePairAndRetryRecoversThatExactPair() {
        val f = SpaceGatewayPostgresFixture(postgres)
        f.migrate()
        val first = f.success(f.tx { f.repo.create(f.session, f.operation, f.at.plusSeconds(30)) })
        val barrier = CyclicBarrier(2)
        val raw = f.rawSource
        val source =
            object : CollaborationSpaceGatewaySessionDataSource by raw {
                override fun find(
                    spaceId: UUID,
                    accountId: UUID,
                    deviceId: UUID,
                    lock: Boolean,
                ): CollaborationSpaceGatewaySessionsRecord? =
                    raw.find(spaceId, accountId, deviceId, lock).also {
                        if (it?.version == 0L) barrier.await(5, TimeUnit.SECONDS)
                    }
            }
        val pool = Executors.newFixedThreadPool(2)
        val next = f.session.copy(version = 1, rotatedAt = f.at.plusSeconds(1))
        try {
            val jobs =
                (1..2).map {
                    pool.submit<Result<CollaborationSpaceGatewayTokens>> {
                        f.tx {
                            f.repository(source)
                                .rotateRefresh(next, first.refreshToken, f.at.plusSeconds(31))
                        }
                    }
                }
            val results = jobs.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is Result.Success })
            assertEquals(1, results.count { it is Result.Failed })
            val pair = f.success(results.first { it is Result.Success })
            val replay = f.success(f.tx { f.repo.replayRefresh(next, first.refreshToken) })!!
            assertEquals(pair.accessToken, replay.accessToken)
            assertEquals(pair.refreshToken, replay.refreshToken)
            assertEquals(1L, f.raw().version)
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun expiredReplayMaintenanceSkipsLocksAndPreservesMetadataAfterNativePurgeAndOffboarding() {
        val f = SpaceGatewayPostgresFixture(postgres, ageSeconds = 120)
        f.migrate()
        val pair = f.success(f.tx { f.repo.create(f.session, f.operation, f.at.plusSeconds(30)) })
        f.owner.update("delete from native_sessions where id=?", f.parent)
        f.owner.update("update company_memberships set active=false where account_id=?", f.account)
        assertEquals(f.session, f.success(f.tx { f.repo.find(f.space, f.account, f.device) }))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        val worker = f.repository(f.workerSource)
        val tx = f.transactions(f.workerSource)
        val anonymous = f.actor.copy(accountId = UUID(0, 0))
        try {
            val job =
                pool.submit<Result<Unit>> {
                    f.tx {
                        f.repo.find(f.space, f.account, f.device, lock = true).map {
                            entered.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                        }
                    }
                }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(
                0,
                f.success(tx.run(anonymous) { worker.clearExpiredReplays(Instant.now(), 1) }),
            )
            release.countDown()
            assertEquals(Result.Success(Unit), job.get(5, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
        assertEquals(
            1,
            f.success(tx.run(anonymous) { worker.clearExpiredReplays(Instant.now(), 1) }),
        )
        assertEquals(
            0,
            f.success(tx.run(anonymous) { worker.clearExpiredReplays(Instant.now(), 1) }),
        )
        assertEquals(f.session, f.success(f.tx { f.repo.find(f.space, f.account, f.device) }))
        assertEquals(f.at.plusSeconds(30), f.raw().replayUntil.toInstant())
        assertNull(f.success(f.tx { f.repo.replayBootstrap(f.session, f.operation) }))
        assertEquals(f.session, f.success(f.tx(anonymous) { f.repo.findAccess(pair.accessToken) }))
        val next = f.session.copy(version = 1, rotatedAt = f.at.plusSeconds(1))
        assertInstanceOf(
            Result.Failed::class.java,
            f.tx { f.repo.rotateRefresh(next, pair.refreshToken, f.at.plusSeconds(31)) },
        )
        assertEquals(0L, f.raw().version)
    }

    private fun failsOrCancels(body: () -> Result<*>) {
        try {
            assertInstanceOf(Result.Failed::class.java, body())
        } catch (_: CancellationException) {} catch (_: InterruptedException) {} finally {
            Thread.interrupted()
        }
    }

    private fun recoveryPurpose(
        f: SpaceGatewayPostgresFixture,
        pair: CollaborationSpaceGatewayTokens,
    ) {
        val root = Path.of(System.getProperty("user.dir")).resolve("../../..").normalize()
        val output = Files.createTempFile("space-gateway-query-", ".sql")
        try {
            val process =
                ProcessBuilder(
                        "python3",
                        "-c",
                        "import sys;sys.path.insert(0,'tool');import recovery_set;print(recovery_set.envelope_query(126))",
                    )
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile())
                    .start()
            try {
                assertTrue(process.waitFor(5, TimeUnit.SECONDS))
                assertEquals(0, process.exitValue())
            } finally {
                if (process.isAlive) {
                    process.destroyForcibly()
                    assertTrue(process.waitFor(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(Files.size(output) < 100000)
            val projection =
                postgres.execInContainer(
                    "psql",
                    "-X",
                    "-q",
                    "-U",
                    postgres.username,
                    "-d",
                    postgres.databaseName,
                    "-v",
                    "ON_ERROR_STOP=1",
                    "-c",
                    Files.readString(output),
                )
            assertEquals(0, projection.exitCode, projection.stderr)
            val row =
                projection.stdout
                    .lineSequence()
                    .filter { it.startsWith("hris:matrix-space-gateway:v1:") }
                    .single()
                    .split('\t')
            assertEquals(collaborationSpaceGatewayPurpose(f.raw()), row[0])
            assertEquals(
                pair.accessToken + "\n" + pair.refreshToken,
                JceIdentityTokenDataSource(f.currentKeys).decrypt(row[0], row[1]),
            )
        } finally {
            Files.deleteIfExists(output)
        }
    }
}
