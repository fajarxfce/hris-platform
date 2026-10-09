package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveBatchHttpTest : LeaveBatchApiFixture() {
    @Test
    fun accrualBatchHasDurableResultsAndIdenticalPeriodsDoNotGrantTwice() {
        val f = accountingFixture()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val payload = batchBody(f, id)
        val first = createBatch(f, payload, key)
        accountingBody(first)
        assertEquals(first.body(), createBatch(f, payload, key).body())
        accountingCode(createBatch(f, batchBody(f)), 409, "leave_batch_active")
        val lease = claimDocuments(JobKind.LEAVE_ACCRUAL).single { batchId(it) == id }
        assertEquals(Result.Success(JobStep(1, false)), stepBatch(f, lease))
        val partial = batchView(f, id)
        assertEquals(1, partial["counts"]["applied"].asInt())
        assertEquals(1, partial["job"]["completedItems"].asInt())
        assertEquals("RUNNING", partial["batch"]["status"].asString())
        drainBatch(f, lease)
        val done = batchView(f, id, "?limit=1")
        assertEquals("COMPLETED", done["batch"]["status"].asString())
        assertEquals("SUCCEEDED", done["job"]["status"].asString())
        assertEquals(2, done["counts"]["applied"].asInt())
        assertEquals(3, done["job"]["completedItems"].asInt())
        assertEquals(1, done["results"]["items"].size())
        assertEquals("1", done["results"]["nextCursor"].asString())
        val next = batchView(f, id, "?after=1&limit=1")
        assertEquals(2, next["results"]["items"][0]["ordinal"].asInt())
        assertTrue(next["results"]["nextCursor"].isNull)
        assertEquals("1", balance(f)["availableDays"].asString())
        val second = beginBatch(f)
        drainBatch(f, second)
        val unchanged = batchView(f, batchId(second))
        assertEquals(2, unchanged["counts"]["unchanged"].asInt())
        assertEquals(2, accountingRows(f, "leave_accrual_postings"))
        assertEquals("1", balance(f)["availableDays"].asString())
        failure(stepBatch(f, lease), "job_lease_lost")
    }

    @Test
    fun yearClosingProcessesCarryExpiryAndEmptyAccountsWithoutSyntheticGrants() {
        val f = accountingFixture(Instant.parse("2027-01-05T03:00:00Z"))
        accountingBody(adjust(f, "5"))
        val lease = beginBatch(f, kind = JobKind.LEAVE_YEAR_CLOSE)
        drainBatch(f, lease)
        val done = batchView(f, batchId(lease))
        assertEquals(2, done["counts"]["applied"].asInt())
        assertEquals(2, accountingRows(f, "leave_year_closings"))
        val view = entitlementView(f)
        assertTrue(view["balance"]["closed"].asBoolean())
        assertEquals("2", view["closing"]["carriedDays"].asString())
        assertEquals("3", view["closing"]["expiredDays"].asString())
        assertEquals("2", entitlementView(f, 2027)["balance"]["availableDays"].asString())
        val replay = beginBatch(f, kind = JobKind.LEAVE_YEAR_CLOSE)
        drainBatch(f, replay)
        assertEquals(2, batchView(f, batchId(replay))["counts"]["unchanged"].asInt())
        assertEquals(4, accountingRows(f, "leave_ledger"))
    }

    @Test
    fun employeeEligibilityAndIndependentAuthorshipProduceExplicitOutcomes() {
        val f = accountingFixture()
        // The employee is also an entitlement operator; own awards remain prohibited.
        for (permission in listOf("leave.accrual.post", "leave.read")) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                f.company,
                f.account,
                permission,
            )
        val id = UUID.randomUUID()
        accountingBody(createBatch(f, batchBody(f, id), client = f.worker, csrf = f.workerCsrf))
        val lease = claimDocuments(JobKind.LEAVE_ACCRUAL).single { batchId(it) == id }
        val actor =
            accountingActor(f)
                .copy(
                    accountId = f.account,
                    permissions = setOf("leave.accrual.post", "leave.read"),
                )
        drainBatch(f, lease, actor)
        val done = batchView(f, id)
        assertEquals(1, done["counts"]["skipped"].asInt())
        assertEquals(1, done["counts"]["applied"].asInt())
        val skipped =
            done["results"]["items"].iterator().asSequence().single {
                it["status"].asString() == "SKIPPED"
            }
        assertEquals("self_adjustment_denied", skipped["failureCode"].asString())
        assertEquals(f.employee.toString(), skipped["employeeId"].asString())
        assertEquals("0", balance(f)["availableDays"].asString())
        accountingBody(accountingPolicy(f, version = 1, minService = 120))
        val deniedId = UUID.randomUUID()
        accountingBody(createBatch(f, batchBody(f, deniedId, period = "2026-08", version = 2)))
        val denied = claimDocuments(JobKind.LEAVE_ACCRUAL).single { batchId(it) == deniedId }
        drainBatch(f, denied)
        assertEquals(2, batchView(f, deniedId)["counts"]["skipped"].asInt())
    }

    @Test
    fun frequencyConflictsAndClosedYearsAreRecordedWithoutFailingOtherTargets() {
        val f = accountingFixture(Instant.parse("2027-01-05T03:00:00Z"))
        accountingBody(accrue(f, accrualBody(month = "2026-09")))
        accountingBody(accountingPolicy(f, version = 1, frequency = "ANNUAL", days = "12"))
        val id = UUID.randomUUID()
        accountingBody(createBatch(f, batchBody(f, id, version = 2)))
        val lease = claimDocuments(JobKind.LEAVE_ACCRUAL).single { batchId(it) == id }
        drainBatch(f, lease)
        val result = batchView(f, id)
        assertEquals("SUCCEEDED", result["job"]["status"].asString())
        assertEquals(1, result["counts"]["failed"].asInt())
        assertEquals(1, result["counts"]["applied"].asInt())
        val failed =
            result["results"]["items"].iterator().asSequence().single {
                it["status"].asString() == "FAILED"
            }
        assertEquals("leave_accrual_frequency_changed", failed["failureCode"].asString())
        assertEquals("2026", failed["parameters"]["year"].asString())
        assertEquals("1", balance(f)["availableDays"].asString())
        val other = accountingFixture(Instant.parse("2027-01-05T03:00:00Z"))
        accountingBody(closeYear(other, closingBody(sourceVersion = 0)))
        val closed = beginBatch(other)
        drainBatch(other, closed)
        assertEquals(1, batchView(other, batchId(closed))["counts"]["failed"].asInt())
        assertEquals(1, batchView(other, batchId(closed))["counts"]["applied"].asInt())
    }

    @Test
    fun batchCommandsAndReadsAreBoundedAndCompanyScoped() {
        val f = accountingFixture()
        val other = accountingFixture()
        accountingCode(
            createBatch(f, batchBody(f, employees = emptySet())),
            422,
            "invalid_leave_batch",
        )
        accountingCode(
            createBatch(f, batchBody(f, employees = setOf(other.employee))),
            422,
            "leave_batch_employee_unavailable",
        )
        accountingCode(createBatch(f, batchBody(f, version = 0)), 409, "stale_policy_version")
        accountingCode(
            createBatch(f, batchBody(f, period = "2026-10")),
            422,
            "leave_accrual_not_due",
        )
        accountingCode(
            createBatch(
                f,
                batchBody(f, kind = JobKind.LEAVE_YEAR_CLOSE, period = "2026"),
                kind = JobKind.LEAVE_YEAR_CLOSE,
            ),
            422,
            "leave_year_not_ended",
        )
        accountingCode(
            createBatch(f, batchBody(f), client = f.worker, csrf = f.workerCsrf),
            403,
            "access_denied",
        )
        val lease = beginBatch(f, employees = setOf(f.employee))
        val id = batchId(lease)
        assertEquals(1, batchView(f, id)["batch"]["totalEmployees"].asInt())
        accountingCode(
            get(f.admin, "/api/v1/companies/${f.company}/leave/batches/$id?limit=201"),
            422,
            "invalid_page",
        )
        accountingCode(
            get(f.admin, "/api/v1/companies/${other.company}/leave/batches/$id"),
            404,
            "leave_batch_not_found",
        )
        accountingCode(
            get(f.worker, "/api/v1/companies/${f.company}/leave/batches/$id"),
            403,
            "access_denied",
        )
        drainBatch(f, lease)
        val listing =
            accountingBody(get(f.admin, "/api/v1/companies/${f.company}/leave/batches?limit=1"))
        assertEquals(id.toString(), listing["items"][0]["id"].asString())
    }

    @Test
    fun competingStartsShareThePeriodGuardAndCommitOnlyOneJob() {
        val f = accountingFixture()
        val barrier = CountDownLatch(1)
        val ids = listOf(UUID.randomUUID(), UUID.randomUUID())
        Executors.newFixedThreadPool(2).use { pool ->
            val pending =
                ids.map { id ->
                    pool.submit<HttpResponse<String>> {
                        barrier.await(5, TimeUnit.SECONDS)
                        createBatch(f, batchBody(f, id))
                    }
                }
            barrier.countDown()
            val outcomes = pending.map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(listOf(200, 409), outcomes.map { it.statusCode() }.sorted())
            assertEquals(
                "leave_batch_active",
                json.readTree(outcomes.single { it.statusCode() == 409 }.body())["code"].asString(),
            )
        }
        assertEquals(1, accountingRows(f, "leave_batches"))
        assertEquals(1, accountingRows(f, "background_jobs"))
        assertEquals(2, accountingRows(f, "leave_batch_targets"))
    }
}
