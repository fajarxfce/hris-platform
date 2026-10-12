package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.jobs.domain.usecases.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class, JobAssuranceProbeConfiguration::class)
class JobAccessHttpTest : PeopleApiFixture() {
    @Autowired private lateinit var jobs: JobRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var probe: AccountLockProbe
    @Autowired private lateinit var assuranceProbe: JobAssuranceProbe
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var journal: ChangeJournalRepository

    private data class Member(val id: UUID, val client: HttpClient, val csrf: String)

    private data class Fixture(
        val company: UUID,
        val owner: Member,
        val operator: Member,
        val job: UUID,
    ) {
        val path
            get() = "/api/v1/companies/$company/jobs/$job"
    }

    @AfterEach
    fun clearProbe() {
        probe.current.getAndSet(null)?.release?.countDown()
        assuranceProbe.current.getAndSet(null)?.release?.countDown()
    }

    private fun member(company: UUID, permissions: Set<String>): Member {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Job fixture',password_hash from accounts where email='admin@example.test'",
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
        return Member(id, browser, login(browser, "$id@example.test"))
    }

    private fun fixture(
        operatorPermissions: Set<String> = setOf("jobs.read", "jobs.manage")
    ): Fixture {
        val admin = client()
        val company = company(admin, login(admin))
        val owner = member(company, emptySet())
        val operator = member(company, operatorPermissions)
        val actor =
            Actor(
                owner.id,
                company,
                emptySet(),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val id = UUID.randomUUID()
        val result =
            transactions.run(actor) {
                jobs.create(
                    JobRequest(
                        id,
                        company,
                        owner.id,
                        JobKind.WORKFORCE_CLOSE,
                        UUID.randomUUID(),
                        mapOf("period" to "2026-09"),
                        actor.authenticatedAt,
                        0,
                        actor.correlationId,
                        clock.instant(),
                        1,
                    )
                )
            }
        assertTrue(result is Result.Success, result.toString())
        return Fixture(company, owner, operator, id)
    }

    private fun cancel(f: Fixture, member: Member = f.operator) =
        post(member.client, "${f.path}/cancel", """{"expectedVersion":0}""", member.csrf)

    private fun <T> whileWaiting(member: Member, change: () -> Unit, call: () -> T): T {
        val barrier = AccountLockProbe.Barrier(member.id)
        probe.current.set(barrier)
        return Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<T> { call() }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                change()
                barrier.release.countDown()
                pending.get(10, TimeUnit.SECONDS)
            } finally {
                barrier.release.countDown()
                probe.current.set(null)
            }
        }
    }

    private fun assertError(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode(), response.body())
        assertEquals(code, json.readTree(response.body())["code"].asString())
    }

    @Test
    fun readsAndCancellationDiscardPermissionsRevokedWhileWaiting() {
        for (operation in listOf("get", "list", "cancel")) {
            val f = fixture()
            val response =
                whileWaiting(
                    f.operator,
                    {
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=?",
                                f.company,
                                f.operator.id,
                            )
                    },
                ) {
                    when (operation) {
                        "get" -> get(f.operator.client, f.path)
                        "list" -> get(f.operator.client, "/api/v1/companies/${f.company}/jobs")
                        else -> cancel(f)
                    }
                }
            if (operation == "list") {
                assertEquals(200, response.statusCode(), response.body())
                assertEquals(0, json.readTree(response.body())["items"].size())
            } else assertError(response, 404, "job_not_found")
            assertEquals(
                false,
                database()
                    .queryForObject(
                        "select cancellation_requested from background_jobs where id=?",
                        Boolean::class.java,
                        f.job,
                    ),
            )
        }
    }

    @Test
    fun anInflightRequestCannotGainNewJobPermissions() {
        for (operation in listOf("get", "list", "cancel")) {
            val f = fixture(emptySet())
            val response =
                whileWaiting(
                    f.operator,
                    {
                        for (permission in setOf("jobs.read", "jobs.manage")) database()
                            .update(
                                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                                f.company,
                                f.operator.id,
                                permission,
                            )
                    },
                ) {
                    when (operation) {
                        "get" -> get(f.operator.client, f.path)
                        "list" -> get(f.operator.client, "/api/v1/companies/${f.company}/jobs")
                        else -> cancel(f)
                    }
                }
            if (operation == "list") {
                assertEquals(200, response.statusCode(), response.body())
                assertEquals(0, json.readTree(response.body())["items"].size())
            } else assertError(response, 404, "job_not_found")
            assertEquals(200, get(f.operator.client, f.path).statusCode())
        }
    }

    @Test
    fun ownerCleanupDoesNotRequireAFormerBusinessGrantButStillRequiresActiveMembership() {
        val f = fixture()
        assertEquals(200, get(f.owner.client, f.path).statusCode())
        val accepted = cancel(f, f.owner)
        assertEquals(200, accepted.statusCode(), accepted.body())
        assertEquals(accepted.body(), cancel(f, f.owner).body())
        val revoked =
            whileWaiting(
                f.owner,
                {
                    database()
                        .update(
                            "update company_memberships set active=false where company_id=? and account_id=?",
                            f.company,
                            f.owner.id,
                        )
                },
            ) {
                cancel(f, f.owner)
            }
        assertError(revoked, 403, "company_access_denied")
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='jobs.cancellation_requested'",
                    Int::class.java,
                    f.job,
                ),
        )
    }

    @Test
    fun ownerReadsRejectRevokedCredentialsAndDisabledCompaniesAfterWaiting() {
        for (operation in listOf("get", "list", "cancel")) {
            for (change in listOf("credentials", "company")) {
                val f = fixture()
                val response =
                    whileWaiting(
                        f.owner,
                        {
                            if (change == "credentials")
                                database()
                                    .update(
                                        "update accounts set security_version=security_version+1 where id=?",
                                        f.owner.id,
                                    )
                            else
                                database()
                                    .update(
                                        "update companies set active=false where id=?",
                                        f.company,
                                    )
                        },
                    ) {
                        when (operation) {
                            "get" -> get(f.owner.client, f.path)
                            "list" -> get(f.owner.client, "/api/v1/companies/${f.company}/jobs")
                            else -> cancel(f, f.owner)
                        }
                    }
                if (change == "credentials") assertError(response, 401, "session_revoked")
                else assertError(response, 403, "company_access_denied")
                assertEquals(
                    false,
                    database()
                        .queryForObject(
                            "select cancellation_requested from background_jobs where id=?",
                            Boolean::class.java,
                            f.job,
                        ),
                )
            }
        }
    }

    @Test
    fun jobActionHintsUseTheOriginalAndCurrentGrantIntersection() {
        for (operation in listOf("get", "list")) {
            for (originallyAllowed in listOf(true, false)) {
                val f =
                    fixture(
                        if (originallyAllowed) setOf("jobs.read", "jobs.manage")
                        else setOf("jobs.read")
                    )
                val response =
                    whileWaiting(
                        f.operator,
                        {
                            if (originallyAllowed)
                                database()
                                    .update(
                                        "delete from membership_permissions where company_id=? and account_id=? and permission='jobs.manage'",
                                        f.company,
                                        f.operator.id,
                                    )
                            else
                                database()
                                    .update(
                                        "insert into membership_permissions(company_id,account_id,permission) values(?,?,'jobs.manage')",
                                        f.company,
                                        f.operator.id,
                                    )
                        },
                    ) {
                        if (operation == "get") get(f.operator.client, f.path)
                        else get(f.operator.client, "/api/v1/companies/${f.company}/jobs")
                    }
                assertEquals(200, response.statusCode(), response.body())
                val body = json.readTree(response.body())
                val item = if (operation == "get") body else body["items"].get(0)
                assertEquals(0, item["availableActions"].size())
                val refreshed = json.readTree(get(f.operator.client, f.path).body())
                assertEquals(if (originallyAllowed) 0 else 1, refreshed["availableActions"].size())
                if (originallyAllowed) assertError(cancel(f), 404, "job_not_found")
            }
        }
    }

    @Test
    fun expiredAssuranceAfterWaitingCannotReadJobsOrRequestCancellation() {
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val get = GetJob(jobs, transactions, companies, members, identities, security, clock)
        val list = ListJobs(jobs, transactions, companies, members, identities, security, clock)
        val cancel =
            RequestJobCancellation(
                jobs,
                transactions,
                journal,
                companies,
                members,
                identities,
                security,
                clock,
            )
        for (operation in listOf("get", "list", "cancel")) {
            val f = fixture()
            val actor =
                Actor(
                    f.operator.id,
                    f.company,
                    setOf("jobs.read", "jobs.manage"),
                    clock.instant(),
                    UUID.randomUUID(),
                    mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1),
                    credentialVersion = 0,
                )
            database()
                .update(
                    "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                    f.operator.id,
                )
            try {
                val result =
                    whileWaiting(f.operator, { clock.set(clock.instant().plusSeconds(2)) }) {
                        when (operation) {
                            "get" -> get.execute(actor, f.job)
                            "list" -> list.execute(actor, 50, null, null)
                            else -> cancel.execute(actor, f.job, 0)
                        }
                    }
                assertEquals("mfa_required", (result as Result.Failed).failure.code)
                assertEquals(
                    false,
                    database()
                        .queryForObject(
                            "select cancellation_requested from background_jobs where id=?",
                            Boolean::class.java,
                            f.job,
                        ),
                )
                assertEquals(
                    0,
                    database()
                        .queryForObject(
                            "select count(*) from audit_entries where resource_id=? and action='jobs.cancellation_requested'",
                            Int::class.java,
                            f.job,
                        ),
                )
                val renewed = actor.copy(mfaVerifiedAt = clock.instant())
                val accepted = cancel.execute(renewed, f.job, 0) as Result.Success
                assertTrue(accepted.value.job.cancellationRequested)
                assertTrue(accepted.value.availableActions.isEmpty())
                assertEquals(accepted, cancel.execute(renewed, f.job, 0))
            } finally {
                database()
                    .update(
                        "update accounts set mfa_secret_encrypted=null where id=?",
                        f.operator.id,
                    )
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["get", "list", "cancel"])
    fun expiryAfterAcquisitionCannotReleaseJobsOrCommitCancellation(operation: String) {
        val f = fixture()
        val actor = assuredActor(f)
        val before = storedJob(f)
        val barrier = JobAssuranceProbe.Barrier(f.company, operation)
        assuranceProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { invokeAssured(f, actor, operation) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                clock.set(clock.instant().plusSeconds(2))
                barrier.release.countDown()
                assertEquals(
                    "mfa_required",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                barrier.release.countDown()
                assuranceProbe.current.set(null)
            }
        }
        assertEquals(before, storedJob(f))
        val renewed = actor.copy(mfaVerifiedAt = clock.instant())
        val accepted = invokeAssured(f, renewed, operation)
        assertTrue(accepted is Result.Success, accepted.toString())
        val committed = storedJob(f)
        assertEquals(
            "mfa_required",
            (invokeAssured(f, actor, operation) as Result.Failed).failure.code,
        )
        assertEquals(committed, storedJob(f))
        assertEquals(accepted, invokeAssured(f, renewed, operation))
        assertEquals(committed, storedJob(f))
    }

    @ParameterizedTest
    @ValueSource(strings = ["get", "list", "cancel"])
    fun cancellationDuringAcquisitionRollsBackAndReleasesTheThreadAndGuards(operation: String) {
        val f = fixture()
        val actor = assuredActor(f).copy(mfaVerifiedAt = clock.instant())
        val before = storedJob(f)
        val barrier = JobAssuranceProbe.Barrier(f.company, operation)
        val finished = CountDownLatch(1)
        assuranceProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<*>> {
                    try {
                        invokeAssured(f, actor, operation)
                    } finally {
                        finished.countDown()
                    }
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertTrue(finished.await(5, TimeUnit.SECONDS))
            } finally {
                barrier.release.countDown()
                assuranceProbe.current.set(null)
            }
            assertEquals(before, storedJob(f))
            val retry = pool.submit<Result<*>> { invokeAssured(f, actor, operation) }
            assertTrue(retry.get(10, TimeUnit.SECONDS) is Result.Success)
        }
    }

    private fun assuredActor(f: Fixture): Actor {
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.operator.id,
            )
        return Actor(
            f.operator.id,
            f.company,
            setOf("jobs.read", "jobs.manage"),
            clock.instant(),
            UUID.randomUUID(),
            mfaVerifiedAt =
                clock.instant().minus(IdentitySecurityPolicy().maximumMfaAge).plusSeconds(1),
            credentialVersion = 0,
        )
    }

    private fun invokeAssured(f: Fixture, actor: Actor, operation: String): Result<*> {
        val security = IdentitySecurityPolicy()
        return when (operation) {
            "get" ->
                GetJob(jobs, transactions, companies, members, identities, security, clock)
                    .execute(actor, f.job)
            "list" ->
                ListJobs(jobs, transactions, companies, members, identities, security, clock)
                    .execute(actor, 50, null, null)
            "cancel" ->
                RequestJobCancellation(
                        jobs,
                        transactions,
                        journal,
                        companies,
                        members,
                        identities,
                        security,
                        clock,
                    )
                    .execute(actor, f.job, 0)
            else -> error("Unknown job fixture operation")
        }
    }

    private fun storedJob(f: Fixture): Map<String, Any?> =
        mapOf(
            "job" to
                database()
                    .queryForMap(
                        "select cancellation_requested,version,available_at,status from background_jobs where id=?",
                        f.job,
                    ),
            "audit" to
                database()
                    .queryForObject(
                        "select count(*) from audit_entries where company_id=?",
                        Int::class.java,
                        f.company,
                    ),
            "outbox" to
                database()
                    .queryForObject(
                        "select count(*) from outbox_events where company_id=?",
                        Int::class.java,
                        f.company,
                    ),
        )
}
