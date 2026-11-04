package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.mappers.toSpacePushRecord
import dev.fajar.hris.collaboration.data.repositories.EncryptedCollaborationSpacePushBindingRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.*
import dev.fajar.hris.identity.data.datasources.JceIdentityTokenDataSource
import dev.fajar.hris.schema.tables.records.CollaborationSpacePushBindingsRecord
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
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
class CollaborationSpacePushBindingStorageTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun stableEncryptedCapabilityKeepsRootOwnershipAndLookupDoesNotExposeSubsequentReads() {
        val f = Fixture()
        assertNull(success(f.read()))
        success(
            f.storage.tx {
                success(f.repo.lock(f.value.spaceId, f.value.deviceId))
                f.repo.reserve(f.value)
            }
        )
        val key = requireNotNull(success(f.storage.tx { f.repo.key(f.value) }))
        assertTrue(key.value.matches(Regex("hrisgp1_[a-f0-9]{64}")))
        assertFalse(requireNotNull(f.raw().keyEncrypted).contains(key.value))
        assertEquals("CollaborationSpacePushKey(<redacted>)", key.toString())
        assertEquals("CollaborationSpacePushBinding(<metadata>)", f.value.toString())
        val lookup = f.storage.actor.copy(accountId = UUID(0, 0), companyId = null)
        success(
            f.storage.tx(lookup) {
                assertEquals(f.value, success(f.repo.findKey(key)))
                // The hash capability is restored inside the same transaction, not just on commit.
                assertNull(
                    success(f.repo.find(f.value.spaceId, f.value.accountId, f.value.deviceId))
                )
                assertNull(
                    success(f.repo.findKey(CollaborationSpacePushKey("hrisgp1_" + "f".repeat(64))))
                )
                assertNull(success(f.repo.key(f.value)))
                Result.Success(Unit)
            }
        )
        for (actor in
            listOf(
                f.storage.actor.copy(accountId = UUID.randomUUID()),
                f.storage.actor.copy(companyId = f.storage.company),
                lookup.copy(companyId = f.storage.company),
            )) {
            success(
                f.storage.tx(actor) {
                    assertNull(
                        success(f.repo.find(f.value.spaceId, f.value.accountId, f.value.deviceId))
                    )
                    assertNull(success(f.repo.findKey(key)))
                    assertNull(success(f.repo.key(f.value)))
                    Result.Success(Unit)
                }
            )
        }
        val envelope = f.raw().keyEncrypted
        val at = now()
        success(f.storage.tx { f.repo.publish(f.value, at) })
        val published = f.value.copy(version = 1, publishedAt = at)
        assertEquals(published, success(f.read()))
        assertEquals(envelope, f.raw().keyEncrypted)
        assertEquals(
            key.value,
            requireNotNull(success(f.storage.tx { f.repo.key(published) })).value,
        )
        assertEquals(
            "collaboration_space_push_changed",
            (f.storage.tx { f.repo.publish(f.value, at) } as Result.Failed).failure.code,
        )
        assertEquals(published, success(f.read()))
    }

    @Test
    fun parentPurgeAndOffboardingPreserveTheCapabilityWithoutAllowingPublication() {
        val f = Fixture()
        success(f.storage.tx { f.repo.reserve(f.value) })
        val key = requireNotNull(success(f.storage.tx { f.repo.key(f.value) })).value
        f.storage.owner.update(
            "update company_memberships set active=false,version=version+1 where account_id=?",
            f.value.accountId,
        )
        assertInstanceOf(Result.Failed::class.java, f.storage.tx { f.repo.publish(f.value, now()) })
        f.storage.owner.update("delete from native_sessions where id=?", f.value.nativeSessionId)
        assertEquals(f.value, success(f.read()))
        assertEquals(key, requireNotNull(success(f.storage.tx { f.repo.key(f.value) })).value)
        assertInstanceOf(Result.Failed::class.java, f.storage.tx { f.repo.publish(f.value, now()) })
        assertEquals(f.value, success(f.read()))
    }

    @Test
    fun backupRestoreAndKeyRotationKeepTheExactCapabilityAndRecoveryPurpose() {
        val f = Fixture()
        val old =
            EncryptedCollaborationSpacePushBindingRepository(
                f.source,
                JceIdentityTokenDataSource(f.storage.oldKeys),
            )
        success(f.storage.tx { old.reserve(f.value) })
        val key = requireNotNull(success(f.storage.tx { old.key(f.value) })).value
        assertEquals(key, requireNotNull(success(f.storage.tx { f.repo.key(f.value) })).value)
        val currentOnly =
            EncryptedCollaborationSpacePushBindingRepository(
                f.source,
                JceIdentityTokenDataSource(
                    parseIdentityKeyring("current", "current:${f.storage.currentKey}")
                ),
            )
        assertInstanceOf(Result.Failed::class.java, f.storage.tx { currentOnly.key(f.value) })
        val at = now()
        success(f.storage.tx { f.repo.publish(f.value, at) })
        val published = f.value.copy(version = 1, publishedAt = at)
        val restoredSource = f.storage.restore()
        val restored =
            EncryptedCollaborationSpacePushBindingRepository(
                PostgresCollaborationSpacePushBindingDataSource(
                    DSL.using(TransactionAwareDataSourceProxy(restoredSource), SQLDialect.POSTGRES)
                ),
                JceIdentityTokenDataSource(f.storage.currentKeys),
            )
        assertEquals(
            key,
            requireNotNull(
                    success(
                        f.storage.transactions(restoredSource).run(f.storage.actor) {
                            restored.key(published)
                        }
                    )
                )
                .value,
        )
        val query = recoveryQuery()
        val result =
            postgres.execInContainer(
                "psql",
                "-X",
                "-q",
                "-U",
                postgres.username,
                "-d",
                "group_gateway_restored",
                "-v",
                "ON_ERROR_STOP=1",
                "-c",
                query,
            )
        assertEquals(0, result.exitCode)
        val restoredKeys =
            result.stdout
                .lineSequence()
                .filter { it.startsWith("hris:matrix-space-push:v1:") }
                .map { line ->
                    val (purpose, encrypted) = line.split('\t')
                    decryptIdentityEnvelope(f.storage.currentKeys, purpose, encrypted)
                }
                .toSet()
        assertEquals(setOf(key), restoredKeys)
    }

    @Test
    fun failedPostWriteReadbackRollsBackReservationAndPublicationAndPreservesCancellation() {
        val f = Fixture()
        for (publication in listOf(false, true)) {
            if (publication) success(f.storage.tx { f.repo.reserve(f.value) })
            for (mode in
                listOf(
                    "missing",
                    "scope",
                    "hash",
                    "ciphertext",
                    "precision",
                    "cancel",
                    "interrupt",
                    "throw",
                )) {
                var written = false
                val source =
                    object : CollaborationSpacePushBindingDataSource by f.source {
                        override fun insert(record: CollaborationSpacePushBindingsRecord) {
                            f.source.insert(record)
                            written = true
                        }

                        override fun publish(
                            expected: CollaborationSpacePushBindingsRecord,
                            at: OffsetDateTime,
                        ): Boolean = f.source.publish(expected, at).also { written = it }

                        override fun find(
                            spaceId: UUID,
                            accountId: UUID,
                            deviceId: UUID,
                        ): CollaborationSpacePushBindingsRecord? {
                            val row = f.source.find(spaceId, accountId, deviceId)
                            if (!written) return row
                            if (mode == "missing") return null
                            if (mode == "cancel") throw CancellationException("owned readback")
                            if (mode == "interrupt") throw InterruptedException("owned readback")
                            if (mode == "throw") error("owned readback")
                            return requireNotNull(row).also {
                                when (mode) {
                                    "scope" -> it.accountId = UUID.randomUUID()
                                    "hash" -> it.keyHash = "b".repeat(64)
                                    "ciphertext" -> it.keyEncrypted = "different-envelope"
                                    "precision" ->
                                        it.reservedAt = requireNotNull(it.reservedAt).plusNanos(1)
                                }
                            }
                        }
                    }
                val repo =
                    EncryptedCollaborationSpacePushBindingRepository(
                        source,
                        JceIdentityTokenDataSource(f.storage.currentKeys),
                    )
                val operation = {
                    f.storage.tx {
                        if (publication) repo.publish(f.value, now()) else repo.reserve(f.value)
                    }
                }
                when (mode) {
                    "cancel" -> assertThrows(CancellationException::class.java) { operation() }
                    "interrupt" -> assertThrows(InterruptedException::class.java) { operation() }
                    else -> assertInstanceOf(Result.Failed::class.java, operation(), mode)
                }
                assertTrue(written)
                assertEquals(if (publication) f.value else null, success(f.read()))
            }
        }
    }

    @Test
    fun invalidMetadataAndForeignCapabilityPrefixesStopBeforeRawStorage() {
        val f = Fixture()
        var calls = 0
        val source =
            object : CollaborationSpacePushBindingDataSource {
                override fun lock(spaceId: UUID, deviceId: UUID) {
                    calls++
                    error("owned acquisition")
                }

                override fun find(
                    spaceId: UUID,
                    accountId: UUID,
                    deviceId: UUID,
                ): CollaborationSpacePushBindingsRecord? {
                    calls++
                    error("owned acquisition")
                }

                override fun byKey(hash: String): CollaborationSpacePushBindingsRecord? {
                    calls++
                    error("owned acquisition")
                }

                override fun insert(record: CollaborationSpacePushBindingsRecord) {
                    calls++
                    error("owned acquisition")
                }

                override fun publish(
                    expected: CollaborationSpacePushBindingsRecord,
                    at: OffsetDateTime,
                ): Boolean {
                    calls++
                    error("owned acquisition")
                }
            }
        val repo =
            EncryptedCollaborationSpacePushBindingRepository(
                source,
                JceIdentityTokenDataSource(f.storage.currentKeys),
            )
        for (bad in
            listOf(
                f.value.copy(spaceId = UUID(0, 0)),
                f.value.copy(nativeSessionId = UUID(0, 0)),
                f.value.copy(matrixUserId = "@other:matrix.example.test"),
                f.value.copy(
                    matrixDeviceId = "HRIS_" + UUID.randomUUID().toString().replace("-", "")
                ),
                f.value.copy(version = 2),
                f.value.copy(publishedAt = now()),
                f.value.copy(reservedAt = f.value.reservedAt.plusNanos(1)),
                f.value.copy(reservedAt = Instant.MAX),
            )) {
            assertInstanceOf(Result.Failed::class.java, repo.reserve(bad))
            assertInstanceOf(Result.Failed::class.java, repo.publish(bad, now()))
            assertInstanceOf(Result.Failed::class.java, repo.key(bad))
        }
        for (bad in
            listOf(
                "hriscp1_" + "a".repeat(64),
                "hrisgp1_" + "A".repeat(64),
                "hrisgp1_" + "a".repeat(65),
            )) assertInstanceOf(
            Result.Failed::class.java,
            repo.findKey(CollaborationSpacePushKey(bad)),
        )
        assertInstanceOf(Result.Failed::class.java, repo.lock(UUID(0, 0), f.value.deviceId))
        assertInstanceOf(
            Result.Failed::class.java,
            repo.find(f.value.spaceId, UUID(0, 0), f.value.deviceId),
        )
        assertEquals(0, calls)
        assertInstanceOf(Result.Failed::class.java, repo.reserve(f.value))
        assertInstanceOf(
            Result.Failed::class.java,
            repo.find(f.value.spaceId, f.value.accountId, f.value.deviceId),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            repo.findKey(CollaborationSpacePushKey("hrisgp1_" + "a".repeat(64))),
        )
        assertEquals(3, calls)
    }

    @Test
    fun everyOriginalCoordinateAndCapabilityHashAreAuthenticatedBeforeReturningAKey() {
        val f = Fixture()
        success(f.storage.tx { f.repo.reserve(f.value) })
        val original = f.raw()
        val account = UUID.randomUUID()
        val space = UUID.randomUUID()
        val device = UUID.randomUUID()
        fun compact(value: UUID) = value.toString().replace("-", "")
        val values =
            listOf(
                f.value.copy(
                    spaceId = space,
                    matrixUserId =
                        "@hrisg_${compact(space)}_${compact(f.value.accountId)}:matrix.example.test",
                ),
                f.value.copy(
                    accountId = account,
                    matrixUserId =
                        "@hrisg_${compact(f.value.spaceId)}_${compact(account)}:matrix.example.test",
                ),
                f.value.copy(deviceId = device, matrixDeviceId = "HRISG_${compact(device)}"),
                f.value.copy(nativeSessionId = UUID.randomUUID()),
                f.value.copy(
                    matrixUserId =
                        "@hris_${compact(UUID.randomUUID())}_${compact(f.value.accountId)}:matrix.example.test"
                ),
                f.value.copy(reservedAt = f.value.reservedAt.plusNanos(1000)),
            )
        for (value in values) {
            val source =
                object : CollaborationSpacePushBindingDataSource by f.source {
                    override fun find(
                        spaceId: UUID,
                        accountId: UUID,
                        deviceId: UUID,
                    ): CollaborationSpacePushBindingsRecord =
                        value.toSpacePushRecord().also {
                            it.keyHash = original.keyHash
                            it.keyEncrypted = original.keyEncrypted
                        }
                }
            val repo =
                EncryptedCollaborationSpacePushBindingRepository(
                    source,
                    JceIdentityTokenDataSource(f.storage.currentKeys),
                )
            assertInstanceOf(Result.Failed::class.java, repo.key(value))
        }
        val source =
            object : CollaborationSpacePushBindingDataSource by f.source {
                override fun find(
                    spaceId: UUID,
                    accountId: UUID,
                    deviceId: UUID,
                ): CollaborationSpacePushBindingsRecord =
                    f.value.toSpacePushRecord().also {
                        it.keyHash = "b".repeat(64)
                        it.keyEncrypted = original.keyEncrypted
                    }

                override fun byKey(hash: String): CollaborationSpacePushBindingsRecord =
                    find(f.value.spaceId, f.value.accountId, f.value.deviceId)
            }
        val repo =
            EncryptedCollaborationSpacePushBindingRepository(
                source,
                JceIdentityTokenDataSource(f.storage.currentKeys),
            )
        assertInstanceOf(Result.Failed::class.java, repo.key(f.value))
        val key = requireNotNull(success(f.storage.tx { f.repo.key(f.value) }))
        assertInstanceOf(Result.Failed::class.java, repo.findKey(key))
        assertEquals(f.value, success(f.read()))
    }

    private fun recoveryQuery(): String {
        val root =
            generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .first { Files.isRegularFile(it.resolve("tool/recovery_set.py")) }
        val process =
            ProcessBuilder(
                    "python3",
                    "-c",
                    "import sys; sys.path.insert(0, 'tool'); import recovery_set; print(recovery_set.envelope_query(136))",
                )
                .directory(root.toFile())
                .start()
        return try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS))
            assertEquals(0, process.exitValue())
            process.inputStream.bufferedReader().use { it.readText() }
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
                assertTrue(process.waitFor(5, TimeUnit.SECONDS))
            }
            process.inputStream.close()
            process.errorStream.close()
            process.outputStream.close()
        }
    }

    private inner class Fixture {
        val storage = SpaceGatewayPostgresFixture(postgres).also { it.migrate() }

        init {
            Flyway.configure().dataSource(storage.source).target("136").load().migrate()
            storage.owner.execute(
                "grant select,insert,update,delete on collaboration_space_push_bindings to space_gateway_fixture,space_gateway_worker"
            )
        }

        val source =
            PostgresCollaborationSpacePushBindingDataSource(
                DSL.using(
                    TransactionAwareDataSourceProxy(storage.runtimeSource),
                    SQLDialect.POSTGRES,
                )
            )
        val repo =
            EncryptedCollaborationSpacePushBindingRepository(
                source,
                JceIdentityTokenDataSource(storage.currentKeys),
            )
        val value =
            CollaborationSpacePushBinding(
                storage.space,
                storage.account,
                storage.device,
                storage.parent,
                storage.user,
                "HRISG_" + storage.device.toString().replace("-", ""),
                0,
                now(),
                null,
            )

        fun read() = storage.tx { repo.find(value.spaceId, value.accountId, value.deviceId) }

        fun raw() =
            requireNotNull(
                    DSL.using(storage.source, SQLDialect.POSTGRES)
                        .fetchOne(
                            "select * from collaboration_space_push_bindings where device_id=?",
                            value.deviceId,
                        )
                )
                .into(CollaborationSpacePushBindingsRecord::class.java)
    }

    private fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS)

    private fun <T> success(result: Result<T>): T = (result as Result.Success).value
}
