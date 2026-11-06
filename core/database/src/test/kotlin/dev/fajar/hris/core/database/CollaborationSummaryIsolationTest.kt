package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.support.JdbcTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSummaryIsolationTest {
    companion object {
        @Container
        @JvmStatic
        val postgres =
            PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

        @BeforeAll
        @JvmStatic
        fun schema() {
            val source =
                DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            Flyway.configure().dataSource(source).load().migrate()
            val sql = JdbcTemplate(source)
            for (role in listOf("summary_api", "summary_worker")) {
                sql.execute(
                    "create role $role login password 'summary-fixture-only' nosuperuser nobypassrls"
                )
                sql.execute("grant usage on schema public to $role")
                sql.execute(
                    "grant select,insert,update,delete on all tables in schema public to $role"
                )
            }
            sql.execute("grant hris_worker_capability to summary_worker")
        }
    }

    private val ownerSource =
        DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
    private val apiSource =
        DriverManagerDataSource(postgres.jdbcUrl, "summary_api", "summary-fixture-only")
    private val workerSource =
        DriverManagerDataSource(postgres.jdbcUrl, "summary_worker", "summary-fixture-only")
    private val admin = JdbcTemplate(ownerSource)
    private val api = JdbcTemplate(apiSource)
    private val worker = JdbcTemplate(workerSource)
    private val apiTransactions = PostgresTransactionRunner(JdbcTransactionManager(apiSource), api)
    private val workerTransactions =
        PostgresTransactionRunner(JdbcTransactionManager(workerSource), worker)
    private val company = UUID.randomUUID()
    private val foreignCompany = UUID.randomUUID()
    private val first = UUID.randomUUID()
    private val peer = UUID.randomUUID()
    private val stranger = UUID.randomUUID()
    private val pair = UUID.randomUUID()
    private val actor
        get() = Actor(first, company, emptySet(), Instant.now(), UUID.randomUUID())

    private val background
        get() = actor.copy(accountId = UUID(0, 0))

    @BeforeEach
    fun fixture() {
        for (id in listOf(company, foreignCompany)) {
            admin.update(
                "insert into companies(id,code,name,timezone) values(?,?,'Fictional summary company','UTC')",
                id,
                id.toString().take(20),
            )
        }
        for (id in listOf(first, peer, stranger)) account(id)
        insertPair(company, pair, first, peer)
    }

    private fun account(id: UUID) {
        admin.update(
            "insert into accounts(id,email,display_name) values(?,?,'Fictional participant')",
            id,
            "$id@example.test",
        )
    }

    private fun insertPair(companyId: UUID, id: UUID, owner: UUID, other: UUID) {
        // PostgreSQL UUID ordering compares unsigned bytes, rather than UUID.compareTo.
        val ordered = listOf(owner, other).sortedBy { it.toString() }
        admin.update(
            "insert into collaboration_direct_conversations(company_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,now())",
            companyId,
            id,
            ordered[0],
            ordered[1],
            owner,
        )
    }

    private fun <T> scoped(actor: Actor = background, block: () -> T): T =
        (workerTransactions.run(actor) { Result.Success(block()) } as Result.Success).value

    private fun save(owner: UUID = first, other: UUID = peer, conversation: UUID = pair) {
        worker.update(
            "insert into collaboration_direct_summary_records(company_id,conversation_id,owner_account_id,peer_account_id,version,peer_display_name) values(?,?,?,?,0,'Fictional participant')",
            company,
            conversation,
            owner,
            other,
        )
    }

    private fun readCount(reader: Actor): Int =
        (apiTransactions.run(reader) {
                Result.Success(
                    api.queryForObject(
                        "select count(*) from collaboration_direct_summary_records",
                        Int::class.java,
                    )!!
                )
            } as Result.Success)
            .value

    @Test
    fun projectionAndSourceRespectOwnerCompanyAndWorkerBoundaries() {
        scoped {
            save()
            save(peer, first)
        }
        assertEquals(1, readCount(actor))
        assertEquals(1, readCount(actor.copy(accountId = peer)))
        assertEquals(0, readCount(actor.copy(accountId = stranger)))
        assertEquals(0, readCount(actor.copy(companyId = foreignCompany)))
        assertEquals(
            0,
            worker.queryForObject(
                "select count(*) from collaboration_direct_summary_records",
                Int::class.java,
            ),
        )
        assertEquals(
            0,
            worker.queryForObject(
                "select count(*) from collaboration_direct_conversations",
                Int::class.java,
            ),
        )
        assertEquals(
            Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied")),
            apiTransactions.run(actor) {
                api.update(
                    "insert into collaboration_direct_summary_records(company_id,conversation_id,owner_account_id,peer_account_id,version,peer_display_name) values(?,?,?,?,0,'Forged')",
                    company,
                    pair,
                    first,
                    peer,
                )
                Result.Success(Unit)
            },
        )
        assertEquals(
            Result.Success(0),
            apiTransactions.run(actor) {
                Result.Success(
                    api.update(
                        "update collaboration_direct_summary_records set version=version+1,peer_display_name='Forged'"
                    )
                )
            },
        )
        assertEquals(
            Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied")),
            workerTransactions.run(background) {
                Result.Success(
                    worker.update(
                        "delete from collaboration_direct_summary_records where company_id=?",
                        company,
                    )
                )
            },
        )
        assertEquals(
            0,
            scoped {
                worker.update(
                    "update collaboration_direct_conversations set version=1 where company_id=?",
                    company,
                )
            },
        )
        assertThrows(DataAccessException::class.java) {
            admin.update(
                "update collaboration_direct_conversations set version=1 where company_id=?",
                company,
            )
        }
    }

    @Test
    fun rawRevisionEmitsOwnedChangeOnlyAfterPublicationAndRejectsNoop() {
        scoped { save() }
        assertNull(
            admin.queryForObject(
                "select sequence from mobile_sync_changes where collection='COLLABORATION_DIRECT' and company_id=?",
                Long::class.java,
                company,
            )
        )
        assertEquals(
            Result.Failed(Failure(FailureKind.CONFLICT, "data_conflict")),
            workerTransactions.run(background) {
                Result.Success(
                    worker.update(
                        "update collaboration_direct_summary_records set version=version+1 where company_id=?",
                        company,
                    )
                )
            },
        )
        scoped {
            worker.update(
                "update collaboration_direct_summary_records set version=1,peer_display_name='Updated participant' where company_id=?",
                company,
            )
        }
        scoped(background.copy(companyId = null)) {
            worker.queryForObject("select publish_mobile_sync_changes(200)", Int::class.java)
        }
        val rows =
            admin.queryForList(
                "select resource_id,owner_account_id,resource_version,operation,sequence from mobile_sync_changes where collection='COLLABORATION_DIRECT' and company_id=? order by sequence",
                company,
            )
        assertEquals(2, rows.size)
        assertEquals(listOf(0L, 1L), rows.map { it["resource_version"] })
        assertTrue(
            rows.all {
                it["resource_id"] == pair &&
                    it["owner_account_id"] == first &&
                    it["operation"] == "UPSERT" &&
                    it["sequence"] != null
            }
        )
        assertEquals(
            0L,
            admin.queryForObject(
                "select version from collaboration_direct_conversations where company_id=?",
                Long::class.java,
                company,
            ),
        )
    }

    @Test
    fun withdrawalAndReturnRetainCanonicalIdentityWithVersionedTombstone() {
        scoped {
            save()
            worker.update(
                "update collaboration_direct_summary_records set version=1,peer_display_name=null where company_id=?",
                company,
            )
            worker.update(
                "update collaboration_direct_summary_records set version=2,peer_display_name='Returned participant' where company_id=?",
                company,
            )
        }
        assertEquals(
            listOf("UPSERT", "DELETE", "UPSERT"),
            admin.queryForList(
                "select operation from mobile_sync_changes where collection='COLLABORATION_DIRECT' and company_id=? order by resource_version",
                String::class.java,
                company,
            ),
        )
        assertEquals(
            listOf(0L, 1L, 2L),
            admin.queryForList(
                "select resource_version from mobile_sync_changes where collection='COLLABORATION_DIRECT' and company_id=? order by resource_version",
                Long::class.java,
                company,
            ),
        )
        assertEquals(
            1,
            admin.queryForObject(
                "select count(*) from collaboration_direct_conversations where company_id=? and id=?",
                Int::class.java,
                company,
                pair,
            ),
        )
    }

    @Test
    fun failedPublicationTransactionRetainsPendingQueueAndNoPartialFacts() {
        val failure = Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_rollback"))
        assertEquals(
            failure,
            workerTransactions.run(background) {
                save()
                worker.update(
                    "update collaboration_summary_refresh_queue set due_at=null where company_id=? and conversation_id=?",
                    company,
                    pair,
                )
                failure
            },
        )
        assertEquals(
            0,
            admin.queryForObject(
                "select count(*) from collaboration_direct_summary_records where company_id=?",
                Int::class.java,
                company,
            ),
        )
        assertEquals(
            0,
            admin.queryForObject(
                "select count(*) from mobile_sync_changes where collection='COLLABORATION_DIRECT' and company_id=?",
                Int::class.java,
                company,
            ),
        )
        assertEquals(
            1,
            admin.queryForObject(
                "select count(*) from collaboration_summary_refresh_queue where company_id=? and due_at is not null",
                Int::class.java,
                company,
            ),
        )
    }

    @Test
    fun nameCaptureIsOneRowAndWorkerFanoutIsBoundedAcrossCompanies() {
        repeat(205) { index ->
            val other = UUID.randomUUID()
            account(other)
            insertPair(
                if (index % 2 == 0) company else foreignCompany,
                UUID.randomUUID(),
                first,
                other,
            )
        }
        admin.update("update accounts set display_name='Renamed fixture' where id=?", first)
        admin.update("update accounts set display_name=display_name where id=?", peer)
        assertEquals(
            1,
            admin.queryForObject(
                "select count(*) from collaboration_summary_account_queue where account_id in (?,?)",
                Int::class.java,
                first,
                peer,
            ),
        )
        assertThrows(DataAccessException::class.java) {
            api.queryForList(
                "select * from collaboration_summary_account_targets(?,null,null,201)",
                first,
            )
        }
        assertThrows(DataAccessException::class.java) {
            worker.queryForList(
                "select * from collaboration_summary_account_targets(?,null,null,202)",
                first,
            )
        }
        val page =
            worker.queryForList(
                "select * from collaboration_summary_account_targets(?,null,null,201)",
                first,
            )
        assertEquals(201, page.size)
        val last = page.last()
        val remaining =
            worker.queryForList(
                "select * from collaboration_summary_account_targets(?,?,?,201)",
                first,
                last["company_id"],
                last["conversation_id"],
            )
        assertEquals(5, remaining.size)
        assertEquals(206, (page + remaining).map { it["conversation_id"] }.toSet().size)
        assertEquals(
            setOf(company, foreignCompany),
            (page + remaining).map { it["company_id"] }.toSet(),
        )
        assertFalse(
            admin.queryForObject(
                "select pg_has_role('summary_api','hris_collaboration_summary_capture','MEMBER')",
                Boolean::class.java,
            )!!
        )
        assertFalse(
            admin.queryForObject(
                "select pg_has_role('summary_worker','hris_collaboration_summary_capture','MEMBER')",
                Boolean::class.java,
            )!!
        )
        assertFalse(
            admin.queryForObject(
                "select has_column_privilege('hris_collaboration_summary_capture','accounts','display_name','SELECT')",
                Boolean::class.java,
            )!!
        )
        assertFalse(
            admin.queryForObject(
                "select rolcanlogin or rolsuper or rolbypassrls from pg_roles where rolname='hris_collaboration_summary_capture'",
                Boolean::class.java,
            )!!
        )
    }

    @Test
    fun laterRenameCannotBeErasedByAnInFlightFanoutCompletion() {
        admin.update("update accounts set display_name='First rename' where id=?", peer)
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val completing =
                pool.submit {
                    scoped(background.copy(companyId = null)) {
                        worker.queryForObject(
                            "select account_id from collaboration_summary_account_queue where account_id=? for update",
                            UUID::class.java,
                            peer,
                        )
                        locked.countDown()
                        check(release.await(15, TimeUnit.SECONDS))
                        worker.update(
                            "update collaboration_summary_account_queue set due_at=null,after_company_id=null,after_conversation_id=null where account_id=?",
                            peer,
                        )
                    }
                }
            assertTrue(locked.await(10, TimeUnit.SECONDS))
            val rename =
                pool.submit {
                    admin.update("update accounts set display_name='Later rename' where id=?", peer)
                }
            try {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (
                    System.nanoTime() < deadline &&
                        admin.queryForObject(
                            "select count(*) from pg_stat_activity where wait_event_type='Lock' and query like 'update accounts set display_name=%'",
                            Int::class.java,
                        ) == 0
                ) Thread.sleep(20)
                assertFalse(rename.isDone)
            } finally {
                release.countDown()
            }
            completing.get(15, TimeUnit.SECONDS)
            rename.get(15, TimeUnit.SECONDS)
        }
        assertEquals(
            1,
            admin.queryForObject(
                "select count(*) from collaboration_summary_account_queue where account_id=? and due_at is not null and after_company_id is null and after_conversation_id is null",
                Int::class.java,
                peer,
            ),
        )
        assertEquals(
            "Later rename",
            admin.queryForObject(
                "select display_name from accounts where id=?",
                String::class.java,
                peer,
            ),
        )
    }

    @Test
    fun publicationPositionDoesNotSkipAProjectionTransactionThatCommitsLate() {
        val laterPair = UUID.randomUUID()
        insertPair(company, laterPair, first, stranger)
        val written = CountDownLatch(1)
        val release = CountDownLatch(1)
        Executors.newSingleThreadExecutor().use { pool ->
            val delayed =
                pool.submit {
                    scoped {
                        save()
                        written.countDown()
                        check(release.await(15, TimeUnit.SECONDS))
                    }
                }
            assertTrue(written.await(10, TimeUnit.SECONDS))
            try {
                scoped { save(first, stranger, laterPair) }
                scoped(background.copy(companyId = null)) {
                    worker.queryForObject(
                        "select publish_mobile_sync_changes(200)",
                        Int::class.java,
                    )
                }
                assertEquals(
                    0,
                    admin.queryForObject(
                        "select count(*) from mobile_sync_changes where company_id=? and resource_id=?",
                        Int::class.java,
                        company,
                        pair,
                    ),
                )
                assertNotNull(
                    admin.queryForObject(
                        "select sequence from mobile_sync_changes where company_id=? and resource_id=?",
                        Long::class.java,
                        company,
                        laterPair,
                    )
                )
            } finally {
                release.countDown()
            }
            delayed.get(15, TimeUnit.SECONDS)
        }
        scoped(background.copy(companyId = null)) {
            worker.queryForObject("select publish_mobile_sync_changes(200)", Int::class.java)
        }
        val published =
            admin.queryForList(
                "select resource_id from mobile_sync_changes where collection='COLLABORATION_DIRECT' and company_id=? order by sequence",
                UUID::class.java,
                company,
            )
        assertEquals(listOf(laterPair, pair), published)
    }

    @Test
    fun accountRenameRollbackAndApiQueueMutationDoNotLoseWork() {
        assertThrows(IllegalStateException::class.java) {
            TransactionTemplate(JdbcTransactionManager(ownerSource)).execute<Unit> {
                admin.update("update accounts set display_name='Rolled back name' where id=?", peer)
                error("fixture rollback")
            }
        }
        assertEquals(
            0,
            admin.queryForObject(
                "select count(*) from collaboration_summary_account_queue where account_id=?",
                Int::class.java,
                peer,
            ),
        )
        assertEquals(
            Result.Success(0),
            apiTransactions.run(actor) {
                Result.Success(
                    api.update(
                        "update collaboration_summary_refresh_queue set due_at=null where company_id=?",
                        company,
                    )
                )
            },
        )
        assertEquals(
            Result.Success(0),
            apiTransactions.run(actor) {
                Result.Success(
                    api.update(
                        "delete from collaboration_summary_refresh_queue where company_id=?",
                        company,
                    )
                )
            },
        )
        assertEquals(
            1,
            admin.queryForObject(
                "select count(*) from collaboration_summary_refresh_queue where company_id=? and due_at is not null",
                Int::class.java,
                company,
            ),
        )
    }
}
