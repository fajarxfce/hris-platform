package dev.fajar.hris

import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.data.datasources.PostgresCompanyDataSource
import dev.fajar.hris.organization.data.repositories.StoredCompanyRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.data.datasources.PostgresDirectoryProjectionDataSource
import dev.fajar.hris.people.data.repositories.StoredDirectoryProjectionRepository
import dev.fajar.hris.people.domain.repositories.*
import dev.fajar.hris.people.domain.usecases.GetDirectoryRecord
import dev.fajar.hris.people.domain.usecases.RefreshCompanyDirectory
import dev.fajar.hris.sync.domain.entities.*
import dev.fajar.hris.sync.domain.repositories.*
import dev.fajar.hris.sync.domain.usecases.*
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.JsonNode

@TestPropertySource(properties = [MOBILE_SYNC_TEST_KEYS])
@Import(AccountLockProbeConfiguration::class)
class DirectoryRecordHttpTest : PeopleApiFixture() {
    @Autowired private lateinit var accounts: AccountLockProbe
    @Autowired private lateinit var context: org.springframework.context.ApplicationContext
    private lateinit var refresh: RefreshCompanyDirectory
    private lateinit var publisher: MobileSyncTestWorker

    private data class Member(val id: UUID, val client: HttpClient)

    @BeforeEach
    fun prepareWorker() {
        val db = database()
        db.execute(
            """do ${'$'}${'$'} begin if not exists(select 1 from pg_roles where rolname='directory_projection_test') then create role directory_projection_test login password 'projection-fixture-only' nosuperuser nobypassrls;end if;end ${'$'}${'$'}"""
        )
        db.execute("grant hris_worker_capability to directory_projection_test")
        db.execute("grant usage on schema public to directory_projection_test")
        db.execute(
            "grant select,insert,update,delete on all tables in schema public to directory_projection_test"
        )
        val source =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "directory_projection_test",
                "projection-fixture-only",
            )
        val sql =
            DSL.using(
                TransactionAwareDataSourceProxy(source),
                SQLDialect.POSTGRES,
                Settings().withExecuteLogging(false),
            )
        refresh =
            RefreshCompanyDirectory(
                StoredDirectoryProjectionRepository(PostgresDirectoryProjectionDataSource(sql)),
                StoredCompanyRepository(
                    PostgresCompanyDataSource(sql),
                    dev.fajar.hris.core.database.datasources.PostgresOperationReceiptDataSource(
                        sql
                    ),
                    json,
                ),
                PostgresTransactionRunner(JdbcTransactionManager(source), JdbcTemplate(source)),
                clock,
            )
        publisher = mobileSyncTestWorker(postgres.jdbcUrl, db)
        // Retire only pending work in this class's isolated fixture database.
        db.update("update directory_refresh_queue set refresh_at=null")
    }

    @AfterEach
    fun releaseProbe() {
        accounts.current.getAndSet(null)?.release?.countDown()
    }

    private fun member(company: UUID, permission: String = "people.directory.read"): Member {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Directory reader',password_hash from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                id,
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                company,
                id,
                permission,
            )
        val client = client()
        login(client, "$id@example.test")
        return Member(id, client)
    }

    private fun body(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    private fun <T> success(result: Result<T>): T {
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    private fun syncPath(company: UUID, phase: String) =
        "/api/v1/companies/$company/sync/$phase?collections=DIRECTORY_EMPLOYEES"

    private fun cursor(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

    @Test
    fun authorizedReadersReceiveOnlyPublicCoworkersAndTheirCommittedChangesWithoutOwningAnEmployment() {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val employee = employee(admin, csrf, company)
        employee(admin, csrf, company, start = "2027-01-01")
        val reader = member(company)
        val before = body(get(reader.client, syncPath(company, "bootstrap")))
        assertEquals(
            listOf("DIRECTORY_EMPLOYEES"),
            before["collections"].iterator().asSequence().map { it.asString() }.toList(),
        )
        assertEquals(0, before["items"].size())
        var token = before["changesCursor"].asString()
        val pending =
            body(get(reader.client, syncPath(company, "changes") + "&cursor=${cursor(token)}"))
        assertTrue(pending["pendingPublication"].asBoolean())
        assertEquals(2, success(refresh.execute()))
        success(publisher.publish())
        val delta =
            body(get(reader.client, syncPath(company, "changes") + "&cursor=${cursor(token)}"))
        assertEquals(1, delta["items"].size())
        assertEquals(employee.toString(), delta["items"][0]["id"].asString())
        assertEquals("UPSERT", delta["items"][0]["operation"].asString())
        assertFalse(delta["pendingPublication"].asBoolean())
        token = delta["cursor"].asString()
        val record =
            body(get(reader.client, "/api/v1/companies/$company/employees/directory/$employee"))
        assertEquals(8, record.size())
        assertEquals(employee.toString(), record["id"].asString())
        assertEquals("Example employee", record["name"].asString())
        assertEquals(0, record["version"].asInt())
        for (field in
            listOf(
                "email",
                "accountId",
                "personId",
                "birthDate",
                "nationality",
                "startDate",
                "status",
            )) assertFalse(record.has(field))
        val snapshot = body(get(reader.client, syncPath(company, "bootstrap")))
        assertEquals(employee.toString(), snapshot["items"][0]["id"].asString())
        assertEquals(
            403,
            get(reader.client, "/api/v1/companies/$company/sync/bootstrap").statusCode(),
        )
        val suspended =
            revise(admin, csrf, company, employee, 0, terms("2026-09-01", status = "SUSPENDED"))
        assertEquals(200, suspended.statusCode(), suspended.body())
        success(refresh.execute())
        success(publisher.publish())
        val removed =
            body(get(reader.client, syncPath(company, "changes") + "&cursor=${cursor(token)}"))
        assertEquals("DELETE", removed["items"][0]["operation"].asString())
        assertEquals(employee.toString(), removed["items"][0]["id"].asString())
        assertEquals(1, removed["items"][0]["version"].asInt())
        val missing = get(reader.client, "/api/v1/companies/$company/employees/directory/$employee")
        assertEquals(404, missing.statusCode())
        assertEquals(
            "directory_employee_not_found",
            json.readTree(missing.body())["code"].asString(),
        )
        assertEquals(0, body(get(reader.client, syncPath(company, "bootstrap")))["items"].size())
    }

    @Test
    fun foreignResourcesAndMissingGrantsCannotReadTheProjectionOrItsPendingState() {
        val admin = client()
        val csrf = login(admin)
        val own = company(admin, csrf)
        val foreign = company(admin, csrf)
        val hidden = employee(admin, csrf, foreign)
        val reader = member(own)
        val denied = member(own, "company.read")
        success(refresh.execute())
        assertEquals(
            404,
            get(reader.client, "/api/v1/companies/$own/employees/directory/$hidden").statusCode(),
        )
        assertEquals(
            403,
            get(reader.client, "/api/v1/companies/$foreign/employees/directory/$hidden")
                .statusCode(),
        )
        assertEquals(403, get(denied.client, syncPath(own, "bootstrap")).statusCode())
        assertEquals(
            403,
            get(denied.client, "/api/v1/companies/$own/employees/directory/$hidden").statusCode(),
        )
        val empty = body(get(reader.client, syncPath(own, "bootstrap")))
        assertEquals(0, empty["items"].size())
        val changes =
            body(
                get(
                    reader.client,
                    syncPath(own, "changes") +
                        "&cursor=${cursor(empty["changesCursor"].asString())}",
                )
            )
        assertFalse(changes["pendingPublication"].asBoolean())
    }

    @ParameterizedTest
    @ValueSource(strings = ["permission", "membership", "credential"])
    fun recordRechecksCurrentAuthorityAfterWaitingForTheAccountGuard(change: String) {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val employee = employee(admin, csrf, company)
        val reader = member(company)
        success(refresh.execute())
        val barrier = AccountLockProbe.Barrier(reader.id)
        accounts.current.set(barrier)
        Executors.newSingleThreadExecutor().use { executor ->
            val pending =
                executor.submit<HttpResponse<String>> {
                    get(reader.client, "/api/v1/companies/$company/employees/directory/$employee")
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                when (change) {
                    "credential" ->
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                reader.id,
                            )
                    "membership" ->
                        database()
                            .update(
                                "update company_memberships set active=false where company_id=? and account_id=?",
                                company,
                                reader.id,
                            )
                    else ->
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=?",
                                company,
                                reader.id,
                            )
                }
                barrier.release.countDown()
                val response = pending.get(10, TimeUnit.SECONDS)
                assertEquals(
                    if (change == "credential") 401 else 403,
                    response.statusCode(),
                    response.body(),
                )
                assertFalse(response.body().contains("Example employee"))
            } finally {
                barrier.release.countDown()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["record", "bootstrap", "changes"])
    fun proofExpiryDuringAcquisitionCannotReleaseDirectoryData(phase: String) {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val employee = employee(admin, csrf, company)
        val reader = member(company, "people.read")
        success(refresh.execute())
        success(publisher.publish())
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                reader.id,
            )
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val actor =
            Actor(
                reader.id,
                company,
                setOf("people.read"),
                clock.instant(),
                UUID.randomUUID(),
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1),
                credentialVersion = 0,
            )
        val tx = context.getBean(TransactionRunner::class.java)
        val companies = context.getBean(CompanyRepository::class.java)
        val members = context.getBean(MembershipRepository::class.java)
        val identities = context.getBean(IdentityRepository::class.java)
        val people = context.getBean(PeopleRepository::class.java)
        val cursors = context.getBean(SyncCursorRepository::class.java)
        val sync = context.getBean(SyncRepository::class.java)
        val directory = context.getBean(DirectoryProjectionRepository::class.java)
        val delayedDirectory =
            object : DirectoryProjectionRepository by directory {
                override fun find(company: UUID, id: UUID) =
                    directory.find(company, id).also { clock.set(clock.instant().plusSeconds(2)) }
            }
        val delayedSync =
            object : SyncRepository by sync {
                override fun snapshot(scope: SyncScope, after: SyncResourceKey?, limit: Int) =
                    sync.snapshot(scope, after, limit).also {
                        clock.set(clock.instant().plusSeconds(2))
                    }

                override fun changes(scope: SyncScope, after: Long, upper: Long, limit: Int) =
                    sync.changes(scope, after, upper, limit).also {
                        clock.set(clock.instant().plusSeconds(2))
                    }
            }
        val bootstrap =
            GetMobileSyncBootstrap(
                sync,
                cursors,
                people,
                companies,
                members,
                identities,
                tx,
                clock,
                security,
            )
        val selection = setOf(SyncCollection.DIRECTORY_EMPLOYEES)
        val cursor =
            if (phase == "changes")
                success(bootstrap.execute(actor, requestedCollections = selection)).changesCursor!!
            else null
        fun acquire(current: Actor): Result<*> =
            when (phase) {
                "record" ->
                    GetDirectoryRecord(
                            delayedDirectory,
                            companies,
                            members,
                            identities,
                            tx,
                            security,
                            clock,
                        )
                        .execute(current, employee)
                "bootstrap" ->
                    GetMobileSyncBootstrap(
                            delayedSync,
                            cursors,
                            people,
                            companies,
                            members,
                            identities,
                            tx,
                            clock,
                            security,
                        )
                        .execute(current, requestedCollections = selection)
                else ->
                    GetMobileSyncChanges(
                            delayedSync,
                            cursors,
                            people,
                            companies,
                            members,
                            identities,
                            tx,
                            clock,
                            security,
                        )
                        .execute(current, requireNotNull(cursor), requestedCollections = selection)
            }
        assertEquals("mfa_required", (acquire(actor) as? Result.Failed)?.failure?.code)
        assertTrue(acquire(actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success)
    }
}
