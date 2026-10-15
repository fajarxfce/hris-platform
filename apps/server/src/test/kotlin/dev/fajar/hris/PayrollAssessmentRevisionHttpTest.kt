package dev.fajar.hris

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.PayrollTaxAssessment
import dev.fajar.hris.payroll.domain.repositories.PayrollAssessmentRepository
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class PayrollAssessmentRevisionHttpTest : PayrollAmendmentReviewApiFixture() {
    @Autowired private lateinit var assessments: PayrollAssessmentRepository

    @Test
    fun originalHistoryRequiresAnExactRevisionAndRemainsCompanyScoped() {
        val f = amendmentFixture()
        val actor = payrollActor(f.payroll, f.maker)
        val original =
            transactions.run(actor) { assessments.taxHistory(f.payroll.company, f.assessment, 0) }
        assertTrue(original is Result.Success, original.toString())
        assertEquals(0L, requireNotNull((original as Result.Success).value).revision)
        for (revision in listOf(-1L, 1L, Long.MAX_VALUE)) {
            assertEquals(
                Result.Success<PayrollTaxAssessment?>(null),
                transactions.run(actor) {
                    assessments.taxHistory(f.payroll.company, f.assessment, revision)
                },
            )
        }
        val other = amendmentFixture()
        assertEquals(
            Result.Success<PayrollTaxAssessment?>(null),
            transactions.run(payrollActor(other.payroll, other.maker)) {
                assessments.taxHistory(f.payroll.company, f.assessment, 0)
            },
        )
        for (table in listOf("payroll_assessment_heads", "payroll_assessment_revisions")) {
            assertEquals(
                Result.Success(0),
                transactions.run(payrollActor(other.payroll, other.maker)) {
                    safeDatabaseCall {
                        runtimeJdbc.queryForObject(
                            "select count(*) from $table where company_id=?",
                            Int::class.java,
                            f.payroll.company,
                        )
                    }
                },
            )
        }
    }

    @Test
    fun targetsCannotInventARevisionWhileRetainingTheCorrectAssessmentId() {
        val initial = calculationFixture()
        reviewTemplate(initial)
        publishTaxRun(initial, beginTaxRun(initial))
        val next = followingMonth(initial, YearMonth.of(2026, 10))
        runProbe.targetSnapshot = { it.single().previousAssessmentRevision = 1 }
        try {
            val response =
                createRun(next, runBody(next, changes = mapOf("incomeDueDate" to "2026-10-31")))
            assertEquals(409, response.statusCode(), response.body())
        } finally {
            runProbe.targetSnapshot = null
        }
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from payroll_runs where company_id=? and period_id=?",
                    Int::class.java,
                    next.people.payroll.company,
                    next.period,
                ),
        )
        val lease = beginTaxRun(next)
        drainRun(next, lease)
        val detail = runView(next, runId(lease))
        assertEquals(0, detail["run"]["failed"].asInt())
        assertEquals(
            0,
            detail["results"]["items"][0]["target"]["previousAssessmentRevision"].asInt(),
        )
    }

    @Test
    fun anApprovedCalculationCannotBePromotedWithoutItsPublicationWorkflow() {
        val scene = amendmentReviewScene()
        payrollBody(decideAmendmentReview(scene))
        val f = scene.draft.source
        val before =
            listOf(
                    "payroll_assessments",
                    "payroll_assessment_revisions",
                    "payroll_assessment_heads",
                    "mobile_sync_changes",
                )
                .associateWith { count(f.payroll.company, it) }
        val result =
            transactions.run(payrollActor(f.payroll, f.maker)) {
                safeDatabaseCall {
                    runtimeJdbc.update(
                        "insert into payroll_assessment_revisions(company_id,assessment_id,revision,amendment_calculation_id,published_at) values(?,?,1,?,now())",
                        f.payroll.company,
                        f.assessment,
                        scene.calculationId,
                    )
                    runtimeJdbc.update(
                        "update payroll_assessment_heads set revision=1 where company_id=? and assessment_id=?",
                        f.payroll.company,
                        f.assessment,
                    )
                }
            }
        assertTrue(result is Result.Failed, result.toString())
        assertEquals(FailureKind.CONFLICT, (result as Result.Failed).failure.kind)
        assertEquals(before, before.keys.associateWith { count(f.payroll.company, it) })
        assertEquals(
            "APPROVED",
            draftView(scene.draft)["amendment"]["summary"]["status"].asString(),
        )
    }

    @Test
    fun originalRevisionsAndHeadIdentityCannotBeRewrittenOrDeleted() {
        val f = amendmentFixture()
        val mutations =
            listOf(
                "update payroll_assessment_revisions set published_at=published_at+interval '1 second' where company_id=?" to
                    FailureKind.FORBIDDEN,
                "delete from payroll_assessment_revisions where company_id=?" to
                    FailureKind.FORBIDDEN,
                "delete from payroll_assessment_heads where company_id=?" to FailureKind.CONFLICT,
                "update payroll_assessment_heads set assessment_id=gen_random_uuid() where company_id=?" to
                    FailureKind.CONFLICT,
                "update payroll_assessment_heads set revision=1 where company_id=?" to
                    FailureKind.CONFLICT,
            )
        for ((statement, kind) in mutations) {
            val result =
                transactions.run(payrollActor(f.payroll, f.maker)) {
                    safeDatabaseCall { runtimeJdbc.update(statement, f.payroll.company) }
                }
            assertTrue(result is Result.Failed, result.toString())
            assertEquals(kind, (result as Result.Failed).failure.kind)
        }
        assertEquals(1, count(f.payroll.company, "payroll_assessment_revisions"))
        assertEquals(
            0L,
            database()
                .queryForObject(
                    "select revision from payroll_assessment_heads where company_id=?",
                    Long::class.java,
                    f.payroll.company,
                ),
        )
    }
}
