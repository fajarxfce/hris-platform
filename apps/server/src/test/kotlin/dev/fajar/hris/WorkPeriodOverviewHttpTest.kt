package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.jobs.domain.entities.JobStep
import dev.fajar.hris.workforce.domain.usecases.AdvanceWorkPeriodClose
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

@Import(WorkPeriodProbeConfiguration::class)
class WorkPeriodOverviewHttpTest : WorkforceAccessApiFixture() {
    @Autowired private lateinit var advance: AdvanceWorkPeriodClose
    @Autowired private lateinit var probe: WorkPeriodProbe

    @AfterEach
    fun clearProbe() {
        probe.current.getAndSet(null)?.release?.countDown()
    }

    private fun body(response: HttpResponse<String>) =
        json.readTree(response.body()).also {
            assertEquals(200, response.statusCode(), response.body())
        }

    private fun reader(f: Fixture, permissions: Set<String>): HttpClient {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Period reader',password_hash from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                f.company,
                id,
            )
        permissions.forEach { permission ->
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    f.company,
                    id,
                    permission,
                )
        }
        return client().also { login(it, "$id@example.test") }
    }

    @Test
    fun reviewingAnUnstartedMonthIsReadOnlyAndUsesTheCompanyDate() {
        val f = isolatedFixture()
        val before =
            database()
                .queryForObject(
                    "select count(*) from work_periods where company_id=?",
                    Int::class.java,
                    f.company,
                )
        val overview = body(get(f.admin, "${f.path}/periods/2026-08"))
        assertEquals("2026-10-01", overview["companyDate"].asString())
        assertEquals("Asia/Jakarta", overview["companyTimezone"].asString())
        assertTrue(overview["period"].isNull)
        assertTrue(overview["attempt"].isNull)
        assertEquals(
            listOf("close"),
            overview["availableActions"].iterator().asSequence().map { it.asString() }.toList(),
        )
        assertEquals(
            before,
            database()
                .queryForObject(
                    "select count(*) from work_periods where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        database()
            .update("update companies set timezone='America/Los_Angeles' where id=?", f.company)
        val current = body(get(f.admin, "${f.path}/periods/2026-09"))
        assertEquals("2026-09-30", current["companyDate"].asString())
        assertTrue(current["availableActions"].isEmpty)
        assertEquals(422, get(f.admin, "${f.path}/periods/1999-12").statusCode())
        assertEquals(422, get(f.admin, "${f.path}/periods/2101-01").statusCode())
    }

    @Test
    fun progressAndPublishedReferencesAreScopedBoundedAndKeepPrivateProfilesOut() {
        val f = isolatedFixture()
        val second = anotherEmployee(f)
        val lease = begin(f)
        val initial = body(get(f.admin, "${f.path}/periods/2026-09"))
        assertEquals("PROCESSING", initial["period"]["status"].asString())
        assertEquals(2, initial["attempt"]["totalEmployees"].asInt())
        assertEquals(0, initial["attempt"]["completedEmployees"].asInt())
        assertEquals(
            setOf(
                "id",
                "status",
                "totalEmployees",
                "completedEmployees",
                "cancellationRequested",
                "failureCode",
                "createdAt",
                "finishedAt",
            ),
            initial["attempt"].properties().map { it.key }.toSet(),
        )
        assertEquals(Result.Success(JobStep(1, false)), advance.execute(f.actor, lease))
        assertEquals(404, get(f.admin, "${f.path}/periods/2026-09/employees").statusCode())
        assertEquals(
            1,
            body(get(f.admin, "${f.path}/periods/2026-09"))["attempt"]["completedEmployees"].asInt(),
        )
        assertEquals(Result.Success(JobStep(2, false)), advance.execute(f.actor, lease))
        assertEquals(Result.Success(JobStep(3, true)), advance.execute(f.actor, lease))
        val closed = body(get(f.admin, "${f.path}/periods/2026-09"))
        assertEquals("CLOSED", closed["period"]["status"].asString())
        assertEquals("SUCCEEDED", closed["attempt"]["status"].asString())
        assertEquals(2, closed["attempt"]["completedEmployees"].asInt())
        val published = body(get(f.admin, "${f.path}/periods/2026-09/employees/${f.employee}"))
        assertEquals(f.employee.toString(), published["employee"]["id"].asString())
        assertEquals("Example Employee", published["employee"]["legalName"].asString())
        assertEquals(closed["period"], published["period"])
        assertEquals(
            setOf("id", "employeeNumber", "legalName", "status", "version"),
            published["employee"].properties().map { it.key }.toSet(),
        )
        assertEquals(
            listOf("view_job", "view_snapshots"),
            closed["availableActions"].iterator().asSequence().map { it.asString() }.toList(),
        )
        anotherEmployee(f) // Later hires have no published snapshot in this attempt.
        val page = body(get(f.admin, "${f.path}/periods/2026-09/employees?limit=1"))
        assertEquals(
            setOf("id", "employeeNumber", "legalName", "status", "version"),
            page["items"][0].properties().map { it.key }.toSet(),
        )
        val next =
            body(
                get(
                    f.admin,
                    "${f.path}/periods/2026-09/employees?limit=1&after=${page["nextCursor"].asString()}",
                )
            )
        assertTrue(next["nextCursor"].isNull)
        assertEquals(
            setOf(f.employee.toString(), second.toString()),
            setOf(page["items"][0]["id"].asString(), next["items"][0]["id"].asString()),
        )
        assertEquals(
            1,
            body(get(f.admin, "${f.path}/periods/2026-09/employees?query=Another"))["items"].size(),
        )
        for (query in
            listOf(
                "limit=0",
                "limit=51",
                "query=${"x".repeat(121)}",
                "after=",
                "after=${"x".repeat(33)}",
            )) assertEquals(
            422,
            get(f.admin, "${f.path}/periods/2026-09/employees?$query").statusCode(),
        )
    }

    @Test
    fun closingReadAndJobAuthorityRemainSeparateAndForeignCompaniesAreRejected() {
        val f = isolatedFixture()
        val foreign = isolatedFixture()
        val closer = reader(f, setOf("workforce.close"))
        val viewer = reader(f, setOf("workforce.read"))
        assertEquals(
            listOf("close"),
            body(get(closer, "${f.path}/periods/2026-09"))["availableActions"]
                .iterator()
                .asSequence()
                .map { it.asString() }
                .toList(),
        )
        assertTrue(body(get(viewer, "${f.path}/periods/2026-09"))["availableActions"].isEmpty)
        val lease = begin(f)
        assertTrue(advance.execute(f.actor, lease) is Result.Success)
        assertTrue(advance.execute(f.actor, lease) is Result.Success)
        assertTrue(body(get(closer, "${f.path}/periods/2026-09"))["availableActions"].isEmpty)
        assertEquals(
            listOf("view_snapshots"),
            body(get(viewer, "${f.path}/periods/2026-09"))["availableActions"]
                .iterator()
                .asSequence()
                .map { it.asString() }
                .toList(),
        )
        assertEquals(403, get(closer, "${f.path}/periods/2026-09/employees").statusCode())
        assertEquals(403, get(f.employeeClient, "${f.path}/periods/2026-09").statusCode())
        assertEquals(
            200,
            get(f.employeeClient, "${f.path}/periods/2026-09/employees/${f.employee}").statusCode(),
        )
        assertEquals(
            404,
            get(viewer, "${f.path}/periods/2026-09/employees/${foreign.employee}").statusCode(),
        )
        assertEquals(403, get(viewer, "${foreign.path}/periods/2026-09").statusCode())
        assertEquals(403, get(viewer, "${foreign.path}/periods/2026-09/employees").statusCode())
        assertEquals(
            404,
            get(closer, "/api/v1/companies/${f.company}/jobs/${lease.job.request.id}").statusCode(),
        )
        assertEquals(
            403,
            get(viewer, "/api/v1/companies/${f.company}/employees?asOf=2026-09-30").statusCode(),
        )
        assertEquals(1, body(get(viewer, "${f.path}/periods/2026-09/employees"))["items"].size())
    }

    @Test
    fun aStoppedAttemptCanBeReviewedRecoveredAndReplacedWithoutLosingItsIdentity() {
        val f = isolatedFixture()
        val lease = begin(f)
        database()
            .update(
                "update background_jobs set attempts=8,lease_until=now()-interval '1 second' where id=?",
                lease.job.request.id,
            )
        assertTrue(claim().isEmpty())
        val failed = body(get(f.admin, "${f.path}/periods/2026-09"))
        assertEquals("PROCESSING", failed["period"]["status"].asString())
        assertEquals("FAILED", failed["attempt"]["status"].asString())
        assertEquals(
            listOf("recover", "view_job"),
            failed["availableActions"].iterator().asSequence().map { it.asString() }.toList(),
        )
        body(
            command(
                f.admin,
                "${f.path}/periods/2026-09/recover",
                """{"expectedVersion":1,"reason":"Review stopped attempt"}""",
                f.csrf,
                UUID.randomUUID(),
            )
        )
        val recovered = body(get(f.admin, "${f.path}/periods/2026-09"))
        assertEquals("REVIEW_REQUIRED", recovered["period"]["status"].asString())
        assertEquals(lease.job.request.id.toString(), recovered["attempt"]["id"].asString())
        assertEquals(
            listOf("close", "view_job"),
            recovered["availableActions"].iterator().asSequence().map { it.asString() }.toList(),
        )
        val replacement = body(close(f, version = 2))["id"].asString()
        assertNotEquals(lease.job.request.id.toString(), replacement)
        assertEquals(
            replacement,
            body(get(f.admin, "${f.path}/periods/2026-09"))["attempt"]["id"].asString(),
        )
    }

    @Test
    fun revokedReadAuthorityCannotSurviveAnAccessGuardWait() {
        for (directory in listOf(false, true)) {
            val f = isolatedFixture()
            val lease = begin(f)
            assertTrue(advance.execute(f.actor, lease) is Result.Success)
            assertTrue(advance.execute(f.actor, lease) is Result.Success)
            val path = "${f.path}/periods/2026-09${if (directory) "/employees" else ""}"
            val denied =
                waiting(
                    f.actor.accountId,
                    {
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission in ('workforce.read','workforce.close')",
                                f.company,
                                f.actor.accountId,
                            )
                    },
                ) {
                    get(f.admin, path)
                }
            assertEquals(403, denied.statusCode(), denied.body())
        }
    }

    @Test
    fun overviewReadersShareAccessGuardsWithoutBlockingTheClosingWorker() {
        val f = isolatedFixture()
        val lease = begin(f)
        val barrier = WorkPeriodProbe.Barrier(f.company, "overview")
        probe.current.set(barrier)
        Executors.newFixedThreadPool(2).use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> { get(f.admin, "${f.path}/periods/2026-09") }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val progress =
                    pool
                        .submit<Result<JobStep>> { advance.execute(f.actor, lease) }
                        .get(4, TimeUnit.SECONDS)
                assertEquals(Result.Success(JobStep(1, false)), progress)
                val parallel = body(get(f.admin, "${f.path}/periods/2026-09"))
                assertEquals(1, parallel["attempt"]["completedEmployees"].asInt())
                barrier.release.countDown()
                val original = body(pending.get(10, TimeUnit.SECONDS))
                assertEquals("PROCESSING", original["period"]["status"].asString())
                assertEquals("RUNNING", original["attempt"]["status"].asString())
                assertEquals(0, original["attempt"]["completedEmployees"].asInt())
            } finally {
                barrier.release.countDown()
                probe.current.set(null)
            }
        }
    }
}
