package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.time.temporal.ChronoUnit
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
class CollaborationSpaceRecipientMigrationTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun upgradeKeepsKeysAndOnlyTheVerifiedPairCanReadCrossCompanyCandidates() {
        val f = Fixture()
        f.reserve()
        f.reserve(f.peer)
        val original =
            f.s.owner.queryForList(
                "select * from collaboration_space_key_publications order by device_id"
            )
        f.migrate()
        assertEquals(
            original,
            f.s.owner.queryForList(
                "select * from collaboration_space_key_publications order by device_id"
            ),
        )
        assertEquals(listOf(f.s.account), f.candidates().map { it["account_id"] })
        f.bind()
        val candidates = f.candidates()
        assertEquals(setOf(f.s.account, f.peer), candidates.map { it["account_id"] }.toSet())
        assertEquals(setOf(false), candidates.map { it["publication_confirmed"] }.toSet())
        assertEquals(candidates, f.candidates(f.s.actor.copy(accountId = f.peer)))
        assertTrue(f.candidates(f.s.actor.copy(companyId = f.s.company)).isEmpty())
        assertTrue(f.candidates(f.s.actor.copy(companyId = f.peerCompany)).isEmpty())
        assertTrue(f.candidates(f.s.anonymous).isEmpty())
        assertTrue(f.candidates(f.s.actor.copy(accountId = UUID.randomUUID())).isEmpty())
        assertEquals(1, success(f.s.run { f.rows("collaboration_space_key_publications").size }))
        assertEquals(
            setOf(
                "space_id",
                "account_id",
                "device_id",
                "matrix_server_name",
                "matrix_user_id",
                "matrix_device_id",
                "fingerprint",
                "receive_until",
                "publication_confirmed",
            ),
            candidates.first().keys,
        )
    }

    @Test
    fun pendingAndConfirmedKeysKeepReceivingAfterShortAccessAndProviderExpiry() {
        val f = Fixture()
        f.reserve()
        f.reserve(f.peer)
        f.bind()
        f.migrate()
        success(
            f.s.run(f.s.actor.copy(accountId = f.peer)) {
                f.s.runtime.update(
                    "update collaboration_space_key_publications set version=1,published_at=statement_timestamp() where device_id=?",
                    f.peerDevice,
                )
            }
        )
        // A legitimate credential rotation expires naturally; do not bypass its trigger.
        success(
            f.s.run(f.s.actor.copy(accountId = f.peer)) {
                f.s.runtime.update(
                    "update collaboration_space_provider_sessions set generation=generation+1,updated_at=statement_timestamp(),token_expires_at=statement_timestamp()+interval '1 second' where device_id=?",
                    f.peerDevice,
                )
            }
        )
        TimeUnit.MILLISECONDS.sleep(1100)
        f.s.owner.update(
            "update native_sessions set access_expires_at=clock_timestamp()-interval '1 second' where id=?",
            f.peerParent,
        )
        assertTrue(
            f.s.owner.queryForObject(
                "select token_expires_at<clock_timestamp() from collaboration_space_provider_sessions where device_id=?",
                Boolean::class.java,
                f.peerDevice,
            ) == true
        )
        val candidates = f.candidates()
        assertEquals(2, candidates.size)
        assertEquals(
            true,
            candidates.single { it["account_id"] == f.peer }["publication_confirmed"],
        )
        assertEquals(
            false,
            candidates.single { it["account_id"] == f.s.account }["publication_confirmed"],
        )
        assertTrue(
            f.s.owner.queryForObject(
                "select expires_at>clock_timestamp() from native_sessions where id=?",
                Boolean::class.java,
                f.peerParent,
            ) == true
        )
    }

    @Test
    fun currentRevocationSecurityAndLastCompanyEligibilityRemoveOnlyTheAffectedPeer() {
        val f = Fixture()
        f.reserve()
        f.reserve(f.peer)
        f.bind()
        f.migrate()
        for ((change, undo) in
            listOf(
                "update accounts set active=false,version=version+1 where id='${f.peer}'" to
                    "update accounts set active=true,version=version+1 where id='${f.peer}'",
                "update native_sessions set credential_version=-1 where id='${f.peerParent}'" to
                    "update native_sessions set credential_version=0 where id='${f.peerParent}'",
                "update company_memberships set active=false,version=version+1 where account_id='${f.peer}'" to
                    "update company_memberships set active=true,version=version+1 where account_id='${f.peer}'",
                "update companies set active=false,version=version+1 where id='${f.peerCompany}'" to
                    "update companies set active=true,version=version+1 where id='${f.peerCompany}'",
                "update native_sessions set access_expires_at=clock_timestamp()-interval '2 seconds',expires_at=clock_timestamp()-interval '1 second' where id='${f.peerParent}'" to
                    "update native_sessions set access_expires_at='${f.s.at.plusSeconds(900)}',expires_at='${f.s.at.plusSeconds(3600)}' where id='${f.peerParent}'",
            )) {
            f.s.owner.update(change)
            assertEquals(listOf(f.s.account), f.candidates().map { it["account_id"] }, change)
            f.s.owner.update(undo)
            assertEquals(2, f.candidates().size)
        }
        f.s.owner.update(
            "update native_sessions set revoked_at=clock_timestamp() where id=?",
            f.peerParent,
        )
        assertEquals(listOf(f.s.account), f.candidates().map { it["account_id"] })
        f.s.owner.update(
            "update company_memberships set active=false,version=version+1 where account_id=?",
            f.s.account,
        )
        assertTrue(f.candidates().isEmpty())
        assertEquals(1, success(f.s.run { f.rows("collaboration_space_key_publications").size }))
    }

    @Test
    fun nativePurgeRemovesReceivingButPreservesOriginalKeyAndDeviceProvenance() {
        val f = Fixture()
        f.reserve()
        f.reserve(f.peer)
        f.bind()
        f.migrate()
        val keys =
            f.s.owner.queryForList(
                "select * from collaboration_space_key_publications order by device_id"
            )
        val devices =
            f.s.owner.queryForList("select * from collaboration_space_devices order by id")
        f.s.owner.update("delete from native_sessions where id=?", f.peerParent)
        assertEquals(listOf(f.s.account), f.candidates().map { it["account_id"] })
        assertEquals(
            keys,
            f.s.owner.queryForList(
                "select * from collaboration_space_key_publications order by device_id"
            ),
        )
        assertEquals(
            devices,
            f.s.owner.queryForList("select * from collaboration_space_devices order by id"),
        )
        val restored = f.s.restore()
        assertEquals(
            keys,
            restored.queryForList(
                "select * from collaboration_space_key_publications order by device_id"
            ),
        )
        assertEquals(
            devices,
            restored.queryForList("select * from collaboration_space_devices order by id"),
        )
        val reader =
            org.springframework.jdbc.core.JdbcTemplate(
                org.springframework.jdbc.datasource.DriverManagerDataSource(
                    requireNotNull(restored.dataSource).connection.use { it.metaData.url },
                    "space_gateway_fixture",
                    "isolated-fixture-only",
                )
            )
        val tx =
            PostgresTransactionRunner(
                org.springframework.jdbc.support.JdbcTransactionManager(
                    requireNotNull(reader.dataSource)
                ),
                reader,
            )
        assertEquals(
            f.candidates(),
            success(
                tx.run(f.s.actor) {
                    safeDatabaseCall {
                        reader.queryForList(
                            "select * from collaboration_space_recipient_devices order by account_id,device_id"
                        )
                    }
                }
            ),
        )
    }

    @Test
    fun projectionOwnerCannotReadPrivateColumnsAndRuntimeCannotAssumeIt() {
        val f = Fixture()
        f.migrate()
        val role = "hris_collaboration_space_recipient_reader"
        assertEquals(
            listOf(false, false, false, false, false, false),
            f.s.owner
                .queryForList(
                    "select rolcanlogin,rolinherit,rolsuper,rolcreaterole,rolcreatedb,rolbypassrls from pg_roles where rolname=?",
                    role,
                )
                .single()
                .values
                .toList(),
        )
        for ((table, column) in
            listOf(
                "accounts" to "email",
                "native_sessions" to "refresh_hash",
                "native_sessions" to "access_hash",
                "collaboration_space_provider_sessions" to "token_encrypted",
                "collaboration_space_gateway_sessions" to "replay_encrypted",
                "collaboration_space_room_provisioning" to "authenticated_at",
            )) {
            assertEquals(
                false,
                f.s.owner.queryForObject(
                    "select has_column_privilege(?,?,?,'SELECT')",
                    Boolean::class.java,
                    role,
                    table,
                    column,
                ),
            )
        }
        assertEquals(
            false,
            success(
                f.s.run {
                    f.s.runtime.queryForObject(
                        "select pg_has_role(current_user,?,'MEMBER')",
                        Boolean::class.java,
                        role,
                    )
                }
            ),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.s.run { f.s.runtime.execute("set local role $role") },
        )
        assertTrue(f.candidates().isEmpty())
        assertEquals(
            listOf("security_barrier=true"),
            f.s.owner.queryForList(
                "select unnest(reloptions) from pg_class where oid='collaboration_space_recipient_devices'::regclass",
                String::class.java,
            ),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.s.run { f.s.runtime.update("delete from collaboration_space_recipient_devices") },
        )
    }

    private inner class Fixture(target: String = "134") {
        val s = SpaceGatewayMigrationFixture(postgres)
        val peer = UUID.randomUUID()
        val peerCompany = UUID.randomUUID()
        val peerParent = UUID.randomUUID()
        val peerDevice = UUID.randomUUID()
        val conversation = UUID.randomUUID()
        val pair = listOf(s.account, peer).sortedBy(UUID::toString)
        val worker = JdbcTemplate(s.workerSource)
        val workerTransactions =
            PostgresTransactionRunner(JdbcTransactionManager(s.workerSource), worker)

        init {
            s.owner.update(
                "insert into companies(id,code,name,timezone) values(?,?,'Fictional other company','UTC')",
                peerCompany,
                peerCompany.toString().take(12),
            )
            s.owner.update(
                "insert into accounts(id,email,display_name) values(?,?,'Fictional room peer')",
                peer,
                "$peer@example.test",
            )
            s.owner.update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                peerCompany,
                peer,
            )
            s.owner.update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                peerCompany,
                peer,
            )
            s.owner.update(
                "insert into collaboration_space_identities(space_id,account_id,matrix_server_name,matrix_user_id,origin,created_at) values(?,?,'matrix.example.test',?,'SPACE',?::timestamptz)",
                s.space,
                peer,
                user(peer),
                s.at.toString(),
            )
            s.owner.update(
                "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,credential_version,access_expires_at,created_at,rotated_at) values(?,?,?,?::timestamptz,?::timestamptz,0,?::timestamptz,?::timestamptz,?::timestamptz)",
                peerParent,
                peer,
                peerParent.toString().replace("-", "").repeat(2),
                s.actor.authenticatedAt.toString(),
                s.at.plusSeconds(3600).toString(),
                s.at.plusSeconds(900).toString(),
                s.at.minusSeconds(60).toString(),
                s.at.minusSeconds(30).toString(),
            )
            s.owner.update(
                "insert into collaboration_space_devices(space_id,id,account_id,matrix_user_id,matrix_device_id,origin,origin_native_session_id,platform,label,state,version,created_at,updated_at) values(?,?,?,?,?,'SPACE',?,'ANDROID','Fictional peer','PROVISIONING',0,?::timestamptz,?::timestamptz)",
                s.space,
                peerDevice,
                peer,
                user(peer),
                "HRISG_${peerDevice.toString().replace("-", "")}",
                peerParent,
                s.at.toString(),
                s.at.toString(),
            )
            s.owner.update(
                "insert into collaboration_space_device_sessions(space_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?::timestamptz)",
                s.space,
                peerDevice,
                peer,
                peerParent,
                s.at.toString(),
            )
            Flyway.configure().dataSource(s.source).target("121").load().migrate()
            s.owner.update(
                "insert into collaboration_space_provider_sessions(space_id,device_id,account_id,native_session_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) values(?,?,?,?,?,?,0,'owned-peer-fixture',?::timestamptz,?::timestamptz,?::timestamptz)",
                s.space,
                peerDevice,
                peer,
                peerParent,
                user(peer),
                "HRISG_${peerDevice.toString().replace("-", "")}",
                s.at.toString(),
                s.at.toString(),
                s.at.plusSeconds(600).toString(),
            )
            s.owner.update(
                "update collaboration_space_devices set state='ACTIVE',version=1 where id=?",
                peerDevice,
            )
            s.migrate()
            Flyway.configure().dataSource(s.source).target(target).load().migrate()
            s.owner.execute(
                "grant select,insert,update,delete on all tables in schema public to space_gateway_fixture,space_gateway_worker"
            )
            success(
                s.run(s.actor.copy(accountId = peer)) {
                    s.runtime.update(
                        "insert into collaboration_space_direct_conversations(space_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,?::timestamptz)",
                        s.space,
                        conversation,
                        pair[0],
                        pair[1],
                        peer,
                        s.at.toString(),
                    )
                }
            )
        }

        fun migrate() {
            Flyway.configure().dataSource(s.source).target("135").load().migrate()
            s.owner.execute(
                "grant select on collaboration_space_recipient_devices to space_gateway_fixture,space_gateway_worker"
            )
        }

        fun reserve(account: UUID = s.account) =
            success(
                s.run(s.actor.copy(accountId = account)) {
                    val device = if (account == s.account) s.device else peerDevice
                    val parent = if (account == s.account) s.parent else peerParent
                    s.runtime.update(
                        "insert into collaboration_space_key_publications(space_id,account_id,device_id,native_session_id,matrix_user_id,matrix_device_id,fingerprint,version,reserved_at) values(?,?,?,?,?,?,?,0,statement_timestamp())",
                        s.space,
                        account,
                        device,
                        parent,
                        user(account),
                        "HRISG_${device.toString().replace("-", "")}",
                        (if (account == s.account) "a" else "b").repeat(64),
                    )
                }
            )

        fun bind() {
            success(
                s.run {
                    intent()
                    enqueue()
                }
            )
            val claimed = success(worker { claim() }).single()
            success(
                worker(s.actor) {
                    publish(claimed)
                    finish(claimed)
                }
            )
        }

        fun candidates(actor: Actor = s.actor) =
            success(
                s.run(actor) {
                    s.runtime.queryForList(
                        "select * from collaboration_space_recipient_devices order by account_id,device_id"
                    )
                }
            )

        fun user(account: UUID) =
            "@hrisg_${s.space.toString().replace("-", "")}_${account.toString().replace("-", "")}:matrix.example.test"

        fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS)

        fun intent(firstUser: String = user(pair[0])) =
            s.runtime.update(
                "insert into collaboration_space_room_intents(space_id,conversation_id,first_account_id,second_account_id,requested_by,matrix_creator_account_id,matrix_server_name,first_matrix_user_id,second_matrix_user_id,origin,created_at) values(?,?,?,?,?,?,'matrix.example.test',?,?,'SPACE',?::timestamptz)",
                s.space,
                conversation,
                pair[0],
                pair[1],
                s.account,
                s.account,
                firstUser,
                user(pair[1]),
                s.at.toString(),
            )

        fun enqueue(
            account: UUID = s.account,
            deviceVersion: Long = 1,
            generation: Long = 0,
            nativeVersion: Long = 0,
            lifetimeSeconds: Long = 120,
        ): Int {
            val at = now()
            return s.runtime.update(
                "insert into collaboration_space_room_provisioning(space_id,conversation_id,device_id,device_version,provider_generation,account_id,native_session_id,revision,native_session_version,credential_version,authenticated_at,correlation_id,client_platform,client_build,created_at,requested_at,expires_at,available_at,state,attempts) values(?,?,?,?,?,?,?,0,?,0,?::timestamptz,?,'ANDROID',28,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz,'QUEUED',0)",
                s.space,
                conversation,
                if (account == s.account) s.device else peerDevice,
                deviceVersion,
                generation,
                account,
                if (account == s.account) s.parent else peerParent,
                nativeVersion,
                s.actor.authenticatedAt.toString(),
                UUID.randomUUID(),
                at.toString(),
                at.toString(),
                at.plusSeconds(lifetimeSeconds).toString(),
                at.toString(),
            )
        }

        fun rows(table: String) = s.runtime.queryForList("select * from $table")

        fun <T> worker(actor: Actor = s.anonymous, body: () -> T): Result<T> =
            workerTransactions.run(actor) { safeDatabaseCall(body) }

        fun claim(seconds: Int = 30) =
            worker.queryForList(
                "select * from claim_collaboration_space_room_provisioning(?,1,?)",
                UUID.randomUUID(),
                seconds,
            )

        fun publish(
            lease: Map<String, Any?>,
            creator: UUID = s.account,
            old: Boolean = false,
        ): Int {
            val columns = if (old) "" else ",matrix_creator_account_id"
            val values = if (old) "" else ",?"
            val args =
                mutableListOf<Any?>(
                    s.space,
                    conversation,
                    lease["account_id"],
                    lease["revision"],
                    lease["lease_token"],
                    "!same-history",
                    now().toString(),
                )
            if (!old) args.add(creator)
            return worker.update(
                "insert into collaboration_space_room_bindings(space_id,conversation_id,published_by,request_revision,lease_token,matrix_room_id,created_at$columns) values(?,?,?,?,?,?,?::timestamptz$values)",
                *args.toTypedArray(),
            )
        }

        fun finish(lease: Map<String, Any?>) =
            worker.update(
                "update collaboration_space_room_provisioning set state='SUCCEEDED',completed_at=statement_timestamp(),last_outcome_revision=revision,last_outcome_state='SUCCEEDED',last_outcome_at=statement_timestamp(),failure_code=null,last_failure_code=null,lease_owner=null,lease_token=null,lease_until=null where space_id=? and conversation_id=? and lease_token=?",
                s.space,
                conversation,
                lease["lease_token"],
            )

        fun cancel(account: UUID = s.account) =
            s.runtime.update(
                "update collaboration_space_room_provisioning set state='CANCELLED',completed_at=statement_timestamp(),last_outcome_revision=revision,last_outcome_state='CANCELLED',last_outcome_at=statement_timestamp(),lease_owner=null,lease_token=null,lease_until=null where space_id=? and conversation_id=? and account_id=?",
                s.space,
                conversation,
                account,
            )
    }

    private fun <T> success(value: Result<T>): T = (value as Result.Success).value
}
