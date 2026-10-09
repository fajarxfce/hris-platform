package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException

class PayrollFinalizationHttpTest : PayrollFinalizationApiFixture() {
    @Test
    fun publicationRetainsApprovedResultsCalculationProgressAndOriginalReceipt() {
        val f = approved()
        val c = f.calculation
        val prior = runView(c, f.run)
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val body = finalizationBody(f, id)
        val receipt = payrollBody(startFinalization(f, body, key))
        assertEquals(receipt, payrollBody(startFinalization(f, body, key)))
        val lease = claimPayrollJobs(JobKind.PAYROLL_FINALIZE).single()
        assertTrue(finalizationView(f, id)["finalization"]["publishedAt"].isNull)
        assertEquals(0, count(c.people.payroll.company, "payroll_assessments"))
        payrollError(startFinalization(f), 409, "payroll_finalization_active")
        payrollError(withdrawReview(c, f.review, 1, 1), 409, "payroll_finalization_active")
        assertEquals(Result.Success(JobStep(1, true)), stepFinalization(f, lease))
        val after = runView(c, f.run)
        assertEquals("FINALIZED", after["run"]["status"].asString())
        assertEquals(2, after["run"]["version"].asLong())
        assertEquals(id.toString(), after["run"]["finalizationId"].asString())
        assertEquals(prior["job"], after["job"])
        assertEquals(prior["results"], after["results"])
        val publication = finalizationView(f, id)
        assertFalse(publication["finalization"]["publishedAt"].isNull)
        val issuerName = publication["finalization"]["companyName"].asString()
        database()
            .update(
                "update companies set name='Renamed company fixture',version=version+1 where id=?",
                c.people.payroll.company,
            )
        assertEquals(issuerName, finalizationView(f, id)["finalization"]["companyName"].asString())
        assertEquals("SUCCEEDED", publication["job"]["status"].asString())
        assertEquals(1, publication["job"]["completedItems"].asInt())
        assertEquals(1, count(c.people.payroll.company, "payroll_assessments"))
        assertEquals("APPROVED", reviewView(c, f.review)["review"]["status"].asString())
        assertEquals(
            "FINALIZED",
            database()
                .queryForObject(
                    "select status from payroll_periods where company_id=? and id=?",
                    String::class.java,
                    c.people.payroll.company,
                    c.period,
                ),
        )
        assertEquals(receipt, payrollBody(startFinalization(f, body, key)))
        payrollError(startFinalization(f), 409, "payroll_already_finalized")
        payrollError(withdrawReview(c, f.review, 1, 1), 409, "payroll_review_not_current")
        val late = stepFinalization(f, lease)
        assertEquals(Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost")), late)
    }

    @Test
    fun finalizationRequiresIndependentApprovalAndExactObservedVersions() {
        val c = calculationFixture()
        val run = calculated(c)
        val review = pendingReview(c, run)
        val finalizer =
            payrollMember(c.people.payroll.company, setOf("company.read", "payroll.finalize"))
        val f = PublicationFixture(c, run, review, finalizer)
        payrollError(startFinalization(f), 409, "payroll_approved_review_required")
        payrollBody(decideReview(c, review))
        payrollError(
            startFinalization(f, finalizationBody(f, changes = mapOf("expectedRunVersion" to 0))),
            409,
            "stale_version",
        )
        payrollError(
            startFinalization(
                f,
                finalizationBody(f, changes = mapOf("expectedApprovalVersion" to 2)),
            ),
            409,
            "approval_changed",
        )
        payrollError(startFinalization(f, member = c.people.preparer), 403, "access_denied")
        payrollError(
            startFinalization(f, finalizationBody(f, changes = mapOf("reason" to ""))),
            422,
            "invalid_payroll_finalization",
        )
        assertEquals(0, count(c.people.payroll.company, "payroll_finalizations"))
        payrollBody(startFinalization(f))
    }

    @Test
    fun cancelledFinalizationPublishesNothingAndExplicitRetryIsBounded() {
        val f = approved()
        repeat(8) { n ->
            val lease = beginFinalization(f)
            val id = finalizationId(lease)
            assertEquals(n + 1, finalizationView(f, id)["finalization"]["number"].asInt())
            cancelFinalization(f, lease)
            assertEquals(
                Result.Failed(Failure(FailureKind.CONFLICT, "job_cancellation_requested")),
                stepFinalization(f, lease),
            )
            assertEquals(
                Result.Success(Unit),
                abortFinalization.execute(
                    lease,
                    Failure(FailureKind.CONFLICT, "job_cancellation_requested"),
                ),
            )
            assertEquals("CANCELLED", finalizationView(f, id)["job"]["status"].asString())
        }
        assertEquals(0, count(f.calculation.people.payroll.company, "payroll_assessments"))
        payrollError(startFinalization(f), 409, "payroll_finalization_capacity")
        val first =
            payrollBody(
                get(f.finalizer.client, runPath(f.calculation, f.run) + "/finalizations?limit=3")
            )
        assertEquals(3, first["items"].size())
        assertEquals("3", first["nextCursor"].asString())
        val last =
            payrollBody(
                get(
                    f.finalizer.client,
                    runPath(f.calculation, f.run) + "/finalizations?after=6&limit=3",
                )
            )
        assertEquals(2, last["items"].size())
        assertTrue(last["nextCursor"].isNull)
        payrollError(
            get(f.finalizer.client, runPath(f.calculation, f.run) + "/finalizations?limit=201"),
            422,
            "invalid_page",
        )
        payrollBody(withdrawReview(f.calculation, f.review, 1, 1))
    }

    @Test
    fun publicationAndReviewRemainImmutableAndCompanyScoped() {
        val f = approved()
        val lease = beginFinalization(f)
        val id = finalizationId(lease)
        assertEquals(Result.Success(JobStep(1, true)), stepFinalization(f, lease))
        val company = f.calculation.people.payroll.company
        for (statement in
            listOf(
                "update payroll_finalizations set published_at=published_at+interval '1 second' where company_id=?",
                "delete from payroll_finalizations where company_id=?",
                "update payroll_assessments set person_id=gen_random_uuid() where company_id=?",
                "delete from payroll_assessments where company_id=?",
                "update payroll_runs set status='ABANDONED',finalization_id=null,version=version+1 where company_id=?",
            )) assertThrows(DataAccessException::class.java) {
            database().update(statement, company)
        }
        val other = approved()
        val hidden =
            transactions.run(payrollActor(other.calculation.people.payroll, other.finalizer)) {
                Result.Success(
                    runtimeJdbc.queryForObject(
                        "select count(*) from payroll_assessments where company_id=?",
                        Int::class.java,
                        company,
                    )
                )
            }
        assertEquals(Result.Success(0), hidden)
        payrollError(
            get(other.finalizer.client, finalizationsPath(other) + "/$id"),
            404,
            "payroll_finalization_not_found",
        )
        payrollError(
            get(f.calculation.people.payroll.admin, finalizationsPath(f) + "/$id"),
            403,
            "access_denied",
        )
    }
}
