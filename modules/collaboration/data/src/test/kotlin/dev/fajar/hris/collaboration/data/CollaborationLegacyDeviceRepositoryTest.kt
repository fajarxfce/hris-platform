package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationLegacyDeviceDataSource
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationLegacyDeviceRepository
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
class CollaborationLegacyDeviceRepositoryTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun compoundCursorPreservesEveryOwnedLegacyDeviceAcrossUnsignedCompanyBoundaries() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).load().migrate()
        val sql = JdbcTemplate(source)
        val account = UUID.randomUUID()
        val companies =
            listOf(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("80000000-0000-0000-0000-000000000001"),
                UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
            )
        val devices = listOf(UUID(0, 1), UUID(0, 2), UUID(0, 3))
        sql.update(
            "insert into accounts(id,email,display_name) values(?,'legacy-page@example.test','Fictional account')",
            account,
        )
        for ((index, company) in companies.withIndex()) {
            sql.update(
                "insert into companies(id,code,name,timezone) values(?,?,?,'UTC')",
                company,
                "LPAGE$index",
                "Fictional $index",
            )
            for (device in devices) {
                sql.update(
                    "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional','PROVISIONING',0,now(),now())",
                    company,
                    device,
                    account,
                )
                sql.update(
                    "insert into collaboration_provider_sessions(company_id,device_id,account_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) values(?,?,?,?,?,0,'isolated-envelope-not-a-token',now(),now(),now()+interval '5 minutes')",
                    company,
                    device,
                    account,
                    "@hris_" + compact(company) + "_" + compact(account) + ":matrix.example.test",
                    "HRIS_" + compact(device),
                )
                sql.update(
                    "update collaboration_devices set state='ACTIVE',version=1,updated_at=now() where company_id=? and id=?",
                    company,
                    device,
                )
            }
        }
        sql.update(
            "insert into company_memberships(company_id,account_id) values(?,?)",
            companies.last(),
            account,
        )
        sql.update(
            "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
            companies.last(),
            account,
        )
        val space =
            requireNotNull(
                sql.queryForObject("select id from collaboration_spaces", UUID::class.java)
            )
        sql.execute("create role legacy_page_fixture login password 'isolated-fixture-only'")
        sql.execute("grant usage on schema public to legacy_page_fixture")
        sql.execute("grant select on all tables in schema public to legacy_page_fixture")
        val runtime =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "legacy_page_fixture",
                "isolated-fixture-only",
            )
        val transactions =
            PostgresTransactionRunner(JdbcTransactionManager(runtime), JdbcTemplate(runtime))
        val repository =
            StoredCollaborationLegacyDeviceRepository(
                PostgresCollaborationLegacyDeviceDataSource(
                    DSL.using(TransactionAwareDataSourceProxy(runtime), SQLDialect.POSTGRES)
                )
            )
        val actor = Actor(account, null, emptySet(), Instant.now(), UUID.randomUUID())
        fun page(
            after: CollaborationLegacyDevicePosition? = null,
            owner: Actor = actor,
            selectedSpace: UUID = space,
        ) = success(transactions.run(owner) { repository.list(selectedSpace, account, after, 2) })
        val positions = mutableListOf<CollaborationLegacyDevicePosition>()
        var cursor: CollaborationLegacyDevicePosition? = null
        var pages = 0
        do {
            assertTrue(pages++ < 5)
            val page = page(cursor)
            assertTrue(page.items.size in 1..2)
            assertTrue(page.items.all { it.accountId == account })
            positions += page.items.map { it.position }
            cursor = page.next
        } while (cursor != null)
        assertEquals(5, pages)
        assertEquals(
            companies.flatMap { company ->
                devices.map { CollaborationLegacyDevicePosition(company, it) }
            },
            positions,
        )
        assertEquals(positions.size, positions.distinct().size)
        val exhausted = page(positions.last())
        assertTrue(exhausted.items.isEmpty())
        assertNull(exhausted.next)
        assertEquals(page(), page(owner = actor.copy(companyId = companies.first())))
        assertTrue(page(owner = actor.copy(accountId = UUID.randomUUID())).items.isEmpty())
        assertTrue(page(selectedSpace = UUID.randomUUID()).items.isEmpty())
        sql.update(
            "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
            companies.last(),
            account,
        )
        assertTrue(page().items.isEmpty())
    }

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun <T> success(value: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, value)
        return (value as Result.Success).value
    }
}
