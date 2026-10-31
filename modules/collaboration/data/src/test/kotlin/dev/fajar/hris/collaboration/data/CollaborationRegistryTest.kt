package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationRegistryDataSource
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationRegistryRepository
import dev.fajar.hris.collaboration.domain.entities.*
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
class CollaborationRegistryTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }

    @Test
    fun quotaCountsDistinctLiveParentsAndBindingFailuresRollBackDeviceCreation() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).load().migrate()
        val sql = JdbcTemplate(source)
        val company = UUID.randomUUID()
        val secondCompany = UUID.randomUUID()
        val account = UUID.randomUUID()
        val otherAccount = UUID.randomUUID()
        val firstParent = UUID.randomUUID()
        val secondParent = UUID.randomUUID()
        val unrelatedParent = UUID.randomUUID()
        sql.update(
            "insert into companies(id,code,name,timezone) values(?,'REG','Registry fixture','UTC'),(?,'OTHER','Other fixture','UTC')",
            company,
            secondCompany,
        )
        sql.update(
            "insert into accounts(id,email,display_name) values(?,'registry@example.test','Registry fixture'),(?,'unrelated@example.test','Other fixture')",
            account,
            otherAccount,
        )
        for ((parent, owner, hash) in
            listOf(
                Triple(firstParent, account, "a"),
                Triple(secondParent, account, "b"),
                Triple(unrelatedParent, otherAccount, "c"),
            )) {
            sql.update(
                "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,access_hash,access_expires_at,credential_version) values(?,?,repeat(?,64),now(),now()+interval '1 day',repeat(?,64),now()+interval '15 minutes',0)",
                parent,
                owner,
                hash,
                hash,
            )
        }
        sql.execute("create role registry_fixture login password 'registry-fixture-only'")
        sql.execute("grant usage on schema public to registry_fixture")
        sql.execute(
            "grant select,insert,update,delete on all tables in schema public to registry_fixture"
        )
        val runtimeSource =
            DriverManagerDataSource(postgres.jdbcUrl, "registry_fixture", "registry-fixture-only")
        val runtime = JdbcTemplate(runtimeSource)
        val transactions = PostgresTransactionRunner(JdbcTransactionManager(runtimeSource), runtime)
        val repository =
            StoredCollaborationRegistryRepository(
                PostgresCollaborationRegistryDataSource(
                    DSL.using(TransactionAwareDataSourceProxy(runtimeSource), SQLDialect.POSTGRES)
                )
            )
        val actor = Actor(account, company, emptySet(), Instant.now(), UUID.randomUUID())
        fun registration(selectedCompany: UUID = company, parent: UUID = firstParent) =
            CollaborationRegistration(
                UUID.randomUUID(),
                selectedCompany,
                account,
                parent,
                CollaborationPlatform.ANDROID,
                "Fictional emulator",
                Instant.now(),
            )
        val first = registration()
        assertEquals(Result.Success(Unit), transactions.run(actor) { repository.register(first) })
        assertEquals(
            Result.Success(1),
            transactions.run(actor) { repository.countActiveParentSessions(account, Instant.now()) },
        )
        val secondCompanyDevice = registration(secondCompany)
        assertEquals(
            Result.Success(Unit),
            transactions.run(actor.copy(companyId = secondCompany)) {
                repository.register(secondCompanyDevice)
            },
        )
        assertEquals(
            Result.Success(1),
            transactions.run(actor) { repository.countActiveParentSessions(account, Instant.now()) },
        )
        val second = registration(parent = secondParent)
        assertEquals(Result.Success(Unit), transactions.run(actor) { repository.register(second) })
        assertEquals(
            Result.Success(2),
            transactions.run(actor) { repository.countActiveParentSessions(account, Instant.now()) },
        )
        val unowned = registration(parent = unrelatedParent)
        assertInstanceOf(
            Result.Failed::class.java,
            transactions.run(actor) { repository.register(unowned) },
        )
        assertEquals(
            0,
            sql.queryForObject(
                "select count(*) from collaboration_devices where id=?",
                Int::class.java,
                unowned.deviceId,
            ),
        )
        val duplicate = registration()
        assertInstanceOf(
            Result.Failed::class.java,
            transactions.run(actor) { repository.register(duplicate) },
        )
        assertEquals(
            0,
            sql.queryForObject(
                "select count(*) from collaboration_devices where id=?",
                Int::class.java,
                duplicate.deviceId,
            ),
        )
        val found = transactions.run(actor) { repository.findBound(company, account, firstParent) }
        assertInstanceOf(Result.Success::class.java, found)
        assertEquals(first.deviceId, (found as Result.Success).value?.id)
        assertEquals(company, found.value?.companyId)
        assertEquals(CollaborationDeviceState.PROVISIONING, found.value?.state)
        assertEquals(
            Result.Success(null),
            transactions.run(actor.copy(accountId = otherAccount)) {
                repository.findBound(company, account, firstParent)
            },
        )
        sql.update("update native_sessions set revoked_at=now() where id=?", firstParent)
        assertEquals(
            Result.Success(1),
            transactions.run(actor) { repository.countActiveParentSessions(account, Instant.now()) },
        )
        // Token refresh expiry still occupies a device slot until the parent
        // session expires or is revoked; stale device credentials occupy none.
        sql.update(
            "update native_sessions set access_expires_at=now()-interval '1 second' where id=?",
            secondParent,
        )
        assertEquals(
            Result.Success(1),
            transactions.run(actor) { repository.countActiveParentSessions(account, Instant.now()) },
        )
        sql.update("update accounts set security_version=security_version+1 where id=?", account)
        assertEquals(
            Result.Success(0),
            transactions.run(actor) { repository.countActiveParentSessions(account, Instant.now()) },
        )
    }
}
