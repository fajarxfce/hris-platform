package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationSpaceIdentityDataSource
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationSpaceIdentityRepository
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceIdentityOrigin
import dev.fajar.hris.collaboration.domain.policies.selectCollaborationSpaceIdentity
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSpaceIdentityRepositoryTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun oneSnapshotDistinguishesUnavailableScopeFromAdmittedAbsenceAndPreservesItsOldestUser() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).load().migrate()
        val sql = JdbcTemplate(source)
        val account = UUID.randomUUID()
        val newcomer = UUID.randomUUID()
        val companies =
            listOf(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"), UUID(0, 1), UUID(0, 2))
        val devices = listOf(UUID.randomUUID(), UUID.randomUUID())
        val server = "matrix.example.test"
        for ((index, company) in companies.withIndex()) sql.update(
            "insert into companies(id,code,name,timezone) values(?,?,?,'UTC')",
            company,
            "ISNAP$index",
            "Fictional $index",
        )
        for ((index, id) in listOf(account, newcomer).withIndex()) {
            sql.update(
                "insert into accounts(id,email,display_name) values(?,?,?)",
                id,
                "snapshot-$index@example.test",
                "Fictional $index",
            )
            sql.update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                companies.last(),
                id,
            )
            sql.update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                companies.last(),
                id,
            )
        }
        for (index in 0..1) {
            val at = if (index == 0) "2026-10-01T00:00:00Z" else "2026-10-02T00:00:00Z"
            sql.update(
                "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional','PROVISIONING',0,?::timestamptz,?::timestamptz)",
                companies[index],
                devices[index],
                account,
                at,
                at,
            )
            sql.update(
                "insert into collaboration_provider_sessions(company_id,device_id,account_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) values(?,?,?,?,?,0,'fixture-envelope-not-a-token',?::timestamptz,?::timestamptz,?::timestamptz+interval '5 minutes')",
                companies[index],
                devices[index],
                account,
                "@hris_${compact(companies[index])}_${compact(account)}:$server",
                "HRIS_${compact(devices[index])}",
                at,
                at,
                at,
            )
            sql.update(
                "update collaboration_devices set state='ACTIVE',version=1,updated_at=created_at where company_id=? and id=?",
                companies[index],
                devices[index],
            )
        }
        sql.update(
            "update collaboration_devices set state='REVOKED',version=2,revoked_at=created_at+interval '1 minute',updated_at=created_at+interval '1 minute' where company_id=? and id=?",
            companies.first(),
            devices.first(),
        )
        sql.update(
            "update collaboration_provider_sessions set generation=1,token_encrypted=null,cleared_at=created_at+interval '1 minute',updated_at=created_at+interval '1 minute' where company_id=? and device_id=?",
            companies.first(),
            devices.first(),
        )
        sql.execute("create role identity_snapshot_fixture login password 'isolated-fixture-only'")
        sql.execute("grant usage on schema public to identity_snapshot_fixture")
        sql.execute("grant select on all tables in schema public to identity_snapshot_fixture")
        sql.execute("grant insert on collaboration_space_identities to identity_snapshot_fixture")
        val runtime =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "identity_snapshot_fixture",
                "isolated-fixture-only",
            )
        val transactions =
            PostgresTransactionRunner(JdbcTransactionManager(runtime), JdbcTemplate(runtime))
        val repository =
            StoredCollaborationSpaceIdentityRepository(
                PostgresCollaborationSpaceIdentityDataSource(
                    DSL.using(TransactionAwareDataSourceProxy(runtime), SQLDialect.POSTGRES)
                )
            )
        val actor = Actor(account, null, emptySet(), Instant.now(), UUID.randomUUID())
        val space =
            requireNotNull(
                sql.queryForObject("select id from collaboration_spaces", UUID::class.java)
            )
        fun read(
            current: Actor = actor,
            requestedAccount: UUID = current.accountId,
            selectedSpace: UUID = space,
            requestedServer: String = server,
        ) =
            success(
                transactions.run(current) {
                    repository.selection(selectedSpace, requestedAccount, requestedServer)
                }
            )
        val initial = requireNotNull(read())
        assertNull(initial.selected)
        assertEquals(devices.first(), initial.preferred?.position?.deviceId)
        assertNotNull(initial.preferred?.credentialRetiredAt)
        assertEquals(initial, read(actor.copy(companyId = companies.last())))
        val fresh = requireNotNull(read(actor.copy(accountId = newcomer)))
        assertNull(fresh.selected)
        assertNull(fresh.preferred)
        assertEquals(
            CollaborationSpaceIdentityOrigin.SPACE,
            success(selectCollaborationSpaceIdentity(space, newcomer, server, fresh, Instant.now()))
                .origin,
        )
        assertNull(read(requestedAccount = newcomer))
        assertNull(read(selectedSpace = UUID.randomUUID()))
        sql.update(
            "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
            companies.last(),
            account,
        )
        val unavailable = read()
        assertNull(unavailable)
        sql.update(
            "update company_memberships set active=true,version=version+1 where company_id=? and account_id=?",
            companies.last(),
            account,
        )
        assertInstanceOf(
            Result.Failed::class.java,
            selectCollaborationSpaceIdentity(space, account, server, unavailable, Instant.now()),
        )
        assertEquals(initial, read())
        val selected =
            success(
                selectCollaborationSpaceIdentity(space, account, server, initial, Instant.now())
            )
        success(transactions.run(actor) { repository.create(selected) })
        val retained = requireNotNull(read())
        assertEquals(selected, retained.selected)
        assertNull(retained.preferred)
        val otherServer = requireNotNull(read(requestedServer = "other.example.test"))
        assertEquals(selected, otherServer.selected)
        val rejected =
            selectCollaborationSpaceIdentity(
                space,
                account,
                "other.example.test",
                otherServer,
                Instant.now(),
            )
        assertEquals(
            "collaboration_identity_migration_required",
            (rejected as Result.Failed).failure.code,
        )
        assertEquals(selected, requireNotNull(read()).selected)
        assertEquals(
            1,
            sql.queryForObject(
                "select count(*) from collaboration_space_identities",
                Int::class.java,
            ),
        )
    }

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
