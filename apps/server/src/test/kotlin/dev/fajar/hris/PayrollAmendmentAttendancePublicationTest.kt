package dev.fajar.hris

import java.util.UUID
import org.jooq.JSONB
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.node.ObjectNode

class PayrollAmendmentAttendancePublicationTest : PayrollAmendmentAttendanceApiFixture() {
    @Test
    fun reviewedAndPublishedEvidenceSurvivesLaterCorrectionsWithoutReplacingTheOriginal() {
        val f = amendmentFixture()
        val original = assessmentFacts(f, 0)
        val closing =
            database()
                .queryForObject(
                    "select payload::text from work_period_snapshots where company_id=? and job_id=? and employment_id=?",
                    String::class.java,
                    f.payroll.company,
                    f.publication.calculation.work.job,
                    f.payroll.employee,
                )
        val first = adjustedWork(f)
        val draft = attendanceDraft(f, first)
        val review = amendmentReviewScene(draft, submitted = false)
        adjustedWork(f, absence = true)
        payrollBody(submitAmendmentReview(review))
        payrollBody(decideAmendmentReview(review))
        val scene =
            AmendmentPublicationScene(
                review,
                payrollMember(f.payroll.company, setOf("company.read", "payroll.amend.publish")),
            )
        val lease = publicationLease(scene)
        val latest = adjustedWork(f)
        assertEquals(
            dev.fajar.hris.core.domain.Result.Success(
                dev.fajar.hris.jobs.domain.entities.JobStep(1, true)
            ),
            stepPublication(scene, lease),
        )
        val published = assessmentFacts(f, 1)
        assertEquals(first.toString(), published["workAdjustmentId"].asString())
        assertEquals("WORKED", published["workDays"][0]["attendance"].asString())
        assertEquals(original, assessmentFacts(f, 0))
        assertEquals(
            closing,
            database()
                .queryForObject(
                    "select payload::text from work_period_snapshots where company_id=? and job_id=? and employment_id=?",
                    String::class.java,
                    f.payroll.company,
                    f.publication.calculation.work.job,
                    f.payroll.employee,
                ),
        )
        val preparation = amendmentPreparation(f)
        assertEquals(first.toString(), preparation["assessments"][0]["workAdjustmentId"].asString())
        assertEquals(
            latest.toString(),
            preparation["assessments"][0]["availableWorkAdjustment"]["id"].asString(),
        )
        val salaryOnly = payrollBody(amendmentPreview(f, amendmentPreviewBody(preparation)))
        assertEquals(first.toString(), salaryOnly["calculations"][0]["workAdjustmentId"].asString())
        val changed =
            payrollBody(
                amendmentPreview(
                    f,
                    amendmentPreviewBody(
                        preparation,
                        listOf(attendanceProposal(preparation, latest)),
                    ),
                )
            )
        assertEquals(latest.toString(), changed["calculations"][0]["workAdjustmentId"].asString())
    }

    @Test
    fun databaseRejectsForgedWorkReferencesAndDaysWithoutRetainingAPartialCalculation() {
        val f = amendmentFixture()
        val first = adjustedWork(f)
        val draft = attendanceDraft(f, first)
        val before = draftCounts(f)
        val key = UUID.randomUUID()
        for (field in listOf("reference", "days")) {
            draftProbe.transformResults = { rows ->
                rows.onEach { row ->
                    val facts = json.readTree(row.facts.data()) as ObjectNode
                    if (field == "reference")
                        facts.put("workAdjustmentId", UUID.randomUUID().toString())
                    else facts.putArray("workDays")
                    row.facts = JSONB.valueOf(json.writeValueAsString(facts))
                }
            }
            assertEquals(409, draftCommand(draft, "calculate", 0, key).statusCode())
            draftProbe.clear()
            assertEquals(before, draftCounts(f))
        }
        payrollBody(draftCommand(draft, "calculate", 0, key))
        assertEquals("CALCULATED", draftView(draft)["amendment"]["summary"]["status"].asString())
    }
}
