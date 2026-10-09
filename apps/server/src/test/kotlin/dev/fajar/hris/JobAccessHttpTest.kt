package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
class JobAccessHttpTest : PeopleApiFixture() {
    @Autowired private lateinit var jobs: JobRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var probe: AccountLockProbe

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

    private fun whileWaiting(
        member: Member,
        change: () -> Unit,
        call: () -> HttpResponse<String>,
    ): HttpResponse<String> {
        val barrier = AccountLockProbe.Barrier(member.id)
        probe.current.set(barrier)
        return Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<String>> { call() }
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
}
