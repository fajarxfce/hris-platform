package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.*
import java.util.UUID
import java.util.concurrent.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSpaceRoomRequestFenceMigrationTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun migrationPreservesReceiptsAndRetainedFenceSurvivesParentPurgeAndArchiveRestore() {
        val f = Fixture(upgrade = false)
        val executed = UUID.randomUUID()
        success(f.s.run { f.receipt(executed) })
        success(f.s.run { f.receipt(executed, legacy = true) })
        val before = f.s.owner.queryForList("select * from operation_receipts order by operation")
        f.upgrade()
        assertEquals(
            before,
            f.s.owner.queryForList("select * from operation_receipts order by operation"),
        )
        success(f.s.run { f.fence() })
        val original = success(f.s.run { f.rows() })
        f.s.owner.update("delete from native_sessions where id=?", f.s.parent)
        f.s.owner.update(
            "update company_memberships set active=false,version=version+1 where account_id=?",
            f.s.account,
        )
        assertEquals(original, success(f.s.run { f.rows() }))
        val restored = f.s.restore()
        assertEquals(
            original,
            restored.queryForList("select * from collaboration_space_room_request_fences"),
        )
        assertEquals(
            before,
            restored.queryForList("select * from operation_receipts order by operation"),
        )
        assertEquals(
            true,
            restored.queryForObject(
                "select relrowsecurity and relforcerowsecurity from pg_class where oid='collaboration_space_room_request_fences'::regclass",
                Boolean::class.java,
            ),
        )
        assertEquals(
            0,
            f.s.owner.queryForObject(
                "select count(*) from collaboration_space_room_provisioning",
                Int::class.java,
            ),
        )
    }

    @Test
    fun accountCompanyAndAnonymousScopesCannotReadOrInsertAnotherOwnersFence() {
        val f = Fixture()
        success(f.s.run { f.fence() })
        val original = success(f.s.run { f.rows() })
        val worker = JdbcTemplate(f.s.workerSource)
        val tx = PostgresTransactionRunner(JdbcTransactionManager(f.s.workerSource), worker)
        for (actor in
            listOf(
                f.s.anonymous,
                f.s.actor.copy(companyId = f.s.company),
                f.s.actor.copy(accountId = f.peer),
            )) {
            assertTrue(success(f.s.run(actor) { f.rows() }).isEmpty())
            assertInstanceOf(
                Result.Failed::class.java,
                f.s.run(actor) { f.fence(UUID.randomUUID()) },
            )
            assertTrue(
                success(
                        tx.run(actor) {
                            safeDatabaseCall {
                                worker.queryForList(
                                    "select * from collaboration_space_room_request_fences"
                                )
                            }
                        }
                    )
                    .isEmpty()
            )
        }
        assertEquals(
            original,
            success(
                tx.run(f.s.actor) {
                    safeDatabaseCall {
                        worker.queryForList("select * from collaboration_space_room_request_fences")
                    }
                }
            ),
        )
        for (change in
            listOf(
                "update collaboration_space_room_request_fences set fence_id=gen_random_uuid()",
                "delete from collaboration_space_room_request_fences",
            )) {
            assertEquals(0, success(f.s.run { f.s.runtime.update(change) }))
            assertThrows(org.springframework.dao.DataAccessException::class.java) {
                f.s.owner.update(change)
            }
        }
        assertEquals(original, success(f.s.run { f.rows() }))
    }

    @Test
    fun strictDescriptorAndTimeBoundsDoNotRequireAnOldNativeDeviceToRemainAvailable() {
        val f = Fixture()
        for ((field, value) in
            listOf(
                "operation_id" to "'00000000-0000-0000-0000-000000000000'::uuid",
                "device_id" to "'00000000-0000-0000-0000-000000000000'::uuid",
                "fence_id" to "'00000000-0000-0000-0000-000000000000'::uuid",
                "expected_device_version" to "1000000",
                "expected_request_revision" to "0",
                "expected_request_account_id" to "'${f.peer}'::uuid",
                "payload_hash" to "'invalid'",
                "created_at" to "'infinity'::timestamptz",
                "created_at" to "clock_timestamp()+interval '10 seconds'",
                "created_at" to "'1900-01-01'::timestamptz",
            )) assertInstanceOf(
            Result.Failed::class.java,
            f.s.run { f.fence(overrides = mapOf(field to value)) },
            field,
        )
        assertTrue(success(f.s.run { f.rows() }).isEmpty())
        // The original descriptor can refer to a device no longer locally/native bound.
        success(
            f.s.run { f.fence(overrides = mapOf("device_id" to "'${UUID.randomUUID()}'::uuid")) }
        )
        assertEquals(1, success(f.s.run { f.rows() }).size)
    }

    @Test
    fun committedReceiptCannotBecomeNonExecutionAndFenceRejectsEveryStablePayloadForOriginalId() {
        val f = Fixture()
        val executed = UUID.randomUUID()
        success(f.s.run { f.receipt(executed) })
        assertInstanceOf(Result.Failed::class.java, f.s.run { f.fence(executed) })
        success(f.s.run { f.fence() })
        for (hash in listOf(f.hash, "b".repeat(64))) {
            val probe = UUID.randomUUID()
            assertInstanceOf(
                Result.Failed::class.java,
                f.s.run {
                    f.receipt(probe, legacy = true)
                    f.receipt(f.operation, hash = hash)
                },
            )
            assertEquals(
                0,
                f.s.owner.queryForObject(
                    "select count(*) from operation_receipts where operation_id=?",
                    Int::class.java,
                    probe,
                ),
            )
        }
        success(f.s.run { f.receipt(f.operation, legacy = true) })
        val retained =
            f.s.owner.queryForList(
                "select * from operation_receipts where operation_id=?",
                executed,
            )
        for (change in
            listOf(
                "delete from operation_receipts where operation_id=?",
                "update operation_receipts set response='{\"version\":1}'::jsonb where operation_id=?",
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                f.s.run { f.s.runtime.update(change, executed) },
            )
        }
        assertEquals(
            retained,
            f.s.owner.queryForList(
                "select * from operation_receipts where operation_id=?",
                executed,
            ),
        )
        assertEquals(1, success(f.s.run { f.rows() }).size)
    }

    @Test
    fun failedAndCancelledFenceTransactionsLeaveNoNonExecutionEvidence() {
        val f = Fixture()
        assertInstanceOf(
            Result.Failed::class.java,
            f.s.transactions.run<Unit>(f.s.actor) {
                f.fence()
                Result.Failed(Failure(FailureKind.CONFLICT, "fixture_rejected"))
            },
        )
        assertThrows(CancellationException::class.java) {
            f.s.transactions.run<Unit>(f.s.actor) {
                f.fence()
                throw CancellationException("fixture cancellation")
            }
        }
        assertTrue(success(f.s.run { f.rows() }).isEmpty())
        success(f.s.run { f.receipt(f.operation) })
    }

    @Test
    fun competingCommittedReceiptAndFenceSerializeOnTheExactOriginalOperationLock() {
        for (fenceFirst in listOf(false, true)) competing(fenceFirst, rollbackFirst = false)
    }

    @Test
    fun rolledBackWriterOrFenceAllowsOnlyTheWaitingOriginalOutcomeToCommit() {
        for (fenceFirst in listOf(false, true)) competing(fenceFirst, rollbackFirst = true)
    }

    private fun competing(fenceFirst: Boolean, rollbackFirst: Boolean) {
        // Each test instance has one migrated database; competing pairs use distinct operation IDs.
        val f = sharedFixture ?: Fixture().also { sharedFixture = it }
        val operation = UUID.randomUUID()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first =
                pool.submit<Result<Unit>> {
                    f.s.transactions.run(f.s.actor) {
                        if (fenceFirst) f.fence(operation) else f.receipt(operation)
                        entered.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                        if (rollbackFirst)
                            Result.Failed(Failure(FailureKind.CONFLICT, "fixture_rollback"))
                        else Result.Success(Unit)
                    }
                }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val second =
                pool.submit<Result<Int>> {
                    f.s.run { if (fenceFirst) f.receipt(operation) else f.fence(operation) }
                }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            var waiting = false
            while (!waiting && System.nanoTime() < deadline) {
                waiting =
                    f.s.owner.queryForObject(
                        "select exists(select 1 from pg_stat_activity where usename='space_gateway_fixture' and wait_event='advisory')",
                        Boolean::class.java,
                    ) == true
                if (!waiting) Thread.sleep(10)
            }
            assertTrue(waiting)
            release.countDown()
            if (rollbackFirst) {
                assertInstanceOf(Result.Failed::class.java, first.get(10, TimeUnit.SECONDS))
                success(second.get(10, TimeUnit.SECONDS))
            } else {
                success(first.get(10, TimeUnit.SECONDS))
                assertInstanceOf(Result.Failed::class.java, second.get(10, TimeUnit.SECONDS))
            }
            val expectedFence = if (rollbackFirst) !fenceFirst else fenceFirst
            assertEquals(
                if (expectedFence) 1 else 0,
                f.s.owner.queryForObject(
                    "select count(*) from collaboration_space_room_request_fences where operation_id=?",
                    Int::class.java,
                    operation,
                ),
            )
            assertEquals(
                if (expectedFence) 0 else 1,
                f.s.owner.queryForObject(
                    "select count(*) from operation_receipts where operation_id=?",
                    Int::class.java,
                    operation,
                ),
            )
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    private var sharedFixture: Fixture? = null

    private inner class Fixture(upgrade: Boolean = true) {
        val s = SpaceGatewayMigrationFixture(postgres).also { it.migrate() }
        val peer = UUID.randomUUID()
        val conversation = UUID.randomUUID()
        val operation = UUID.randomUUID()
        val hash = "a".repeat(64)

        init {
            s.owner.update(
                "insert into accounts(id,email,display_name) values(?,?,'Fictional fence peer')",
                peer,
                "$peer@example.test",
            )
            s.owner.update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                s.company,
                peer,
            )
            s.owner.update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                s.company,
                peer,
            )
            Flyway.configure().dataSource(s.source).target("137").load().migrate()
            s.owner.execute(
                "grant select,insert,update,delete on all tables in schema public to space_gateway_fixture,space_gateway_worker"
            )
            val pair = listOf(s.account, peer).sortedBy(UUID::toString)
            success(
                s.run {
                    s.runtime.update(
                        "insert into collaboration_space_direct_conversations(space_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,?::timestamptz)",
                        s.space,
                        conversation,
                        pair[0],
                        pair[1],
                        s.account,
                        s.at.toString(),
                    )
                }
            )
            if (upgrade) upgrade()
        }

        fun upgrade() {
            Flyway.configure().dataSource(s.source).target("138").load().migrate()
            s.owner.execute(
                "grant select,insert,update,delete on all tables in schema public to space_gateway_fixture,space_gateway_worker"
            )
        }

        fun rows() = s.runtime.queryForList("select * from collaboration_space_room_request_fences")

        fun fence(id: UUID = operation, overrides: Map<String, String> = emptyMap()): Int {
            val fields =
                linkedMapOf(
                        "account_id" to "'${s.account}'::uuid",
                        "operation_id" to "'$id'::uuid",
                        "space_id" to "'${s.space}'::uuid",
                        "conversation_id" to "'$conversation'::uuid",
                        "device_id" to "'${s.device}'::uuid",
                        "expected_device_version" to "1",
                        "expected_request_account_id" to "null",
                        "expected_request_revision" to "null",
                        "payload_hash" to "'$hash'",
                        "fence_id" to "gen_random_uuid()",
                        "created_at" to "clock_timestamp()",
                    )
                    .apply { putAll(overrides) }
            return s.runtime.update(
                "insert into collaboration_space_room_request_fences(${fields.keys.joinToString()}) values(${fields.values.joinToString()})"
            )
        }

        fun receipt(id: UUID, legacy: Boolean = false, hash: String = this.hash): Int =
            s.runtime.update(
                "insert into operation_receipts(company_id,actor_id,operation,operation_id,payload_hash,resource_id,response) values(null,?,?,?,?,?,'{\"version\":0}'::jsonb)",
                s.account,
                if (legacy) "collaboration.space_room_setup"
                else "collaboration.space_room_setup.v2",
                id,
                hash,
                conversation,
            )
    }

    private fun <T> success(value: Result<T>): T = (value as Result.Success).value
}
