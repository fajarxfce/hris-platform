package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.leave.domain.usecases.*
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import tools.jackson.databind.JsonNode

@Import(LeaveBatchProbeConfiguration::class)
abstract class LeaveBatchApiFixture : LeaveAccountingApiFixture() {
    @Autowired protected lateinit var batchAccrual: AdvanceLeaveAccrualBatch
    @Autowired protected lateinit var batchClosing: AdvanceLeaveYearCloseBatch
    @Autowired protected lateinit var abortBatch: AbortLeaveBatch
    @Autowired protected lateinit var batchProbe: LeaveBatchProbe
    @Autowired protected lateinit var batchJobs: JobRepository
    private val companies = java.util.concurrent.ConcurrentHashMap.newKeySet<UUID>()

    @AfterEach
    fun releaseBatchFixtures() {
        batchProbe.clear()
        for (company in companies) database()
            .update(
                "update background_jobs set status='CANCELLED',finished_at=now(),lease_owner=null,lease_token=null,lease_until=null where company_id=? and kind in ('LEAVE_ACCRUAL','LEAVE_YEAR_CLOSE') and status in ('QUEUED','RUNNING')",
                company,
            )
    }

    protected fun batchBody(
        f: LeaveFixture,
        id: UUID = UUID.randomUUID(),
        kind: JobKind = JobKind.LEAVE_ACCRUAL,
        period: String = "2026-09",
        version: Long = 1,
        employees: Set<UUID>? = null,
    ): String =
        json.writeValueAsString(
            mapOf(
                "id" to id,
                "typeId" to f.type,
                (if (kind == JobKind.LEAVE_ACCRUAL) "month" else "year") to
                    (if (kind == JobKind.LEAVE_ACCRUAL) period else period.toInt()),
                "expectedPolicyVersion" to version,
                "employeeIds" to employees,
                "reason" to "Company leave entitlement processing",
            )
        )

    protected fun createBatch(
        f: LeaveFixture,
        body: String = batchBody(f),
        key: UUID = UUID.randomUUID(),
        kind: JobKind = JobKind.LEAVE_ACCRUAL,
        client: HttpClient = f.admin,
        csrf: String = f.adminCsrf,
    ): HttpResponse<String> {
        companies += f.company
        return command(
            client,
            "/api/v1/companies/${f.company}/leave/${if(kind==JobKind.LEAVE_ACCRUAL) "accrual-batches" else "year-close-batches"}",
            body,
            csrf,
            key,
        )
    }

    protected fun beginBatch(
        f: LeaveFixture,
        id: UUID = UUID.randomUUID(),
        kind: JobKind = JobKind.LEAVE_ACCRUAL,
        period: String = if (kind == JobKind.LEAVE_ACCRUAL) "2026-09" else "2026",
        employees: Set<UUID>? = null,
    ): JobLease {
        accountingBody(
            createBatch(f, batchBody(f, id, kind, period, employees = employees), kind = kind)
        )
        return claimDocuments(kind).single { it.job.request.values["batchId"] == id.toString() }
    }

    protected fun stepBatch(
        f: LeaveFixture,
        lease: JobLease,
        actor: Actor = accountingActor(f),
    ): Result<JobStep> =
        when (lease.job.request.kind) {
            JobKind.LEAVE_ACCRUAL -> batchAccrual.execute(actor, lease)
            JobKind.LEAVE_YEAR_CLOSE -> batchClosing.execute(actor, lease)
            else -> error("Unexpected test job")
        }

    protected fun drainBatch(f: LeaveFixture, lease: JobLease, actor: Actor = accountingActor(f)) {
        repeat(lease.job.request.totalItems + 1) {
            val result = stepBatch(f, lease, actor)
            assertTrue(result is Result.Success, result.toString())
            if ((result as Result.Success).value.finished) return
        }
        error("Leave batch exceeded its bounded step budget")
    }

    protected fun batchId(lease: JobLease): UUID =
        UUID.fromString(lease.job.request.values.getValue("batchId"))

    protected fun batchView(
        f: LeaveFixture,
        id: UUID,
        suffix: String = "",
        client: HttpClient = f.admin,
    ): JsonNode =
        accountingBody(get(client, "/api/v1/companies/${f.company}/leave/batches/$id$suffix"))

    protected fun resumeBatch(
        f: LeaveFixture,
        id: UUID,
        version: Long,
        key: UUID = UUID.randomUUID(),
        client: HttpClient = f.admin,
        csrf: String = f.adminCsrf,
    ): HttpResponse<String> =
        command(
            client,
            "/api/v1/companies/${f.company}/leave/batches/$id/resume",
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to version,
                    "reason" to "Resume interrupted entitlement processing",
                )
            ),
            csrf,
            key,
        )

    protected fun cancelBatch(f: LeaveFixture, lease: JobLease) {
        val job =
            accountingBody(
                get(f.admin, "/api/v1/companies/${f.company}/jobs/${lease.job.request.id}")
            )
        accountingBody(
            command(
                f.admin,
                "/api/v1/companies/${f.company}/jobs/${lease.job.request.id}/cancel",
                json.writeValueAsString(mapOf("expectedVersion" to job["version"].asLong())),
                f.adminCsrf,
                UUID.randomUUID(),
            )
        )
        assertEquals(
            Result.Success(Unit),
            abortBatch.execute(lease, Failure(FailureKind.CONFLICT, "job_cancellation_requested")),
        )
    }

    protected fun failure(result: Result<*>, code: String) {
        assertTrue(result is Result.Failed, result.toString())
        assertEquals(code, (result as Result.Failed).failure.code)
    }
}
