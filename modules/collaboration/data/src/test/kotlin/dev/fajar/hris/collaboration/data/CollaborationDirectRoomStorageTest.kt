package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationDirectRoomDataSource
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationDirectRoomRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationDirectRoomStorageTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
        lateinit var owner: JdbcTemplate

        @BeforeAll
        @JvmStatic
        fun migrate() {
            val source =
                DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            owner = JdbcTemplate(source)
            Flyway.configure().dataSource(source).target("110").load().migrate()
            val account = UUID.randomUUID()
            owner.update(
                "insert into accounts(id,email,display_name) values(?,?,'Pre-upgrade room fixture')",
                account,
                "$account@example.test",
            )
            val before =
                owner.queryForList(
                    "select row_to_json(a)::text as snapshot from accounts a order by id"
                )
            Flyway.configure().dataSource(source).target("111").load().migrate()
            assertEquals(
                before,
                owner.queryForList(
                    "select row_to_json(a)::text as snapshot from accounts a order by id"
                ),
            )
            owner.execute(
                "create role direct_room_fixture login password 'direct-room-fixture-only'"
            )
            owner.execute("grant usage on schema public to direct_room_fixture")
            owner.execute(
                "grant select,insert,update,delete on all tables in schema public to direct_room_fixture"
            )
        }
    }

    @Test
    fun retainedPointerAndBindingAreVisibleOnlyToTheirCompanyPair() {
        val f = Fixture()
        val pointer = f.pointer()
        assertEquals(Result.Success(Unit), f.run { f.repository.create(pointer) })
        for (reader in listOf(f.actor, f.actor.copy(accountId = f.peer))) {
            assertEquals(
                Result.Success(pointer),
                f.run(reader) { f.repository.provisioning(f.company, f.conversation) },
            )
        }
        for (reader in
            listOf(
                f.actor.copy(accountId = UUID.randomUUID()),
                f.actor.copy(companyId = UUID.randomUUID()),
            )) {
            assertEquals(
                Result.Success(null),
                f.run(reader) { f.repository.provisioning(f.company, f.conversation) },
            )
        }
        assertFailed(f.run { f.repository.publish(f.binding(pointer)) }) // Still QUEUED.
        f.running(pointer.jobId)
        val binding = f.binding(pointer)
        assertEquals(Result.Success(Unit), f.run { f.repository.publish(binding) })
        for (reader in listOf(f.actor, f.actor.copy(accountId = f.peer))) {
            assertEquals(
                Result.Success(binding),
                f.run(reader) { f.repository.binding(f.company, f.conversation) },
            )
        }
        assertEquals(
            Result.Success(null),
            f.run(f.actor.copy(accountId = UUID.randomUUID())) {
                f.repository.binding(f.company, f.conversation)
            },
        )
        assertEquals(
            0,
            f.runtime.queryForObject(
                "select count(*) from collaboration_direct_room_bindings",
                Int::class.java,
            ),
        )
        // Ordinary native-session cleanup removes only the live device binding.
        owner.update("delete from native_sessions where id=?", f.parent)
        assertEquals(
            Result.Success(pointer),
            f.run { f.repository.provisioning(f.company, f.conversation) },
        )
        assertEquals(
            Result.Success(binding),
            f.run(f.actor.copy(accountId = f.peer)) {
                f.repository.binding(f.company, f.conversation)
            },
        )
        assertEquals(
            Result.Success(0),
            f.run {
                Result.Success(
                    f.runtime.update(
                        "delete from collaboration_direct_room_bindings where company_id=?",
                        f.company,
                    )
                )
            },
        )
        assertThrows(DataAccessException::class.java) {
            owner.update(
                "delete from collaboration_direct_room_bindings where company_id=?",
                f.company,
            )
        }
        assertThrows(DataAccessException::class.java) {
            owner.update(
                "delete from collaboration_direct_room_provisioning where company_id=?",
                f.company,
            )
        }
    }

    @Test
    fun retryRequiresTerminalOldJobAndKeepsCreatorWithANewLiveDevice() {
        val f = Fixture()
        val initial = f.pointer()
        assertEquals(Result.Success(Unit), f.run { f.repository.create(initial) })
        val next = f.pointer(version = 1, createdAt = initial.createdAt)
        assertFailed(f.run { f.repository.replace(next, 0) })
        f.failed(initial.jobId)
        assertFailed(f.run(f.actor.copy(accountId = f.peer)) { f.repository.replace(next, 0) })
        assertFailed(f.run { f.repository.replace(next, 1) })
        val replacementParent = UUID.randomUUID()
        val replacementDevice = f.device(replacementParent)
        val replacement =
            f.pointer(
                version = 1,
                createdAt = initial.createdAt,
                device = replacementDevice,
                parent = replacementParent,
            )
        owner.update("delete from native_sessions where id=?", f.parent)
        assertEquals(
            Result.Success(Unit),
            f.run {
                f.repository.lock(f.company, f.conversation).flatMap {
                    f.repository.replace(replacement, 0)
                }
            },
        )
        assertEquals(
            Result.Success(replacement),
            f.run { f.repository.provisioning(f.company, f.conversation) },
        )
        assertThrows(DataAccessException::class.java) {
            owner.update(
                "update collaboration_direct_room_provisioning set account_id=? where company_id=?",
                f.peer,
                f.company,
            )
        }
        assertFailed(f.run { f.repository.publish(f.binding(initial)) })
        f.running(replacement.jobId)
        assertEquals(Result.Success(Unit), f.run { f.repository.publish(f.binding(replacement)) })
        f.failed(replacement.jobId)
        val afterPublication =
            f.pointer(
                version = 2,
                createdAt = initial.createdAt,
                device = replacementDevice,
                parent = replacementParent,
            )
        assertFailed(f.run { f.repository.replace(afterPublication, 1) })
    }

    @Test
    fun rejectsMismatchedJobScopeAndPublicationGenerationAndDuplicateProviderRoom() {
        val f = Fixture()
        val initial = f.pointer()
        assertFailed(f.run { f.repository.create(initial.copy(accountId = f.peer)) })
        assertFailed(f.run { f.repository.create(initial.copy(version = 1)) })
        assertFailed(
            f.run { f.repository.create(initial.copy(conversationId = UUID.randomUUID())) }
        )
        assertEquals(Result.Success(Unit), f.run { f.repository.create(initial) })
        f.running(initial.jobId)
        val binding = f.binding(initial)
        assertFailed(f.run { f.repository.publish(binding.copy(provisioningVersion = 1)) })
        assertFailed(f.run { f.repository.publish(binding.copy(accountId = f.peer)) })
        assertEquals(Result.Success(Unit), f.run { f.repository.publish(binding) })
        val other = Fixture()
        val pointer = other.pointer()
        assertEquals(Result.Success(Unit), other.run { other.repository.create(pointer) })
        other.running(pointer.jobId)
        assertFailed(
            other.run {
                other.repository.publish(
                    other.binding(pointer).copy(matrixRoomId = binding.matrixRoomId)
                )
            }
        )
        assertThrows(DataAccessException::class.java) {
            owner.update(
                "update collaboration_direct_room_bindings set matrix_room_id='!replacement' where company_id=?",
                f.company,
            )
        }
    }

    @Test
    fun competingRetriesPublishOnlyOneNewPointerUnderOwnedRoomGuard() {
        val f = Fixture()
        val initial = f.pointer()
        assertEquals(Result.Success(Unit), f.run { f.repository.create(initial) })
        f.failed(initial.jobId)
        val alternatives = (1..2).map { f.pointer(version = 1, createdAt = initial.createdAt) }
        val entered = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val work =
                alternatives.map { value ->
                    executor.submit<Result<Unit>> {
                        entered.countDown()
                        assertTrue(start.await(5, TimeUnit.SECONDS))
                        f.run {
                            f.repository.lock(f.company, f.conversation).flatMap {
                                f.repository.replace(value, 0)
                            }
                        }
                    }
                }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            start.countDown()
            val results = work.map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is Result.Success })
            assertEquals(1, results.count { it is Result.Failed })
            val actual =
                (f.run { f.repository.provisioning(f.company, f.conversation) } as Result.Success)
                    .value
            assertTrue(actual in alternatives)
        } finally {
            start.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun failedResultAndInterruptionRollBackPublicationAndJobProgress() {
        val f = Fixture()
        val pointer = f.pointer()
        assertEquals(Result.Success(Unit), f.run { f.repository.create(pointer) })
        f.running(pointer.jobId)
        val failure = Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_interrupted"))
        assertEquals(
            failure,
            f.run {
                f.repository.publish(f.binding(pointer)).flatMap {
                    f.runtime.update(
                        "update background_jobs set completed_items=1 where id=?",
                        pointer.jobId,
                    )
                    failure
                }
            },
        )
        assertEquals(
            0,
            owner.queryForObject(
                "select completed_items from background_jobs where id=?",
                Int::class.java,
                pointer.jobId,
            ),
        )
        assertEquals(
            Result.Success(null),
            f.run { f.repository.binding(f.company, f.conversation) },
        )
        try {
            assertThrows(InterruptedException::class.java) {
                f.run {
                    f.repository.publish(f.binding(pointer)).flatMap<Unit, Unit> {
                        throw InterruptedException("fixture cancellation")
                    }
                }
            }
        } finally {
            Thread.interrupted()
        }
        assertEquals(
            Result.Success(null),
            f.run { f.repository.binding(f.company, f.conversation) },
        )
        assertEquals(Result.Success(Unit), f.run { f.repository.publish(f.binding(pointer)) })
    }

    @Test
    fun dumpRestoreRetainsRoomIdentityPointerAndParticipantRls() {
        val f = Fixture()
        val pointer = f.pointer()
        assertEquals(Result.Success(Unit), f.run { f.repository.create(pointer) })
        f.running(pointer.jobId)
        val binding = f.binding(pointer)
        assertEquals(Result.Success(Unit), f.run { f.repository.publish(binding) })
        fun command(vararg args: String) {
            val result = postgres.execInContainer(*args)
            assertEquals(0, result.exitCode, result.stderr)
        }
        command(
            "pg_dump",
            "-U",
            postgres.username,
            "-d",
            postgres.databaseName,
            "-Fc",
            "-f",
            "/tmp/direct-room.dump",
        )
        command("createdb", "-U", postgres.username, "direct_room_restored")
        command(
            "pg_restore",
            "-U",
            postgres.username,
            "-d",
            "direct_room_restored",
            "--exit-on-error",
            "/tmp/direct-room.dump",
        )
        val url =
            postgres.jdbcUrl.substringBefore('?').substringBeforeLast('/') + "/direct_room_restored"
        val restoredSource =
            DriverManagerDataSource(url, "direct_room_fixture", "direct-room-fixture-only")
        val restored = JdbcTemplate(restoredSource)
        val tx = PostgresTransactionRunner(JdbcTransactionManager(restoredSource), restored)
        val repository =
            StoredCollaborationDirectRoomRepository(
                PostgresCollaborationDirectRoomDataSource(
                    DSL.using(TransactionAwareDataSourceProxy(restoredSource), SQLDialect.POSTGRES)
                )
            )
        assertEquals(
            Result.Success(pointer),
            tx.run(f.actor) { repository.provisioning(f.company, f.conversation) },
        )
        assertEquals(
            Result.Success(binding),
            tx.run(f.actor.copy(accountId = f.peer)) {
                repository.binding(f.company, f.conversation)
            },
        )
        assertEquals(
            Result.Success(null),
            tx.run(f.actor.copy(accountId = UUID.randomUUID())) {
                repository.binding(f.company, f.conversation)
            },
        )
        assertEquals(
            Result.Success(null),
            tx.run(f.actor.copy(companyId = UUID.randomUUID())) {
                repository.provisioning(f.company, f.conversation)
            },
        )
        val restoredOwner =
            JdbcTemplate(DriverManagerDataSource(url, postgres.username, postgres.password))
        assertThrows(DataAccessException::class.java) {
            restoredOwner.update(
                "delete from collaboration_direct_room_bindings where company_id=?",
                f.company,
            )
        }
        assertEquals(
            0,
            restored.queryForObject(
                "select count(*) from collaboration_direct_room_bindings",
                Int::class.java,
            ),
        )
    }

    private fun assertFailed(result: Result<*>) {
        assertInstanceOf(Result.Failed::class.java, result)
    }

    private class Fixture {
        val company = UUID.randomUUID()
        val account = UUID.randomUUID()
        val peer = UUID.randomUUID()
        val parent = UUID.randomUUID()
        val conversation = UUID.randomUUID()
        val now: Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val actor = Actor(account, company, emptySet(), now, UUID.randomUUID())
        private val source =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "direct_room_fixture",
                "direct-room-fixture-only",
            )
        val runtime = JdbcTemplate(source)
        private val transactions =
            PostgresTransactionRunner(JdbcTransactionManager(source), runtime)
        val repository =
            StoredCollaborationDirectRoomRepository(
                PostgresCollaborationDirectRoomDataSource(
                    DSL.using(TransactionAwareDataSourceProxy(source), SQLDialect.POSTGRES)
                )
            )
        val device: UUID

        init {
            owner.update(
                "insert into companies(id,code,name,timezone) values(?,?,'Fictional room company','UTC')",
                company,
                company.toString().replace("-", ""),
            )
            for (id in listOf(account, peer)) owner.update(
                "insert into accounts(id,email,display_name) values(?,?,'Fictional room participant')",
                id,
                "$id@example.test",
            )
            val pair = listOf(account, peer).sortedBy(UUID::toString)
            owner.update(
                "insert into collaboration_direct_conversations(company_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,?)",
                company,
                conversation,
                pair[0],
                pair[1],
                account,
                java.sql.Timestamp.from(now),
            )
            device = device(parent)
        }

        fun device(parent: UUID): UUID {
            val device = UUID.randomUUID()
            val hash = UUID.randomUUID().toString().replace("-", "").repeat(2)
            owner.update(
                "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,access_hash,access_expires_at,credential_version) values(?,?,?,now(),now()+interval '1 day',?,now()+interval '15 minutes',0)",
                parent,
                account,
                hash,
                hash,
            )
            owner.update(
                "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional room device','PROVISIONING',0,?,?)",
                company,
                device,
                account,
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now),
            )
            owner.update(
                "insert into collaboration_device_sessions(company_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?)",
                company,
                device,
                account,
                parent,
                java.sql.Timestamp.from(now),
            )
            owner.update(
                "update collaboration_devices set state='ACTIVE',version=1 where company_id=? and id=?",
                company,
                device,
            )
            return device
        }

        fun pointer(
            version: Long = 0,
            createdAt: Instant = now,
            device: UUID = this.device,
            parent: UUID = this.parent,
        ): CollaborationDirectRoomProvisioning {
            val id = UUID.randomUUID()
            val at = now.plusSeconds(version)
            owner.update(
                """insert into background_jobs(id,company_id,actor_id,kind,operation_id,request,authenticated_at,credential_version,correlation_id,created_at,total_items)
                values(?,?,?,'COLLABORATION_DIRECT_PROVISION',?,jsonb_build_object('conversationId',?::text,'deviceId',?::text,'nativeSessionId',?::text,'platform','ANDROID','clientBuild','5'),?,0,?,?,1)""",
                id,
                company,
                account,
                UUID.randomUUID(),
                conversation,
                device,
                parent,
                java.sql.Timestamp.from(now),
                UUID.randomUUID(),
                java.sql.Timestamp.from(at),
            )
            return CollaborationDirectRoomProvisioning(
                company,
                conversation,
                account,
                device,
                id,
                version,
                createdAt,
                at,
            )
        }

        fun binding(pointer: CollaborationDirectRoomProvisioning) =
            CollaborationDirectRoomBinding(
                company,
                conversation,
                account,
                pointer.jobId,
                pointer.version,
                "proof.invalid",
                "!${UUID.randomUUID()}",
                pointer.updatedAt.plusSeconds(1),
            )

        fun running(id: UUID) {
            owner.update(
                "update background_jobs set status='RUNNING',attempts=1,lease_owner=gen_random_uuid(),lease_token=gen_random_uuid(),lease_until=now()+interval '1 minute' where id=?",
                id,
            )
        }

        fun failed(id: UUID) {
            owner.update(
                "update background_jobs set status='FAILED',lease_owner=null,lease_token=null,lease_until=null,finished_at=now() where id=?",
                id,
            )
        }

        fun <T> run(actor: Actor = this.actor, work: () -> Result<T>): Result<T> =
            transactions.run(actor, work)
    }
}
