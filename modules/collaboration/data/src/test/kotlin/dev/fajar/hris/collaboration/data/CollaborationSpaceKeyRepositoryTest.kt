package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationSpaceKeyRepository
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceKeyPublication
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.schema.tables.records.CollaborationSpaceKeyPublicationsRecord
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CancellationException
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSpaceKeyRepositoryTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun reservationAndExactAcknowledgementRetainOriginalIdentityWithRecoverableLostResponse() {
        val f = Fixture()
        assertNull(success(f.read()))
        success(f.storage.tx { f.repo.reserve(f.value) })
        assertEquals(f.value, success(f.read()))
        val at = now()
        for (mismatch in
            listOf(
                f.value.copy(fingerprint = "b".repeat(64)),
                f.value.copy(nativeSessionId = UUID.randomUUID()),
                f.value.copy(reservedAt = f.value.reservedAt.plusNanos(1000)),
            )) {
            assertEquals(
                "collaboration_space_device_keys_changed",
                (f.storage.tx { f.repo.publish(mismatch, at) } as Result.Failed).failure.code,
            )
        }
        assertEquals(f.value, success(f.read()))
        success(f.storage.tx { f.repo.publish(f.value, at) })
        val published = f.value.copy(version = 1, publishedAt = at)
        assertEquals(published, success(f.read()))
        assertEquals(
            "collaboration_space_device_keys_changed",
            (f.storage.tx { f.repo.publish(f.value, at) } as Result.Failed).failure.code,
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.storage.tx { f.repo.reserve(f.value.copy(fingerprint = "b".repeat(64))) },
        )
        assertEquals(published, success(f.read()))
        assertEquals("CollaborationSpaceKeyPublication(<metadata>)", published.toString())
    }

    @Test
    fun uncertainReservationSurvivesOffboardingAndParentPurgeWithoutGrantingAnotherPublication() {
        val f = Fixture()
        success(f.storage.tx { f.repo.reserve(f.value) })
        f.storage.owner.update(
            "update company_memberships set active=false,version=version+1 where account_id=?",
            f.value.accountId,
        )
        f.storage.owner.update("delete from native_sessions where id=?", f.value.nativeSessionId)
        assertEquals(f.value, success(f.read()))
        assertInstanceOf(Result.Failed::class.java, f.storage.tx { f.repo.publish(f.value, now()) })
        assertEquals(f.value, success(f.read()))
        assertNull(
            success(
                f.storage.tx(f.storage.actor.copy(accountId = UUID.randomUUID())) {
                    f.repo.find(f.value.spaceId, f.value.accountId, f.value.deviceId)
                }
            )
        )
        assertNull(
            success(
                f.storage.tx(f.storage.actor.copy(companyId = f.storage.company)) {
                    f.repo.find(f.value.spaceId, f.value.accountId, f.value.deviceId)
                }
            )
        )
    }

    @Test
    fun acquisitionAndPostWriteReadbackShareFailureBoundaryAndRollBackBothTransitions() {
        val f = Fixture()
        for (publication in listOf(false, true)) {
            if (publication) success(f.storage.tx { f.repo.reserve(f.value) })
            for (mode in
                listOf(
                    "missing",
                    "scope",
                    "fingerprint",
                    "precision",
                    "futureShape",
                    "cancel",
                    "interrupt",
                    "throw",
                )) {
                var wrote = false
                val source =
                    object : CollaborationSpaceKeyDataSource by f.source {
                        override fun insert(record: CollaborationSpaceKeyPublicationsRecord) {
                            f.source.insert(record)
                            wrote = true
                        }

                        override fun publish(
                            expected: CollaborationSpaceKeyPublicationsRecord,
                            at: OffsetDateTime,
                        ): Boolean = f.source.publish(expected, at).also { wrote = it }

                        override fun find(
                            spaceId: UUID,
                            accountId: UUID,
                            deviceId: UUID,
                        ): CollaborationSpaceKeyPublicationsRecord? {
                            val row = f.source.find(spaceId, accountId, deviceId)
                            if (!wrote) return row
                            if (mode == "missing") return null
                            if (mode == "cancel") throw CancellationException("fixture readback")
                            if (mode == "interrupt") throw InterruptedException("fixture readback")
                            if (mode == "throw") error("fixture readback")
                            return requireNotNull(row).also {
                                when (mode) {
                                    "scope" -> it.accountId = UUID.randomUUID()
                                    "fingerprint" -> it.fingerprint = "b".repeat(64)
                                    "precision" ->
                                        it.reservedAt = requireNotNull(it.reservedAt).plusNanos(1)
                                    "futureShape" -> it.version = 2
                                }
                            }
                        }
                    }
                val repo = StoredCollaborationSpaceKeyRepository(source)
                val call = {
                    f.storage.tx {
                        if (publication) repo.publish(f.value, now()) else repo.reserve(f.value)
                    }
                }
                when (mode) {
                    "cancel" -> assertThrows(CancellationException::class.java) { call() }
                    "interrupt" -> assertThrows(InterruptedException::class.java) { call() }
                    else -> assertInstanceOf(Result.Failed::class.java, call(), mode)
                }
                assertTrue(wrote)
                assertEquals(if (publication) f.value else null, success(f.read()))
            }
        }
    }

    @Test
    fun invalidMetadataIsRejectedBeforeIoAndSourceFailuresNeverEscapeAsSuccess() {
        val f = Fixture()
        var calls = 0
        val source =
            object : CollaborationSpaceKeyDataSource {
                override fun find(
                    spaceId: UUID,
                    accountId: UUID,
                    deviceId: UUID,
                ): CollaborationSpaceKeyPublicationsRecord? {
                    calls++
                    error("fixture I/O")
                }

                override fun insert(record: CollaborationSpaceKeyPublicationsRecord) {
                    calls++
                    error("fixture I/O")
                }

                override fun publish(
                    expected: CollaborationSpaceKeyPublicationsRecord,
                    at: OffsetDateTime,
                ): Boolean {
                    calls++
                    error("fixture I/O")
                }
            }
        val repo = StoredCollaborationSpaceKeyRepository(source)
        for (bad in
            listOf(
                f.value.copy(spaceId = UUID(0, 0)),
                f.value.copy(nativeSessionId = UUID(0, 0)),
                f.value.copy(matrixUserId = "@other:matrix.example.test"),
                f.value.copy(matrixDeviceId = "HRISG_wrong"),
                f.value.copy(fingerprint = "a".repeat(63)),
                f.value.copy(version = 2),
                f.value.copy(publishedAt = now()),
                f.value.copy(reservedAt = f.value.reservedAt.plusNanos(1)),
                f.value.copy(reservedAt = Instant.MAX),
            )) {
            assertInstanceOf(Result.Failed::class.java, repo.reserve(bad))
            assertInstanceOf(Result.Failed::class.java, repo.publish(bad, now()))
        }
        for (bad in
            listOf(
                now().plusNanos(1),
                f.value.reservedAt.minusNanos(1000),
                Instant.MAX,
            )) assertInstanceOf(Result.Failed::class.java, repo.publish(f.value, bad))
        assertInstanceOf(
            Result.Failed::class.java,
            repo.find(UUID(0, 0), f.value.accountId, f.value.deviceId),
        )
        assertEquals(0, calls)
        assertInstanceOf(
            Result.Failed::class.java,
            repo.find(f.value.spaceId, f.value.accountId, f.value.deviceId),
        )
        assertEquals(1, calls)
        assertInstanceOf(Result.Failed::class.java, repo.reserve(f.value))
        assertEquals(2, calls)
        assertInstanceOf(Result.Failed::class.java, repo.publish(f.value, now()))
        assertEquals(3, calls)
    }

    private inner class Fixture {
        val storage = SpaceGatewayPostgresFixture(postgres).also { it.migrate() }
        val source =
            PostgresCollaborationSpaceKeyDataSource(
                DSL.using(
                    TransactionAwareDataSourceProxy(storage.runtimeSource),
                    SQLDialect.POSTGRES,
                )
            )
        val repo = StoredCollaborationSpaceKeyRepository(source)
        val value =
            CollaborationSpaceKeyPublication(
                storage.space,
                storage.account,
                storage.device,
                storage.parent,
                storage.user,
                "HRISG_" + storage.device.toString().replace("-", ""),
                "a".repeat(64),
                0,
                now(),
            )

        init {
            Flyway.configure().dataSource(storage.source).target("128").load().migrate()
            storage.owner.execute(
                "grant select,insert,update,delete on collaboration_space_key_publications to space_gateway_fixture,space_gateway_worker"
            )
        }

        fun read() = storage.tx { repo.find(value.spaceId, value.accountId, value.deviceId) }
    }

    private fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS)

    private fun <T> success(result: Result<T>): T = (result as Result.Success).value
}
