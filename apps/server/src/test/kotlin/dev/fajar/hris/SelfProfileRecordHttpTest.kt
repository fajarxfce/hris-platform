package dev.fajar.hris

import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.data.datasources.PostgresCompanyDataSource
import dev.fajar.hris.organization.data.repositories.StoredCompanyRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.data.datasources.PostgresSelfProfileProjectionDataSource
import dev.fajar.hris.people.data.repositories.StoredSelfProfileProjectionRepository
import dev.fajar.hris.people.domain.repositories.*
import dev.fajar.hris.people.domain.usecases.GetSelfProfileRecord
import dev.fajar.hris.people.domain.usecases.RefreshSelfProfiles
import dev.fajar.hris.sync.domain.entities.*
import dev.fajar.hris.sync.domain.repositories.*
import dev.fajar.hris.sync.domain.usecases.*
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.CancellationException
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
class SelfProfileRecordHttpTest : PeopleApiFixture() {
    @Autowired private lateinit var accounts: AccountLockProbe
    @Autowired private lateinit var profiles: SelfProfileProjectionRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var sync: SyncRepository
    @Autowired private lateinit var cursors: SyncCursorRepository
    private lateinit var refresh: RefreshSelfProfiles
    private lateinit var publisher: MobileSyncTestWorker

    private data class Member(val id: UUID, val client: HttpClient)

    @BeforeEach
    fun prepareWorker() {
        val db = database()
        db.execute(
            """do ${'$'}${'$'} begin if not exists(select 1 from pg_roles where rolname='self_profile_projection_test') then create role self_profile_projection_test login password 'projection-fixture-only' nosuperuser nobypassrls;end if;end ${'$'}${'$'}"""
        )
        db.execute("grant hris_worker_capability to self_profile_projection_test")
        db.execute("grant usage on schema public to self_profile_projection_test")
        db.execute(
            "grant select,insert,update,delete on all tables in schema public to self_profile_projection_test"
        )
        val source =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "self_profile_projection_test",
                "projection-fixture-only",
            )
        val sql =
            DSL.using(
                TransactionAwareDataSourceProxy(source),
                SQLDialect.POSTGRES,
                Settings().withExecuteLogging(false),
            )
        refresh =
            RefreshSelfProfiles(
                StoredSelfProfileProjectionRepository(
                    PostgresSelfProfileProjectionDataSource(sql),
                    json,
                ),
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
        // Only retire pending work in this class's isolated fixture database.
        db.update("update self_profile_refresh_queue set refresh_at=null")
    }

    @AfterEach
    fun releaseProbe() {
        accounts.current.getAndSet(null)?.release?.countDown()
    }

    private fun member(
        company: UUID,
        permissions: Set<String> = setOf("people.self.read"),
    ): Member {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Profile reader',password_hash from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                id,
            )
        for (permission in permissions) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                company,
                id,
                permission,
            )
        val browser = client()
        login(browser, "$id@example.test")
        return Member(id, browser)
    }

    private fun body(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    private fun <T> success(result: Result<T>): T {
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    private fun recordPath(company: UUID, employee: UUID) =
        "/api/v1/companies/$company/employees/me/profiles/$employee"

    private fun syncPath(company: UUID, phase: String) =
        "/api/v1/companies/$company/sync/$phase?collections=SELF_PROFILES"

    private fun cursor(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

    private fun actor(
        reader: Member,
        company: UUID,
        permissions: Set<String> = setOf("people.self.read"),
    ) =
        Actor(
            reader.id,
            company,
            permissions,
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = 0,
        )

    private fun reader(
        repository: SelfProfileProjectionRepository = profiles,
        security: IdentitySecurityPolicy = IdentitySecurityPolicy(enforceMfa = false),
    ) =
        GetSelfProfileRecord(
            repository,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Test
    fun explicitFeedAndCanonicalRecordContainOnlyTheOwnersPublishedPrivateProfile() {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val owner = member(company)
        val managerAccount = member(company)
        val manager = employee(admin, csrf, company, account = managerAccount.id)
        val employment = employee(admin, csrf, company, manager, owner.id)
        employee(admin, csrf, company)
        val futureOwner = member(company)
        employee(admin, csrf, company, account = futureOwner.id, start = "2027-01-01")

        val before = body(get(owner.client, syncPath(company, "bootstrap")))
        assertEquals(0, before["items"].size())
        assertEquals(
            listOf("SELF_PROFILES"),
            before["collections"].iterator().asSequence().map { it.asString() }.toList(),
        )
        val token = before["changesCursor"].asString()
        val pending =
            body(get(owner.client, syncPath(company, "changes") + "&cursor=${cursor(token)}"))
        assertTrue(pending["pendingPublication"].asBoolean())
        assertEquals(4, success(refresh.execute()))
        success(publisher.publish())

        val delta =
            body(get(owner.client, syncPath(company, "changes") + "&cursor=${cursor(token)}"))
        assertEquals(1, delta["items"].size())
        assertEquals(employment.toString(), delta["items"][0]["id"].asString())
        assertEquals("UPSERT", delta["items"][0]["operation"].asString())
        assertFalse(delta["pendingPublication"].asBoolean())
        val record = body(get(owner.client, recordPath(company, employment)))
        assertEquals(5, record.size())
        assertEquals(employment.toString(), record["id"].asString())
        assertEquals(0, record["version"].asLong())
        assertEquals("Asia/Jakarta", record["timezone"].asString())
        assertEquals(owner.id.toString(), record["profile"]["accountId"].asString())
        assertEquals(company.toString(), record["profile"]["ownerCompanyId"].asString())
        assertEquals("ID", record["profile"]["nationality"].asString())
        assertEquals(manager.toString(), record["employment"]["manager"]["id"].asString())
        assertEquals(4, record["employment"]["manager"].size())
        assertFalse(record.toString().contains(managerAccount.id.toString()))
        assertEquals(404, get(owner.client, recordPath(company, manager)).statusCode())
        assertEquals(404, get(admin, recordPath(company, employment)).statusCode())
        val bootstrapped = body(get(owner.client, syncPath(company, "bootstrap")))
        assertEquals(
            listOf(employment.toString()),
            bootstrapped["items"].iterator().asSequence().map { it["id"].asString() }.toList(),
        )
        assertEquals(
            0,
            body(get(futureOwner.client, syncPath(company, "bootstrap")))["items"].size(),
        )
        assertEquals(
            403,
            get(owner.client, "/api/v1/companies/$company/employees/directory/$manager")
                .statusCode(),
        )
    }

    @Test
    fun privateAndEmploymentChangesPublishOneCoherentSnapshotWithoutAnAgeBasedRewrite() {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val owner = member(company)
        val employment = employee(admin, csrf, company, account = owner.id)
        success(refresh.execute())
        success(publisher.publish())
        val original = body(get(owner.client, recordPath(company, employment)))
        val token =
            body(get(owner.client, syncPath(company, "bootstrap")))["changesCursor"].asString()
        val changed =
            command(
                admin,
                "/api/v1/companies/$company/employees/$employment/profile",
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to 0,
                        "legalName" to "Updated own profile",
                        "birthDate" to "1990-03-04",
                        "nationality" to "ID",
                        "email" to "private-profile@example.test",
                        "reason" to "Verified personnel record",
                    )
                ),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, changed.statusCode(), changed.body())
        val revised =
            revise(admin, csrf, company, employment, 0, terms("2026-09-01", status = "SUSPENDED"))
        assertEquals(200, revised.statusCode(), revised.body())
        assertEquals(original, body(get(owner.client, recordPath(company, employment))))
        success(refresh.execute())
        success(publisher.publish())
        val current = body(get(owner.client, recordPath(company, employment)))
        assertEquals(1, current["version"].asLong())
        assertEquals(1, current["employment"]["employee"]["version"].asLong())
        assertEquals(1, current["profile"]["version"].asLong())
        assertEquals("SUSPENDED", current["employment"]["employee"]["terms"]["status"].asString())
        assertEquals("Updated own profile", current["profile"]["legalName"].asString())
        assertEquals(
            "Updated own profile",
            current["employment"]["employee"]["person"]["legalName"].asString(),
        )
        assertEquals("1990-03-04", current["profile"]["birthDate"].asString())
        val delta =
            body(get(owner.client, syncPath(company, "changes") + "&cursor=${cursor(token)}"))
        assertEquals(1, delta["items"].size())
        assertEquals(1, delta["items"][0]["version"].asLong())
        clock.set(clock.instant().plusSeconds(73 * 3600))
        database()
            .update(
                "update self_profile_refresh_queue set refresh_at='-infinity' where company_id=?",
                company,
            )
        success(refresh.execute())
        assertEquals(current, body(get(owner.client, recordPath(company, employment))))
    }

    @Test
    fun foreignCompanyAndPartialReadGrantsCannotAcquirePrivateRecordsOrPendingState() {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val foreign = company(admin, csrf)
        val owner = member(foreign)
        val hidden = employee(admin, csrf, foreign, account = owner.id)
        val reader = member(company, setOf("people.read", "people.profile.read"))
        success(refresh.execute())
        assertEquals(404, get(reader.client, recordPath(company, hidden)).statusCode())
        assertEquals(403, get(reader.client, recordPath(foreign, hidden)).statusCode())
        val empty = body(get(reader.client, syncPath(company, "bootstrap")))
        assertEquals(0, empty["items"].size())
        val pending =
            body(
                get(
                    reader.client,
                    syncPath(company, "changes") +
                        "&cursor=${cursor(empty["changesCursor"].asString())}",
                )
            )
        assertFalse(pending["pendingPublication"].asBoolean())
        for (permission in listOf("people.read", "people.profile.read", "people.directory.read")) {
            val denied = member(company, setOf(permission))
            assertEquals(403, get(denied.client, recordPath(company, hidden)).statusCode())
            assertEquals(403, get(denied.client, syncPath(company, "bootstrap")).statusCode())
        }
        assertEquals(
            403,
            get(owner.client, "/api/v1/companies/$foreign/sync/bootstrap").statusCode(),
        )
    }

    @Test
    fun broaderGrantsStillExportOnlyOwnEmploymentAndOldCursorsCannotCrossAccounts() {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val first = member(company, setOf("people.read", "people.profile.read"))
        val second = member(company, setOf("people.read", "people.profile.read"))
        val firstEmployment = employee(admin, csrf, company, account = first.id)
        val secondEmployment = employee(admin, csrf, company, account = second.id)
        success(refresh.execute())
        success(publisher.publish())
        val firstPage = body(get(first.client, syncPath(company, "bootstrap")))
        assertEquals(
            listOf(firstEmployment.toString()),
            firstPage["items"].iterator().asSequence().map { it["id"].asString() }.toList(),
        )
        val secondPage = body(get(second.client, syncPath(company, "bootstrap")))
        assertEquals(
            listOf(secondEmployment.toString()),
            secondPage["items"].iterator().asSequence().map { it["id"].asString() }.toList(),
        )
        assertEquals(404, get(first.client, recordPath(company, secondEmployment)).statusCode())
        val token = firstPage["changesCursor"].asString()
        val reused = get(second.client, syncPath(company, "changes") + "&cursor=${cursor(token)}")
        assertEquals(422, reused.statusCode(), reused.body())
        assertEquals("invalid_sync_cursor", json.readTree(reused.body())["code"].asString())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='people.profile.read'",
                company,
                first.id,
            )
        assertEquals(
            403,
            get(first.client, syncPath(company, "changes") + "&cursor=${cursor(token)}")
                .statusCode(),
        )
        assertEquals(403, get(first.client, recordPath(company, firstEmployment)).statusCode())
    }

    @Test
    fun disablingPeopleGatesTheCanonicalRecordAndFeedWithoutDestroyingThePublishedProfile() {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val owner = member(company)
        val employment = employee(admin, csrf, company, account = owner.id)
        success(refresh.execute())
        success(publisher.publish())
        val original = body(get(owner.client, recordPath(company, employment)))
        val token =
            body(get(owner.client, syncPath(company, "bootstrap")))["changesCursor"].asString()
        for ((index, disabled) in listOf(listOf("PEOPLE"), emptyList()).withIndex()) {
            val updated =
                command(
                    admin,
                    "/api/v1/companies/$company/settings/client-policy",
                    json.writeValueAsString(
                        mapOf(
                            "expectedVersion" to if (index == 0) null else 0,
                            "activateAt" to null,
                            "disabledModules" to disabled,
                            "minimumBuilds" to mapOf("android" to 0, "ios" to 0, "web" to 0),
                            "maintenance" to null,
                            "reason" to "Company capability configuration",
                        )
                    ),
                    csrf,
                    UUID.randomUUID(),
                    "PUT",
                )
            assertEquals(200, updated.statusCode(), updated.body())
            for (path in
                listOf(
                    recordPath(company, employment),
                    syncPath(company, "bootstrap"),
                    syncPath(company, "changes") + "&cursor=${cursor(token)}",
                )) {
                val response = get(owner.client, path)
                assertEquals(if (index == 0) 403 else 200, response.statusCode(), response.body())
                if (index == 0)
                    assertEquals(
                        "company_module_disabled",
                        json.readTree(response.body())["code"].asString(),
                    )
            }
        }
        assertEquals(original, body(get(owner.client, recordPath(company, employment))))
    }

    @ParameterizedTest
    @ValueSource(strings = ["permission", "membership", "credential"])
    fun recordRechecksCurrentAuthorityAfterWaitingForItsAccountGuard(change: String) {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val owner = member(company)
        val employment = employee(admin, csrf, company, account = owner.id)
        success(refresh.execute())
        // Admission owns the first guard; stop inside the canonical read itself.
        val barrier = AccountLockProbe.Barrier(owner.id, occurrence = 2)
        accounts.current.set(barrier)
        Executors.newSingleThreadExecutor().use { executor ->
            val pending =
                executor.submit<HttpResponse<String>> {
                    get(owner.client, recordPath(company, employment))
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                when (change) {
                    "credential" ->
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                owner.id,
                            )
                    "membership" ->
                        database()
                            .update(
                                "update company_memberships set active=false where company_id=? and account_id=?",
                                company,
                                owner.id,
                            )
                    else ->
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=?",
                                company,
                                owner.id,
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
                assertFalse(response.body().contains("nationality"))
            } finally {
                barrier.release.countDown()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["record", "bootstrap", "changes"])
    fun proofExpiryDuringAcquisitionCannotReleasePrivateProfileData(phase: String) {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val grants = setOf("people.read", "people.profile.read")
        val owner = member(company, grants)
        val employment = employee(admin, csrf, company, account = owner.id)
        success(refresh.execute())
        success(publisher.publish())
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                owner.id,
            )
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val actor =
            actor(owner, company, grants)
                .copy(mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1))
        val delayedProfile =
            object : SelfProfileProjectionRepository by profiles {
                override fun find(company: UUID, id: UUID) =
                    profiles.find(company, id).also { clock.set(clock.instant().plusSeconds(2)) }
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
        val selection = setOf(SyncCollection.SELF_PROFILES)
        val bootstrap =
            GetMobileSyncBootstrap(
                sync,
                cursors,
                people,
                companies,
                members,
                identities,
                transactions,
                clock,
                security,
            )
        val token =
            if (phase == "changes")
                success(bootstrap.execute(actor, requestedCollections = selection)).changesCursor!!
            else null
        fun acquire(current: Actor): Result<*> =
            when (phase) {
                "record" -> reader(delayedProfile, security).execute(current, employment)
                "bootstrap" ->
                    GetMobileSyncBootstrap(
                            delayedSync,
                            cursors,
                            people,
                            companies,
                            members,
                            identities,
                            transactions,
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
                            transactions,
                            clock,
                            security,
                        )
                        .execute(current, requireNotNull(token), requestedCollections = selection)
            }
        assertEquals("mfa_required", (acquire(actor) as? Result.Failed)?.failure?.code)
        assertTrue(acquire(actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success)
    }

    @Test
    fun cancelledPrivateAcquisitionReleasesTheTransactionAndItsGuards() {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val owner = member(company)
        val employment = employee(admin, csrf, company, account = owner.id)
        success(refresh.execute())
        val cancelled =
            object : SelfProfileProjectionRepository by profiles {
                override fun find(company: UUID, id: UUID) =
                    profiles.find(company, id).also {
                        throw CancellationException("fixture cancellation")
                    }
            }
        assertThrows(CancellationException::class.java) {
            reader(cancelled).execute(actor(owner, company), employment)
        }
        assertEquals(200, get(owner.client, recordPath(company, employment)).statusCode())
        val update = revise(admin, csrf, company, employment, 0, terms("2026-09-01"))
        assertEquals(200, update.statusCode(), update.body())
    }
}
