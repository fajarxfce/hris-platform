package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException

@Import(AccountLockProbeConfiguration::class)
class EmployeeImportHttpTest : EmployeeImportApiFixture() {
    @Autowired private lateinit var accessProbe: AccountLockProbe

    @Test
    fun previewKeepsEmployeesUntouchedAndPartialApplicationRequiresExplicitConfirmation() {
        val f = fixture()
        val id = UUID.randomUUID()
        val startKey = UUID.randomUUID()
        val content =
            "$header\nE01,Alya,ID,2026-01-01,PERMANENT\nE02,Invalid date,ID,invalid,PERMANENT\nE03,First duplicate,ID,2026-01-01,PERMANENT\nE03,Second duplicate,ID,2026-01-01,PERMANENT\nE05,No contract end,ID,2026-01-01,FIXED_TERM\ne06, Last employee ,id,2026-01-01,PERMANENT"
        val started = start(f, content, id, startKey)
        assertEquals(200, started.statusCode(), started.body())
        drain(f, lease(id, JobKind.EMPLOYEE_IMPORT_PREVIEW))
        val preview = details(f, id)
        assertEquals("REVIEW", preview.get("batch").get("status").asString())
        assertEquals(2, preview.get("counts").get("READY").asInt())
        assertEquals(4, preview.get("counts").get("INVALID").asInt())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from employments where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        val proposals = rows(f, id)
        assertEquals("invalid_date", proposals.get(1).get("issues").get("startDate").asString())
        assertEquals(
            "duplicate_employee_number_in_import",
            proposals.get(2).get("issues").get("employeeNumber").asString(),
        )
        assertEquals(
            "duplicate_employee_number_in_import",
            proposals.get(3).get("issues").get("employeeNumber").asString(),
        )
        assertEquals(
            "invalid_fixed_term_contract",
            proposals.get(4).get("issues").get("terms").asString(),
        )
        assertEquals("E06", proposals.get(5).get("employeeNumber").asString())
        assertEquals(409, confirm(f, id).statusCode())
        val applyKey = UUID.randomUUID()
        val confirmed = confirm(f, id, partial = true, key = applyKey)
        assertEquals(200, confirmed.statusCode(), confirmed.body())
        drain(f, lease(id, JobKind.EMPLOYEE_IMPORT_APPLY))
        val completed = details(f, id)
        assertEquals("COMPLETED", completed.get("batch").get("status").asString())
        assertEquals(2, completed.get("counts").get("APPLIED").asInt())
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from employments where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from person_profile_revisions where owner_company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(confirmed.body(), confirm(f, id, partial = true, key = applyKey).body())
        assertEquals(started.body(), start(f, content, id, startKey).body())
        assertEquals(409, start(f, csv("DIFFERENT"), id, startKey).statusCode())
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from background_jobs where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        val page = json.readTree(get(f.browser, "${f.path}/$id/rows?limit=2").body())
        assertEquals(2, page.get("items").size())
        assertEquals("2", page.get("nextCursor").asString())
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update employee_import_rows set employee_number='OTHER' where import_id=?",
                    id,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database().update("delete from employee_import_attempts where import_id=?", id)
        }
    }

    @Test
    fun applicationRevalidatesPreviewAndReportsNewConflictsWithoutOrphanPeople() {
        val f = fixture()
        val id = begin(f, csv("E01", "E02"))
        drain(f, lease(id, JobKind.EMPLOYEE_IMPORT_PREVIEW))
        val person =
            database()
                .queryForObject(
                    "select (proposed->>'personId')::uuid from employee_import_rows where company_id=? and import_id=? and row_number=2",
                    UUID::class.java,
                    f.company,
                    id,
                )!!
        createNumber(f, "E02")
        val ready = CountDownLatch(2)
        val go = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val requests =
                (1..2).map {
                    pool.submit<Int> {
                        ready.countDown()
                        check(go.await(5, TimeUnit.SECONDS))
                        confirm(f, id).statusCode()
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            go.countDown()
            assertEquals(listOf(200, 409), requests.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        drain(f, lease(id, JobKind.EMPLOYEE_IMPORT_APPLY))
        val result = rows(f, id)
        assertEquals("APPLIED", result.get(0).get("status").asString())
        assertEquals("REJECTED", result.get(1).get("status").asString())
        assertEquals(
            "employee_number_exists",
            result.get(1).get("issues").get("employeeNumber").asString(),
        )
        assertEquals(
            0,
            database()
                .queryForObject("select count(*) from persons where id=?", Int::class.java, person),
        )
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from employments where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals("COMPLETED", details(f, id).get("batch").get("status").asString())
    }

    @Test
    fun cancellationAndResumeRetainAppliedRowsAndRejectLateLeaseWork() {
        val f = fixture()
        val id = begin(f, csv("E01", "E02"))
        drain(f, lease(id, JobKind.EMPLOYEE_IMPORT_PREVIEW))
        assertEquals(200, confirm(f, id).statusCode())
        val old = lease(id, JobKind.EMPLOYEE_IMPORT_APPLY)
        assertEquals(Result.Success(JobStep(1, false)), advance(f, old))
        val firstEmployee = rows(f, id).get(0).get("createdEmploymentId").asString()
        assertEquals(200, changeImport(f, id, "cancel", 2).statusCode())
        assertEquals(
            Result.Failed(Failure(FailureKind.CONFLICT, "job_cancellation_requested")),
            advance(f, old),
        )
        assertEquals(
            Result.Success(Unit),
            abort.execute(old, Failure(FailureKind.CONFLICT, "job_cancellation_requested")),
        )
        assertEquals("STOPPED", details(f, id).get("batch").get("status").asString())
        assertEquals(200, changeImport(f, id, "resume", 4).statusCode())
        val next = lease(id, JobKind.EMPLOYEE_IMPORT_APPLY)
        assertEquals(2, next.job.request.totalItems)
        assertEquals(
            Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost")),
            advance(f, old),
        )
        assertEquals(
            Result.Success(Unit),
            abort.execute(old, Failure(FailureKind.UNEXPECTED, "late_failure")),
        )
        drain(f, next)
        assertEquals(firstEmployee, rows(f, id).get(0).get("createdEmploymentId").asString())
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from employments where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where company_id=? and action='people.employee_import_row_applied'",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(
            3,
            json.readTree(get(f.browser, "${f.path}/$id/attempts").body()).get("items").size(),
        )
    }

    @Test
    fun anExhaustedPreviewCanResumeFromItsPersistedRows() {
        val f = fixture()
        val id = begin(f, csv("E01", "E02"))
        val old = lease(id, JobKind.EMPLOYEE_IMPORT_PREVIEW)
        assertEquals(Result.Success(JobStep(1, false)), advance(f, old))
        database()
            .update(
                "update background_jobs set attempts=8,lease_until=clock_timestamp()-interval '1 second' where id=?",
                old.job.request.id,
            )
        assertTrue(claim(JobKind.EMPLOYEE_IMPORT_PREVIEW).isEmpty())
        assertEquals(
            "FAILED",
            database()
                .queryForObject(
                    "select status from background_jobs where id=?",
                    String::class.java,
                    old.job.request.id,
                ),
        )
        assertEquals("PREVIEWING", details(f, id).get("batch").get("status").asString())
        assertEquals(200, changeImport(f, id, "resume", 0).statusCode())
        val next = lease(id, JobKind.EMPLOYEE_IMPORT_PREVIEW)
        assertEquals(2, next.job.request.totalItems)
        drain(f, next)
        val result = details(f, id)
        assertEquals("REVIEW", result.get("batch").get("status").asString())
        assertEquals(2, result.get("counts").get("READY").asInt())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from employments where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun failedAuditRollsBackTheCreatedEmployeeRowOutcomeAndCheckpoint() {
        val f = fixture()
        val id = begin(f, csv("E01"))
        drain(f, lease(id, JobKind.EMPLOYEE_IMPORT_PREVIEW))
        assertEquals(200, confirm(f, id).statusCode())
        val old = lease(id, JobKind.EMPLOYEE_IMPORT_APPLY)
        database()
            .execute(
                """create function fail_import_audit() returns trigger language plpgsql as ${'$'}${'$'} begin
            if new.company_id='${f.company}'::uuid and new.action='people.employee_import_row_applied' then raise exception 'Fixture failure' using errcode='23514';end if;return new;end ${'$'}${'$'}"""
            )
        database()
            .execute(
                "create trigger import_probe before insert on audit_entries for each row execute function fail_import_audit()"
            )
        val failure: Result<JobStep>
        try {
            failure = advance(f, old)
            assertTrue(failure is Result.Failed, failure.toString())
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from employments where company_id=?",
                        Int::class.java,
                        f.company,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from persons where owner_company_id=?",
                        Int::class.java,
                        f.company,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from person_profile_revisions where owner_company_id=?",
                        Int::class.java,
                        f.company,
                    ),
            )
            assertEquals("READY", rows(f, id).get(0).get("status").asString())
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select completed_items from background_jobs where id=?",
                        Int::class.java,
                        old.job.request.id,
                    ),
            )
        } finally {
            database().execute("drop trigger import_probe on audit_entries")
            database().execute("drop function fail_import_audit()")
        }
        assertEquals(Result.Success(Unit), abort.execute(old, (failure as Result.Failed).failure))
        assertEquals(200, changeImport(f, id, "resume", 3).statusCode())
        drain(f, lease(id, JobKind.EMPLOYEE_IMPORT_APPLY))
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from employments where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun revocationDuringAPausedWorkerStepStopsMutationAndFreshAuthorizationCanResume() {
        var f = fixture()
        val id = begin(f, csv("E01"))
        drain(f, lease(id, JobKind.EMPLOYEE_IMPORT_PREVIEW))
        assertEquals(200, confirm(f, id).statusCode())
        val old = lease(id, JobKind.EMPLOYEE_IMPORT_APPLY)
        val barrier = AccountLockProbe.Barrier(f.actor.accountId)
        accessProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val result = pool.submit<Result<JobStep>> { advance(f, old) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "update accounts set security_version=security_version+1 where id=?",
                        f.actor.accountId,
                    )
            } finally {
                barrier.release.countDown()
                accessProbe.current.set(null)
            }
            assertEquals(
                Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "session_revoked")),
                result.get(10, TimeUnit.SECONDS),
            )
        }
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from employments where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(
            Result.Success(Unit),
            abort.execute(old, Failure(FailureKind.UNAUTHENTICATED, "session_revoked")),
        )
        assertEquals(401, get(f.browser, "${f.path}/$id").statusCode())
        f =
            f.copy(
                csrf = login(f.browser),
                actor =
                    f.actor.copy(credentialVersion = requireNotNull(f.actor.credentialVersion) + 1),
            )
        assertEquals(200, changeImport(f, id, "resume", 3).statusCode())
        drain(f, lease(id, JobKind.EMPLOYEE_IMPORT_APPLY))
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from employments where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun twoApplyJobsCannotCreateTheSameCompanyEmployeeNumber() {
        val f = fixture()
        val first = begin(f, csv("E01"))
        val second = begin(f, csv("E01"))
        val previews = claim(JobKind.EMPLOYEE_IMPORT_PREVIEW)
        assertEquals(2, previews.size)
        previews.forEach { drain(f, it) }
        assertEquals(200, confirm(f, first).statusCode())
        assertEquals(200, confirm(f, second).statusCode())
        val jobs = claim(JobKind.EMPLOYEE_IMPORT_APPLY)
        assertEquals(2, jobs.size)
        val ready = CountDownLatch(2)
        val go = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val outcomes =
                jobs.map { job ->
                    pool.submit<Result<JobStep>> {
                        ready.countDown()
                        check(go.await(5, TimeUnit.SECONDS))
                        advance(f, job)
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            go.countDown()
            outcomes.forEach {
                assertEquals(Result.Success(JobStep(1, false)), it.get(15, TimeUnit.SECONDS))
            }
        }
        jobs.forEach { drain(f, it) }
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from employments where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from persons where owner_company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        val statuses =
            listOf(
                    rows(f, first).get(0).get("status").asString(),
                    rows(f, second).get(0).get("status").asString(),
                )
                .sorted()
        assertEquals(listOf("APPLIED", "REJECTED"), statuses)
    }

    @Test
    fun malformedFilesAndUnauthorizedOrForeignReadsDoNotCreateOrRevealImports() {
        val f = fixture()
        val second = fixture()
        val invalidKey = UUID.randomUUID()
        assertEquals(
            422,
            start(f, "employee_number,employee_number\nE01,E02", key = invalidKey).statusCode(),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where operation_id=?",
                    Int::class.java,
                    invalidKey,
                ),
        )
        assertEquals(422, start(f, csv("E01"), fileName = "../staff.csv").statusCode())
        val large = start(f, "é".repeat(300000))
        assertEquals(422, large.statusCode())
        assertEquals(
            "employee_import_size_limit",
            json.readTree(large.body()).get("code").asString(),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from employee_imports where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        val id = begin(f, csv("E01"))
        assertEquals(404, get(second.browser, "${second.path}/$id/rows").statusCode())
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Limited user',password_hash from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                f.company,
                account,
            )
        for (permission in setOf("people.read", "people.manage", "people.import")) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                f.company,
                account,
                permission,
            )
        val browser = client()
        val csrf = login(browser, "$account@example.test")
        assertEquals(403, get(browser, "${f.path}/$id/rows").statusCode())
        assertEquals(403, get(browser, "${f.path}/template").statusCode())
        assertEquals(403, start(f.copy(browser = browser, csrf = csrf), csv("E02")).statusCode())
        val template = get(f.browser, "${f.path}/template")
        assertEquals(200, template.statusCode(), template.body())
        assertTrue(template.body().startsWith(header))
        assertTrue(
            template
                .headers()
                .firstValue("Content-Disposition")
                .orElse("")
                .contains("employee-import-template.csv")
        )
    }
}
