package dev.fajar.hris

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollAmendmentAttendanceHttpTest : PayrollAmendmentAttendanceApiFixture() {
    @Test
    fun preparationSeparatesAvailableWorkFromThePublishedSourceAndPreviewPinsOnlyTheSelectedRevision() {
        val f = amendmentFixture()
        val original = assessmentFacts(f, 0)
        val adjustment = adjustedWork(f)
        val preparation = amendmentPreparation(f)
        val basis = preparation["assessments"][0]
        assertTrue(basis["workAdjustmentId"].isNull)
        assertEquals(adjustment.toString(), basis["availableWorkAdjustment"]["id"].asString())
        assertEquals(30, basis["workDays"].size())
        val before = draftCounts(f)
        val preview =
            payrollBody(
                amendmentPreview(
                    f,
                    amendmentPreviewBody(
                        preparation,
                        listOf(attendanceProposal(preparation, adjustment)),
                    ),
                )
            )
        assertEquals(
            adjustment.toString(),
            preview["calculations"][0]["workAdjustmentId"].asString(),
        )
        assertEquals("WORKED", preview["calculations"][0]["workDays"][0]["attendance"].asString())
        assertEquals(before, draftCounts(f))
        assertEquals(original, assessmentFacts(f, 0))
        val draft = attendanceDraft(f, adjustment)
        assertEquals(
            adjustment.toString(),
            draftView(draft)["amendment"]["terms"]["corrections"][0]["workAdjustmentId"].asString(),
        )
    }

    @Test
    fun newerAttendanceRequiresAnotherDraftBeforeCalculationButOriginalReceiptsRemainRecoverable() {
        val f = amendmentFixture()
        val first = adjustedWork(f)
        val draft = attendanceDraft(f, first)
        val second = adjustedWork(f, absence = true)
        val before = draftCounts(f)
        val calculationKey = UUID.randomUUID()
        payrollError(
            draftCommand(draft, "calculate", 0, calculationKey),
            409,
            "stale_workforce_version",
        )
        assertEquals(before, draftCounts(f))
        assertEquals(
            0,
            payrollBody(saveDraft(f, draft.id, draft.body, draft.operation))["version"].asLong(),
        )
        val preparation = amendmentPreparation(f)
        payrollError(
            amendmentPreview(
                f,
                amendmentPreviewBody(preparation, listOf(attendanceProposal(preparation, first))),
            ),
            409,
            "stale_workforce_version",
        )
        val replacement =
            draftBody(
                f,
                preparation,
                0,
                listOf(attendanceProposal(preparation, second, absence = true)),
            )
        payrollError(
            saveDraft(f, draft.id, replacement, draft.operation),
            409,
            "operation_payload_mismatch",
        )
        assertEquals(1, payrollBody(saveDraft(f, draft.id, replacement))["version"].asLong())
        payrollBody(draftCommand(draft, "calculate", 1, calculationKey))
        assertEquals("CALCULATED", draftView(draft)["amendment"]["summary"]["status"].asString())
        val history = payrollBody(get(f.maker.client, draftsPath(f) + "/${draft.id}/revisions/0"))
        assertEquals(
            first.toString(),
            history["terms"]["corrections"][0]["workAdjustmentId"].asString(),
        )
    }

    @Test
    fun anotherCompanyEmployeeOrUnpublishedReferenceCannotBecomeTheAmendmentWorkSource() {
        val processing = processingFixture()
        val sibling =
            employee(
                processing.payroll.admin,
                processing.payroll.adminCsrf,
                processing.payroll.company,
            )
        val f = amendmentFixture(calculationFixture(f = processing))
        val current = adjustedWork(f)
        val siblingAdjustment = adjustedWork(f, employee = sibling)
        val other = amendmentFixture()
        val foreign = adjustedWork(other)
        val preparation = amendmentPreparation(f)
        val before = draftCounts(f)
        for (reference in listOf(siblingAdjustment, foreign, UUID.randomUUID())) {
            payrollError(
                amendmentPreview(
                    f,
                    amendmentPreviewBody(
                        preparation,
                        listOf(attendanceProposal(preparation, reference)),
                    ),
                ),
                409,
                "stale_workforce_version",
            )
            payrollError(
                saveDraft(
                    f,
                    UUID.randomUUID(),
                    draftBody(
                        f,
                        preparation,
                        changes = listOf(attendanceProposal(preparation, reference)),
                    ),
                ),
                409,
                "stale_workforce_version",
            )
        }
        assertEquals(before, draftCounts(f))
        attendanceDraft(f, current)
        assertEquals(
            403,
            get(other.maker.client, amendmentPath(f) + "/amendment-preparation").statusCode(),
        )
    }
}
