package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationSpaceDataSource
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationSpaceRepository
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
class CollaborationSpaceDirectoryTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun groupSearchPagesAccountsBeforeBoundingTheirLabelsAndDoesNotSearchLoginEmails() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).load().migrate()
        val sql = JdbcTemplate(source)
        val companies = List(12) { UUID.randomUUID() }.sortedBy { it.toString() }
        val caller = UUID.randomUUID()
        val peers = List(5) { UUID(0, it.toLong() + 1) }
        for ((index, id) in companies.withIndex()) {
            sql.update(
                "insert into companies(id,code,name,timezone) values(?,?,?,'UTC')",
                id,
                "SPACE$index",
                "Fictional company $index",
            )
        }
        val names = listOf("Rani O'Brien %_!", "Rani Other", "Bima", "Nadia", "Dimas")
        for ((index, id) in (peers + caller).withIndex()) {
            sql.update(
                "insert into accounts(id,email,display_name) values(?,?,?)",
                id,
                "directory-$index@example.test",
                names.getOrNull(index) ?: "Caller",
            )
            for (company in
                if (id == peers.first()) companies else listOf(companies[index % companies.size])) {
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
        }
        sql.execute("create role group_directory_fixture login password 'isolated-fixture-only'")
        sql.execute("grant usage on schema public to group_directory_fixture")
        sql.execute("grant select on all tables in schema public to group_directory_fixture")
        val runtime =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "group_directory_fixture",
                "isolated-fixture-only",
            )
        val transactions =
            PostgresTransactionRunner(JdbcTransactionManager(runtime), JdbcTemplate(runtime))
        val repository =
            StoredCollaborationSpaceRepository(
                PostgresCollaborationSpaceDataSource(
                    DSL.using(TransactionAwareDataSourceProxy(runtime), SQLDialect.POSTGRES)
                )
            )
        val actor = Actor(caller, null, emptySet(), Instant.now(), UUID.randomUUID())
        fun <T> success(value: Result<T>) = (value as Result.Success).value
        val space = requireNotNull(success(transactions.run(actor) { repository.current() }))
        fun page(
            query: String = "",
            after: UUID? = null,
            limit: Int = 2,
            current: Actor = actor,
            selectedSpace: UUID = space,
        ) =
            success(
                transactions.run(current) {
                    repository.contacts(selectedSpace, caller, query, after, limit)
                }
            )
        val first = page()
        assertEquals(peers.take(2), first.items.map { it.accountId })
        assertEquals(companies.take(8), first.items.first().companies.map { it.id })
        assertTrue(first.items.first().hasMoreCompanies)
        assertEquals(1, first.items.last().companies.size)
        assertFalse(first.items.last().hasMoreCompanies)
        val second = page(after = UUID.fromString(first.nextCursor))
        val third = page(after = UUID.fromString(second.nextCursor))
        assertEquals(peers, (first.items + second.items + third.items).map { it.accountId })
        assertNull(third.nextCursor)
        val exhausted = page(after = peers.last())
        assertTrue(exhausted.items.isEmpty())
        assertNull(exhausted.nextCursor)
        assertEquals(first, page(current = actor.copy(companyId = companies.first())))
        assertEquals(first, page(current = actor.copy(companyId = companies.last())))
        assertEquals(listOf(peers.first()), page(query = "%_!").items.map { it.accountId })
        assertEquals(listOf(peers.first()), page(query = "O'Brien").items.map { it.accountId })
        assertEquals(peers.take(2), page(query = "rAnI").items.map { it.accountId })
        assertTrue(page(query = "@example.test").items.isEmpty())
        assertTrue(page(selectedSpace = UUID.randomUUID()).items.isEmpty())
        assertTrue(page(current = actor.copy(accountId = UUID.randomUUID())).items.isEmpty())
        sql.update(
            "delete from membership_permissions where account_id=? and permission='collaboration.chat'",
            peers.first(),
        )
        assertEquals(peers.drop(1), page(limit = 50).items.map { it.accountId })
        sql.update(
            "update company_memberships set active=false,version=version+1 where account_id=?",
            caller,
        )
        assertNull(success(transactions.run(actor) { repository.current() }))
        assertTrue(page().items.isEmpty())
    }
}
