package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.*
import java.util.UUID
import java.util.concurrent.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSpaceConversationMigrationTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun publicMembersDeduplicateCompaniesAndDoNotGrantPrivateColumnsOrRoleMembership() {
        val f = base()
        val other = company(f)
        val peer = peer(f, other)
        affiliate(f, f.company, peer)
        migrate(f)
        val rows =
            success(
                f.run {
                    f.runtime.queryForList(
                        "select * from collaboration_space_members order by account_id"
                    )
                }
            )
        assertEquals(2, rows.size)
        assertEquals(1, rows.count { it["account_id"] == peer })
        assertNull(rows.single { it["account_id"] == peer }["matrix_user_id"])
        assertEquals(f.user, rows.single { it["account_id"] == f.account }["matrix_user_id"])
        assertEquals(
            rows,
            success(
                f.run(f.actor.copy(companyId = other)) {
                    f.runtime.queryForList(
                        "select * from collaboration_space_members order by account_id"
                    )
                }
            ),
        )
        for (role in
            listOf("hris_collaboration_directory_reader", "hris_collaboration_legacy_reader")) {
            assertEquals(
                false,
                f.owner.queryForObject(
                    "select rolcanlogin or rolsuper or rolbypassrls or rolcreaterole from pg_roles where rolname=?",
                    Boolean::class.java,
                    role,
                ),
            )
            assertEquals(
                false,
                f.owner.queryForObject(
                    "select pg_has_role('space_gateway_fixture',?,'MEMBER')",
                    Boolean::class.java,
                    role,
                ),
            )
            assertEquals(
                false,
                f.owner.queryForObject(
                    "select has_column_privilege(?,'accounts','email','SELECT')",
                    Boolean::class.java,
                    role,
                ),
            )
            assertEquals(
                false,
                f.owner.queryForObject(
                    "select has_column_privilege(?,'collaboration_space_provider_sessions','token_encrypted','SELECT')",
                    Boolean::class.java,
                    role,
                ),
            )
            assertEquals(
                false,
                f.owner.queryForObject(
                    "select has_column_privilege(?,'native_sessions','refresh_hash','SELECT')",
                    Boolean::class.java,
                    role,
                ),
            )
            assertEquals(
                false,
                f.owner.queryForObject(
                    "select has_column_privilege(?,'background_jobs','request','SELECT')",
                    Boolean::class.java,
                    role,
                ),
            )
            assertInstanceOf(
                Result.Failed::class.java,
                f.run { f.runtime.execute("set local role $role") },
            )
        }
        f.owner.update(
            "update company_memberships set active=false,version=version+1 where account_id=?",
            peer,
        )
        assertEquals(
            listOf(f.account),
            success(
                f.run {
                    f.runtime.queryForList(
                        "select account_id from collaboration_space_members",
                        UUID::class.java,
                    )
                }
            ),
        )
        assertTrue(
            success(
                    f.run(f.anonymous) {
                        f.runtime.queryForList("select * from collaboration_space_members")
                    }
                )
                .isEmpty()
        )
    }

    @Test
    fun onlyCurrentRootPairCanInsertAndOwnedImmutableIntentSurvivesOffboardingAndPurge() {
        val f = base()
        val peer = peer(f, company(f))
        migrate(f)
        val id = UUID.randomUUID()
        for (actor in
            listOf(
                f.anonymous,
                f.actor.copy(companyId = f.company),
                f.actor.copy(accountId = UUID.randomUUID()),
            )) {
            assertInstanceOf(Result.Failed::class.java, f.run(actor) { insert(f, peer, id) })
        }
        for (time in listOf("'infinity'", "clock_timestamp()+interval '1 second'")) {
            assertInstanceOf(Result.Failed::class.java, f.run { insert(f, peer, id, time) })
        }
        assertInstanceOf(Result.Failed::class.java, f.run { insert(f, f.account, id) })
        val unavailable = peer(f, company(f), eligible = false)
        assertInstanceOf(Result.Failed::class.java, f.run { insert(f, unavailable, id) })
        assertEquals(1, success(f.run { insert(f, peer, id) }))
        assertEquals(1, success(f.run(f.actor.copy(accountId = peer)) { count(f.runtime) }))
        assertInstanceOf(Result.Failed::class.java, f.run { insert(f, peer, UUID.randomUUID()) })
        assertEquals(
            0,
            success(
                f.run { f.runtime.update("delete from collaboration_space_direct_conversations") }
            ),
        )
        assertEquals(
            0,
            success(
                f.run {
                    f.runtime.update(
                        "update collaboration_space_direct_conversations set version=1"
                    )
                }
            ),
        )
        assertThrows(DataAccessException::class.java) {
            f.owner.update("delete from collaboration_space_direct_conversations")
        }
        assertThrows(DataAccessException::class.java) {
            f.owner.update("update collaboration_space_direct_conversations set created_by=?", peer)
        }
        f.owner.update("delete from native_sessions where id=?", f.parent)
        f.owner.update(
            "update company_memberships set active=false,version=version+1 where account_id=?",
            f.account,
        )
        assertEquals(1, success(f.run { count(f.runtime) }))
        assertTrue(
            success(f.run { f.runtime.queryForList("select * from collaboration_space_members") })
                .isEmpty()
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.run { insert(f, unavailable, UUID.randomUUID()) },
        )
        assertEquals(0, success(f.run(f.anonymous) { count(f.runtime) }))
        assertEquals(0, success(f.run(f.actor.copy(companyId = f.company)) { count(f.runtime) }))
        assertEquals(
            true,
            f.owner.queryForObject(
                "select relrowsecurity and relforcerowsecurity from pg_class where oid='collaboration_space_direct_conversations'::regclass",
                Boolean::class.java,
            ),
        )
    }

    @Test
    fun oppositeConcurrentCreatorsCannotReplaceOriginalPairAndFailedTransactionsLeaveNoIntent() {
        val f = base()
        val peer = peer(f, company(f))
        migrate(f)
        val failed =
            f.transactions.run<Unit>(f.actor) {
                insert(f, peer, UUID.randomUUID())
                Result.Failed(Failure(FailureKind.CONFLICT, "fixture_late_denial"))
            }
        assertInstanceOf(Result.Failed::class.java, failed)
        assertEquals(0, success(f.run { count(f.runtime) }))
        for (interrupt in listOf(false, true)) {
            val action = {
                f.transactions.run<Unit>(f.actor) {
                    insert(f, peer, UUID.randomUUID())
                    if (interrupt) throw InterruptedException("fixture")
                    throw CancellationException("fixture")
                }
            }
            if (interrupt) assertThrows(InterruptedException::class.java) { action() }
            else assertThrows(CancellationException::class.java) { action() }
            assertEquals(0, success(f.run { count(f.runtime) }))
        }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val start = CountDownLatch(1)
            val results =
                listOf(f.account, peer).map { creator ->
                    pool.submit<Result<Int>> {
                        assertTrue(start.await(10, TimeUnit.SECONDS))
                        f.run(f.actor.copy(accountId = creator)) {
                            insert(f, peer, UUID.randomUUID(), creator = creator)
                        }
                    }
                }
            start.countDown()
            val values = results.map { it.get(20, TimeUnit.SECONDS) }
            assertEquals(1, values.count { it is Result.Success })
            assertEquals(1, values.count { it is Result.Failed })
            assertEquals(1, success(f.run { count(f.runtime) }))
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun migrationAndArchiveRetainOwnedOriginalBoundRoomsWithoutCompanyOrProviderAuthority() {
        val f = base()
        val peer = peer(f, company(f))
        val conversation = UUID.randomUUID()
        seedLegacyRoom(f, peer, conversation)
        val original = f.owner.queryForList("select * from collaboration_direct_room_bindings")
        migrate(f)
        success(f.run { insert(f, peer, UUID.randomUUID()) })
        assertEquals(
            original,
            f.owner.queryForList("select * from collaboration_direct_room_bindings"),
        )
        val inventory =
            success(
                f.run {
                    f.runtime.queryForList("select * from collaboration_space_legacy_conversations")
                }
            )
        assertEquals(1, inventory.size)
        assertEquals(f.company, inventory.single()["legacy_company_id"])
        assertEquals(conversation, inventory.single()["legacy_conversation_id"])
        assertEquals("!owned:matrix.example.test", inventory.single()["matrix_room_id"])
        assertTrue(
            success(
                    f.run {
                        f.runtime.queryForList("select * from collaboration_direct_room_bindings")
                    }
                )
                .isEmpty()
        )
        assertTrue(
            success(
                    f.run {
                        f.runtime.queryForList("select * from collaboration_direct_conversations")
                    }
                )
                .isEmpty()
        )
        assertTrue(
            success(
                    f.run(f.actor.copy(accountId = UUID.randomUUID())) {
                        f.runtime.queryForList(
                            "select * from collaboration_space_legacy_conversations"
                        )
                    }
                )
                .isEmpty()
        )
        f.owner.update("delete from native_sessions where id=?", f.parent)
        assertEquals(
            inventory,
            success(
                f.run {
                    f.runtime.queryForList("select * from collaboration_space_legacy_conversations")
                }
            ),
        )
        val restoredOwner = f.restore()
        val restoredSource =
            DriverManagerDataSource(
                postgres.jdbcUrl.substringBeforeLast('/') + "/space_gateway_restored",
                "space_gateway_fixture",
                "isolated-fixture-only",
            )
        val restored = JdbcTemplate(restoredSource)
        val tx = PostgresTransactionRunner(JdbcTransactionManager(restoredSource), restored)
        assertEquals(
            inventory,
            success(
                tx.run(f.actor) {
                    safeDatabaseCall {
                        restored.queryForList(
                            "select * from collaboration_space_legacy_conversations"
                        )
                    }
                }
            ),
        )
        assertEquals(1, success(tx.run(f.actor) { safeDatabaseCall { count(restored) } }))
        assertEquals(0, success(tx.run(f.anonymous) { safeDatabaseCall { count(restored) } }))
        assertEquals(
            true,
            restoredOwner.queryForObject(
                "select relrowsecurity and relforcerowsecurity from pg_class where oid='collaboration_space_direct_conversations'::regclass",
                Boolean::class.java,
            ),
        )
        assertThrows(DataAccessException::class.java) {
            restoredOwner.update("delete from collaboration_space_direct_conversations")
        }
        f.owner.update(
            "update company_memberships set active=false,version=version+1 where account_id=?",
            f.account,
        )
        assertTrue(
            success(
                    f.run {
                        f.runtime.queryForList(
                            "select * from collaboration_space_legacy_conversations"
                        )
                    }
                )
                .isEmpty()
        )
        assertEquals(
            original,
            f.owner.queryForList("select * from collaboration_direct_room_bindings"),
        )
    }

    private fun base() = SpaceGatewayMigrationFixture(postgres).also { it.migrate() }

    private fun migrate(f: SpaceGatewayMigrationFixture) {
        Flyway.configure().dataSource(f.source).target("129").load().migrate()
        f.owner.execute(
            "grant select,insert,update,delete on collaboration_space_direct_conversations to space_gateway_fixture,space_gateway_worker"
        )
        f.owner.execute(
            "grant select on collaboration_space_members,collaboration_space_legacy_conversations to space_gateway_fixture,space_gateway_worker"
        )
    }

    private fun company(f: SpaceGatewayMigrationFixture): UUID =
        UUID.randomUUID().also {
            f.owner.update(
                "insert into companies(id,code,name,timezone) values(?,?,'Fictional other company','UTC')",
                it,
                it.toString().replace("-", ""),
            )
        }

    private fun peer(
        f: SpaceGatewayMigrationFixture,
        company: UUID,
        eligible: Boolean = true,
    ): UUID =
        UUID.randomUUID().also {
            f.owner.update(
                "insert into accounts(id,email,display_name) values(?,?,'Fictional peer')",
                it,
                "$it@example.test",
            )
            if (eligible) affiliate(f, company, it)
        }

    private fun affiliate(f: SpaceGatewayMigrationFixture, company: UUID, account: UUID) {
        f.owner.update(
            "insert into company_memberships(company_id,account_id) values(?,?)",
            company,
            account,
        )
        f.owner.update(
            "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
            company,
            account,
        )
    }

    private fun insert(
        f: SpaceGatewayMigrationFixture,
        peer: UUID,
        id: UUID,
        time: String = "clock_timestamp()",
        creator: UUID = f.account,
    ): Int {
        val pair = listOf(f.account, peer).sortedBy(UUID::toString)
        return f.runtime.update(
            "insert into collaboration_space_direct_conversations(space_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,$time)",
            f.space,
            id,
            pair[0],
            pair[1],
            creator,
        )
    }

    private fun count(sql: JdbcTemplate) =
        sql.queryForObject(
            "select count(*) from collaboration_space_direct_conversations",
            Int::class.java,
        )!!

    private fun <T> success(value: Result<T>): T = (value as Result.Success).value

    private fun seedLegacyRoom(f: SpaceGatewayMigrationFixture, peer: UUID, conversation: UUID) {
        val pair = listOf(f.account, peer).sortedBy(UUID::toString)
        val device = UUID.randomUUID()
        val job = UUID.randomUUID()
        val at = java.sql.Timestamp.from(f.at)
        f.owner.update(
            "insert into collaboration_direct_conversations(company_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,?)",
            f.company,
            conversation,
            pair[0],
            pair[1],
            f.account,
            at,
        )
        f.owner.update(
            "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional retained device','PROVISIONING',0,?,?)",
            f.company,
            device,
            f.account,
            at,
            at,
        )
        f.owner.update(
            "insert into collaboration_device_sessions(company_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?)",
            f.company,
            device,
            f.account,
            f.parent,
            at,
        )
        f.owner.update(
            "update collaboration_devices set state='ACTIVE',version=1 where id=?",
            device,
        )
        f.owner.update(
            """insert into background_jobs(id,company_id,actor_id,kind,operation_id,request,authenticated_at,credential_version,correlation_id,created_at,total_items)
            values(?,?,?,'COLLABORATION_DIRECT_PROVISION',?,jsonb_build_object('conversationId',?::text,'deviceId',?::text,'nativeSessionId',?::text,'platform','ANDROID','clientBuild','5'),?,0,?,?,1)""",
            job,
            f.company,
            f.account,
            UUID.randomUUID(),
            conversation,
            device,
            f.parent,
            at,
            UUID.randomUUID(),
            at,
        )
        f.owner.update(
            "insert into collaboration_direct_room_provisioning(company_id,conversation_id,account_id,device_id,job_id,version,created_at,updated_at) values(?,?,?,?,?,0,?,?)",
            f.company,
            conversation,
            f.account,
            device,
            job,
            at,
            at,
        )
        f.owner.update(
            "update background_jobs set status='RUNNING',attempts=1,lease_owner=gen_random_uuid(),lease_token=gen_random_uuid(),lease_until=now()+interval '1 minute' where id=?",
            job,
        )
        f.owner.update(
            "insert into collaboration_direct_room_bindings(company_id,conversation_id,account_id,job_id,provisioning_version,matrix_server_name,matrix_room_id,created_at) values(?,?,?,?,0,'matrix.example.test','!owned:matrix.example.test',?)",
            f.company,
            conversation,
            f.account,
            job,
            at,
        )
    }
}
