package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationLegacyParentDataSource
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationLegacyParentRepository
import dev.fajar.hris.collaboration.domain.entities.CollaborationLegacyDevicePosition
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
class CollaborationLegacyParentRepositoryTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun exactPublicParentAndKeyMetadataTracksCurrentScopeAndDisappearsAfterParentPurge() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).load().migrate()
        val sql = JdbcTemplate(source)
        val account = UUID.randomUUID()
        val peer = UUID.randomUUID()
        val company = UUID.randomUUID()
        val parent = UUID.randomUUID()
        val device = UUID.randomUUID()
        val space =
            requireNotNull(
                sql.queryForObject("select id from collaboration_spaces", UUID::class.java)
            )
        sql.update(
            "insert into companies(id,code,name,timezone) values(?,'LPARENT','Fictional company','UTC')",
            company,
        )
        for ((index, id) in listOf(account, peer).withIndex()) {
            sql.update(
                "insert into accounts(id,email,display_name) values(?,?,?)",
                id,
                "parent-$index@example.test",
                "Fictional $index",
            )
            sql.update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                id,
            )
            sql.update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                company,
                id,
            )
        }
        sql.update(
            "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at) values(?,?,?,now(),now()+interval '1 hour')",
            parent,
            account,
            compact(parent).repeat(2),
        )
        sql.update(
            "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional','PROVISIONING',0,now(),now())",
            company,
            device,
            account,
        )
        sql.update(
            "insert into collaboration_device_sessions(company_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,now())",
            company,
            device,
            account,
            parent,
        )
        val matrixUser = "@hris_${compact(company)}_${compact(account)}:matrix.example.test"
        sql.update(
            "insert into collaboration_provider_sessions(company_id,device_id,account_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) values(?,?,?,?,?,0,'fixture-envelope-not-a-token',now(),now(),now()+interval '5 minutes')",
            company,
            device,
            account,
            matrixUser,
            "HRIS_${compact(device)}",
        )
        sql.update(
            "update collaboration_devices set state='ACTIVE',version=1,updated_at=now() where company_id=? and id=?",
            company,
            device,
        )
        sql.execute("create role legacy_parent_fixture login password 'isolated-fixture-only'")
        sql.execute("grant usage on schema public to legacy_parent_fixture")
        sql.execute("grant select on collaboration_space_legacy_parents to legacy_parent_fixture")
        val runtime =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "legacy_parent_fixture",
                "isolated-fixture-only",
            )
        val transactions =
            PostgresTransactionRunner(JdbcTransactionManager(runtime), JdbcTemplate(runtime))
        val repository =
            StoredCollaborationLegacyParentRepository(
                PostgresCollaborationLegacyParentDataSource(
                    DSL.using(TransactionAwareDataSourceProxy(runtime), SQLDialect.POSTGRES)
                )
            )
        val actor = Actor(account, null, emptySet(), Instant.now(), UUID.randomUUID())
        val position = CollaborationLegacyDevicePosition(company, device)
        fun read(
            current: Actor = actor,
            requestedAccount: UUID = current.accountId,
            requestedParent: UUID = parent,
            requestedPosition: CollaborationLegacyDevicePosition = position,
        ) =
            success(
                transactions.run(current) {
                    repository.find(space, requestedAccount, requestedParent, requestedPosition)
                }
            )
        val initial = requireNotNull(read())
        assertNull(initial.key)
        assertEquals(matrixUser, initial.device.matrixUserId)
        assertEquals(initial, read(actor.copy(companyId = UUID.randomUUID())))
        assertNull(read(actor.copy(accountId = peer), requestedAccount = account))
        assertNull(read(requestedAccount = peer))
        assertNull(read(requestedParent = UUID.randomUUID()))
        assertNull(read(requestedPosition = position.copy(companyId = UUID.randomUUID())))
        sql.update(
            "insert into collaboration_device_key_publications(company_id,device_id,account_id,fingerprint,version,reserved_at) values(?,?,?,?,0,now())",
            company,
            device,
            account,
            "a".repeat(64),
        )
        val reserved = requireNotNull(read())
        assertEquals("a".repeat(64), reserved.key?.fingerprint)
        assertEquals(0L, reserved.key?.version)
        sql.update(
            "update collaboration_device_key_publications set version=1,published_at=now() where company_id=? and device_id=?",
            company,
            device,
        )
        val published = requireNotNull(read())
        assertEquals(1L, published.key?.version)
        assertNotNull(published.key?.publishedAt)
        sql.update(
            "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
            company,
            account,
        )
        assertNull(read())
        sql.update(
            "update company_memberships set active=true,version=version+1 where company_id=? and account_id=?",
            company,
            account,
        )
        assertEquals(published, read())
        sql.update("delete from native_sessions where id=?", parent)
        assertNull(read())
        assertEquals(
            1,
            sql.queryForObject(
                "select count(*) from collaboration_provider_sessions",
                Int::class.java,
            ),
        )
        assertEquals(
            1,
            sql.queryForObject(
                "select count(*) from collaboration_device_key_publications",
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
