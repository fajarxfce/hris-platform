package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.*
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.*
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.Assertions.*
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
class CollaborationOriginalPairRouteMigrationTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun upgradeReservesExactExistingHistoryAndDifferentCanonicalPairsRemainSeparate() {
        val f = fixture()
        val old = PairFixture(f)
        val group = PairFixture(f)
        val different = PairFixture(f, differentCompany = true)
        val fresh = PairFixture(f, fresh = true)
        success(old.run(old.companyActor) { old.original() })
        for (p in listOf(group, different, fresh)) success(p.run { p.intent() })
        migrate(f)
        assertEquals(
            2,
            f.owner.queryForObject(
                "select count(*) from collaboration_original_pair_routes",
                Int::class.java,
            ),
        )
        assertEquals("LEGACY", success(old.run { old.routes() }).single()["origin"])
        assertEquals(
            "SPACE",
            success(group.run(group.companyActor) { group.routes() }).single()["origin"],
        )
        assertInstanceOf(Result.Failed::class.java, old.run { old.intent() })
        assertInstanceOf(
            Result.Failed::class.java,
            group.run(group.companyActor) { group.original() },
        )
        for (p in listOf(different, fresh)) {
            assertTrue(success(p.run { p.routes() }).isEmpty())
            success(p.run(p.companyActor) { p.original() })
        }
        assertEquals(
            4,
            f.owner.queryForObject(
                "select count(*) from collaboration_original_pair_routes",
                Int::class.java,
            ),
        )
    }

    @Test
    fun competingOldAndGroupInsertsHaveOneWinnerEvenWithFixedSnapshotAndRollback() {
        val f = fixture()
        migrate(f)
        val pool = Executors.newFixedThreadPool(2)
        try {
            for (isolation in
                listOf(
                    Connection.TRANSACTION_READ_COMMITTED,
                    Connection.TRANSACTION_REPEATABLE_READ,
                )) {
                for (firstOld in listOf(true, false)) for (rollback in listOf(false, true)) {
                    val p = PairFixture(f)
                    val held = CountDownLatch(1)
                    val release = CountDownLatch(1)
                    try {
                        fun insert(old: Boolean) = if (old) p.original() else p.intent()
                        val first =
                            pool.submit<Result<Int>> {
                                isolated(
                                    f,
                                    if (firstOld) p.companyActor else p.actor,
                                    isolation,
                                    rollback,
                                ) {
                                    insert(firstOld)
                                    held.countDown()
                                    assertTrue(release.await(10, TimeUnit.SECONDS))
                                    1
                                }
                            }
                        assertTrue(held.await(10, TimeUnit.SECONDS))
                        val second =
                            pool.submit<Result<Int>> {
                                isolated(f, if (firstOld) p.actor else p.companyActor, isolation) {
                                    insert(!firstOld)
                                }
                            }
                        var waited = false
                        repeat(200) {
                            if (!waited) {
                                waited =
                                    f.owner.queryForObject(
                                        "select exists(select 1 from pg_stat_activity where wait_event_type='Lock' and (query like 'insert into collaboration_direct_conversations%' or query like 'insert into collaboration_space_room_intents%'))",
                                        Boolean::class.java,
                                    ) == true
                                if (!waited) TimeUnit.MILLISECONDS.sleep(10)
                            }
                        }
                        assertTrue(
                            waited,
                            "$isolation/$firstOld/$rollback must wait on the actual reservation",
                        )
                        release.countDown()
                        success(first.get(10, TimeUnit.SECONDS))
                        val last = second.get(10, TimeUnit.SECONDS)
                        if (rollback) success(last)
                        else assertInstanceOf(Result.Failed::class.java, last)
                        val oldWins = if (rollback) !firstOld else firstOld
                        val routes = success(p.run { p.routes() })
                        assertEquals(1, routes.size)
                        assertEquals(if (oldWins) "LEGACY" else "SPACE", routes.single()["origin"])
                        assertEquals(
                            if (oldWins) 1 else 0,
                            f.owner.queryForObject(
                                "select count(*) from collaboration_direct_conversations where id=?",
                                Int::class.java,
                                p.originalId,
                            ),
                        )
                        assertEquals(
                            if (oldWins) 0 else 1,
                            f.owner.queryForObject(
                                "select count(*) from collaboration_space_room_intents where conversation_id=?",
                                Int::class.java,
                                p.groupId,
                            ),
                        )
                    } finally {
                        release.countDown()
                    }
                }
            }
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun routeCannotBeForgedDeletedReassignedOrReadByAnUnrelatedActor() {
        val f = fixture()
        migrate(f)
        val p = PairFixture(f)
        for (actor in listOf(p.actor, p.companyActor, f.anonymous)) {
            assertInstanceOf(
                Result.Failed::class.java,
                p.run(actor) {
                    f.runtime.update(
                        "insert into collaboration_original_pair_routes(original_company_id,first_account_id,second_account_id,origin,legacy_conversation_id,created_at) values(?,?,?,'LEGACY',?,?::timestamptz)",
                        p.company,
                        p.pair[0],
                        p.pair[1],
                        UUID.randomUUID(),
                        f.at.toString(),
                    )
                },
            )
            assertTrue(success(p.run { p.routes() }).isEmpty())
        }
        assertInstanceOf(
            Result.Failed::class.java,
            p.run {
                f.runtime.update(
                    "insert into collaboration_original_pair_routes(original_company_id,first_account_id,second_account_id,origin,space_id,space_conversation_id,created_at) values(?,?,?,'SPACE',?,?,?::timestamptz)",
                    p.company,
                    p.pair[0],
                    p.pair[1],
                    f.space,
                    p.groupId,
                    f.at.toString(),
                )
            },
        )
        assertTrue(success(p.run { p.routes() }).isEmpty())
        success(p.run { p.intent() })
        val expected = success(p.run { p.routes() })
        for (actor in
            listOf(
                p.companyActor,
                p.actor.copy(accountId = p.pair[1]),
                p.companyActor.copy(accountId = p.pair[1]),
            )) {
            assertEquals(expected, success(p.run(actor) { p.routes() }))
        }
        for (actor in
            listOf(f.anonymous, f.actor, p.companyActor.copy(companyId = UUID.randomUUID()))) {
            assertTrue(success(p.run(actor) { p.routes() }).isEmpty())
        }
        assertInstanceOf(
            Result.Failed::class.java,
            p.run { f.runtime.execute("set local role hris_collaboration_directory_reader") },
        )
        assertThrows(DataAccessException::class.java) {
            f.owner.update("delete from collaboration_original_pair_routes")
        }
        assertThrows(DataAccessException::class.java) {
            f.owner.update("update collaboration_original_pair_routes set created_at=created_at")
        }
        val unrelated = PairFixture(f)
        assertInstanceOf(
            Result.Failed::class.java,
            unrelated.run {
                f.runtime.update(
                    "insert into collaboration_original_pair_routes(original_company_id,first_account_id,second_account_id,origin,space_id,space_conversation_id,created_at) values(?,?,?,'SPACE',?,?,?::timestamptz)",
                    p.company,
                    unrelated.pair[0],
                    unrelated.pair[1],
                    f.space,
                    p.groupId,
                    f.at.toString(),
                )
            },
        )
        assertTrue(success(unrelated.run { unrelated.routes() }).isEmpty())
        assertEquals(expected, success(p.run { p.routes() }))
    }

    @Test
    fun boundOriginalIsAdoptedWithoutNewRouteAndArchiveRetainsBothKindsAfterParentPurge() {
        val f = fixture()
        migrate(f)
        val old = PairFixture(f)
        val group = PairFixture(f)
        success(old.run(old.companyActor) { old.original() })
        old.bindOriginal()
        val before = success(old.run { old.routes() })
        success(old.run { old.intent(legacy = true) })
        assertEquals(before, success(old.run { old.routes() }))
        success(group.run { group.intent() })
        for (parent in old.parents.values + group.parents.values) f.owner.update(
            "delete from native_sessions where id=?",
            parent,
        )
        val expected =
            f.owner.queryForList(
                "select * from collaboration_original_pair_routes order by original_company_id,first_account_id,second_account_id"
            )
        val owner = f.restore()
        assertEquals(
            expected,
            owner.queryForList(
                "select * from collaboration_original_pair_routes order by original_company_id,first_account_id,second_account_id"
            ),
        )
        assertEquals(
            true,
            owner.queryForObject(
                "select relrowsecurity and relforcerowsecurity from pg_class where oid='collaboration_original_pair_routes'::regclass",
                Boolean::class.java,
            ),
        )
        val source =
            DriverManagerDataSource(
                postgres.jdbcUrl.substringBeforeLast('/') + "/space_gateway_restored",
                "space_gateway_fixture",
                "isolated-fixture-only",
            )
        val sql = JdbcTemplate(source)
        val tx = PostgresTransactionRunner(JdbcTransactionManager(source), sql)
        assertEquals(
            before,
            success(
                tx.run(old.actor) {
                    safeDatabaseCall {
                        sql.queryForList(
                            "select * from collaboration_original_pair_routes where first_account_id=?",
                            old.pair[0],
                        )
                    }
                }
            ),
        )
        assertTrue(
            success(
                    tx.run(f.anonymous) {
                        safeDatabaseCall {
                            sql.queryForList("select * from collaboration_original_pair_routes")
                        }
                    }
                )
                .isEmpty()
        )
        assertThrows(DataAccessException::class.java) {
            owner.update("delete from collaboration_original_pair_routes")
        }
    }

    @Test
    fun conflictingPreexistingHistoriesAbortUpgradeInsteadOfChoosingOne() {
        val f = fixture()
        val p = PairFixture(f)
        success(p.run { p.intent() })
        success(p.run(p.companyActor) { p.original() }) // Legal before the new route fence.
        assertThrows(FlywayException::class.java) { migrate(f) }
        assertEquals(
            "131",
            f.owner.queryForObject(
                "select version from flyway_schema_history where success and version is not null order by installed_rank desc limit 1",
                String::class.java,
            ),
        )
        assertNull(
            f.owner.queryForObject(
                "select to_regclass('collaboration_original_pair_routes')::text",
                String::class.java,
            )
        )
        assertEquals(
            1,
            f.owner.queryForObject(
                "select count(*) from collaboration_direct_conversations where id=?",
                Int::class.java,
                p.originalId,
            ),
        )
        assertEquals(
            1,
            f.owner.queryForObject(
                "select count(*) from collaboration_space_room_intents where conversation_id=?",
                Int::class.java,
                p.groupId,
            ),
        )
    }

    private fun fixture() =
        SpaceGatewayMigrationFixture(postgres).also {
            it.migrate()
            Flyway.configure().dataSource(it.source).target("131").load().migrate()
            it.owner.execute(
                "grant select,insert,update,delete on all tables in schema public to space_gateway_fixture,space_gateway_worker"
            )
        }

    private fun migrate(f: SpaceGatewayMigrationFixture) {
        Flyway.configure().dataSource(f.source).target("132").load().migrate()
        f.owner.execute(
            "grant select,insert,update,delete on all tables in schema public to space_gateway_fixture,space_gateway_worker"
        )
    }

    private fun <T> isolated(
        f: SpaceGatewayMigrationFixture,
        actor: Actor,
        isolation: Int,
        rollback: Boolean = false,
        body: () -> T,
    ): Result<T> = safeDatabaseCall {
        val template =
            TransactionTemplate(JdbcTransactionManager(f.runtimeSource)).apply {
                isolationLevel = isolation
                timeout = 15
            }
        requireNotNull(
            template.execute { status ->
                f.runtime.execute("set local statement_timeout='10s'")
                f.runtime.execute("set local lock_timeout='5s'")
                f.runtime.queryForObject(
                    "select set_config('hris.actor_id',?,true)",
                    String::class.java,
                    actor.accountId.toString(),
                )
                f.runtime.queryForObject(
                    "select set_config('hris.company_id',?,true)",
                    String::class.java,
                    actor.companyId?.toString() ?: "",
                )
                assertEquals(
                    if (isolation == Connection.TRANSACTION_REPEATABLE_READ) "repeatable read"
                    else "read committed",
                    f.runtime.queryForObject("show transaction_isolation", String::class.java),
                )
                val value = body()
                if (rollback) status.setRollbackOnly()
                value
            }
        )
    }

    private fun <T> success(result: Result<T>): T = (result as Result.Success).value

    private inner class PairFixture(
        val f: SpaceGatewayMigrationFixture,
        differentCompany: Boolean = false,
        private val fresh: Boolean = false,
    ) {
        val company = f.company
        val pair = listOf(UUID.randomUUID(), UUID.randomUUID()).sortedBy(UUID::toString)
        val companies =
            mapOf(
                pair[0] to company,
                pair[1] to if (differentCompany) UUID.randomUUID() else company,
            )
        val devices = pair.associateWith { UUID.randomUUID() }
        val parents = pair.associateWith { UUID.randomUUID() }
        val originalId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val actor = f.actor.copy(accountId = pair[0])
        val companyActor = actor.copy(companyId = company)
        val at = f.at.toString()

        fun compact(id: UUID) = id.toString().replace("-", "")

        fun oldUser(account: UUID) =
            "@hris_${compact(companies.getValue(account))}_${compact(account)}:matrix.example.test"

        fun user(account: UUID) =
            if (fresh) "@hrisg_${compact(f.space)}_${compact(account)}:matrix.example.test"
            else oldUser(account)

        init {
            val db = f.owner
            if (differentCompany)
                db.update(
                    "insert into companies(id,code,name,timezone) values(?,?,'Fictional other route company','UTC')",
                    companies.getValue(pair[1]),
                    compact(companies.getValue(pair[1])),
                )
            for (account in pair) {
                val company = companies.getValue(account)
                val device = devices.getValue(account)
                val parent = parents.getValue(account)
                db.update(
                    "insert into accounts(id,email,display_name) values(?,?,'Fictional retained room participant')",
                    account,
                    "$account@example.test",
                )
                db.update(
                    "insert into company_memberships(company_id,account_id) values(?,?)",
                    company,
                    account,
                )
                db.update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                    company,
                    account,
                )
                db.update(
                    "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,credential_version,access_expires_at,created_at,rotated_at) values(?,?,?,?::timestamptz,?::timestamptz,0,?::timestamptz,?::timestamptz,?::timestamptz)",
                    parent,
                    account,
                    compact(parent).repeat(2),
                    f.at.minusSeconds(60).toString(),
                    f.at.plusSeconds(3600).toString(),
                    f.at.plusSeconds(900).toString(),
                    f.at.minusSeconds(60).toString(),
                    f.at.minusSeconds(30).toString(),
                )
                db.update(
                    "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional retained room device','PROVISIONING',0,?::timestamptz,?::timestamptz)",
                    company,
                    device,
                    account,
                    at,
                    at,
                )
                db.update(
                    "insert into collaboration_device_sessions(company_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?::timestamptz)",
                    company,
                    device,
                    account,
                    parent,
                    at,
                )
                db.update(
                    "update collaboration_devices set state='ACTIVE',version=1 where company_id=? and id=?",
                    company,
                    device,
                )
                db.update(
                    "insert into collaboration_provider_sessions(company_id,device_id,account_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) values(?,?,?,?,?,0,'fixture-envelope',?::timestamptz,?::timestamptz,?::timestamptz)",
                    company,
                    device,
                    account,
                    oldUser(account),
                    "HRIS_${compact(device)}",
                    at,
                    at,
                    f.at.plusSeconds(600).toString(),
                )
                if (fresh)
                    db.update(
                        "insert into collaboration_space_identities(space_id,account_id,matrix_server_name,matrix_user_id,origin,created_at) values(?,?,'matrix.example.test',?,'SPACE',?::timestamptz)",
                        f.space,
                        account,
                        user(account),
                        at,
                    )
                else
                    db.update(
                        "insert into collaboration_space_identities(space_id,account_id,matrix_server_name,matrix_user_id,origin,legacy_company_id,legacy_device_id,created_at) values(?,?,'matrix.example.test',?,'LEGACY',?,?,?::timestamptz)",
                        f.space,
                        account,
                        user(account),
                        company,
                        device,
                        at,
                    )
            }

            db.update(
                "insert into collaboration_space_direct_conversations(space_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,?::timestamptz)",
                f.space,
                groupId,
                pair[0],
                pair[1],
                pair[1],
                at,
            )
        }

        fun original() =
            f.runtime.update(
                "insert into collaboration_direct_conversations(company_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,?::timestamptz)",
                company,
                originalId,
                pair[0],
                pair[1],
                pair[0],
                at,
            )

        fun intent(legacy: Boolean = false) =
            f.runtime.update(
                "insert into collaboration_space_room_intents(space_id,conversation_id,first_account_id,second_account_id,requested_by,matrix_creator_account_id,matrix_server_name,first_matrix_user_id,second_matrix_user_id,origin,legacy_company_id,legacy_conversation_id,legacy_room_id,legacy_bound_at,created_at) values(?,?,?,?,?,?,'matrix.example.test',?,?,?,?,?,?,?::timestamptz,?::timestamptz)",
                f.space,
                groupId,
                pair[0],
                pair[1],
                pair[0],
                pair[0],
                user(pair[0]),
                user(pair[1]),
                if (legacy) "LEGACY" else "SPACE",
                if (legacy) company else null,
                if (legacy) originalId else null,
                if (legacy) "!legacy:matrix.example.test" else null,
                if (legacy) at else null,
                at,
            )

        fun routes() =
            f.runtime.queryForList(
                "select * from collaboration_original_pair_routes where original_company_id=? and first_account_id=? and second_account_id=?",
                company,
                pair[0],
                pair[1],
            )

        fun <T> run(actor: Actor = this.actor, body: () -> T) = f.run(actor, body)

        fun bindOriginal() {
            val db = f.owner
            val job = UUID.randomUUID()
            val creator = pair[0]
            db.update(
                """insert into background_jobs(id,company_id,actor_id,kind,operation_id,request,authenticated_at,credential_version,correlation_id,created_at,total_items) values(?,?,?,'COLLABORATION_DIRECT_PROVISION',?,jsonb_build_object('conversationId',?::text,'deviceId',?::text,'nativeSessionId',?::text,'platform','ANDROID','clientBuild','28'),?::timestamptz,0,?,?::timestamptz,1)""",
                job,
                company,
                creator,
                UUID.randomUUID(),
                originalId,
                devices.getValue(creator),
                parents.getValue(creator),
                f.at.minusSeconds(60).toString(),
                UUID.randomUUID(),
                at,
            )
            db.update(
                "insert into collaboration_direct_room_provisioning(company_id,conversation_id,account_id,device_id,job_id,version,created_at,updated_at) values(?,?,?,?,?,0,?::timestamptz,?::timestamptz)",
                company,
                originalId,
                creator,
                devices.getValue(creator),
                job,
                at,
                at,
            )
            db.update(
                "update background_jobs set status='RUNNING',attempts=1,lease_owner=gen_random_uuid(),lease_token=gen_random_uuid(),lease_until=now()+interval '1 minute' where id=?",
                job,
            )
            db.update(
                "insert into collaboration_direct_room_bindings(company_id,conversation_id,account_id,job_id,provisioning_version,matrix_server_name,matrix_room_id,created_at) values(?,?,?,?,0,'matrix.example.test','!legacy:matrix.example.test',?::timestamptz)",
                company,
                originalId,
                creator,
                job,
                at,
            )
        }
    }
}
