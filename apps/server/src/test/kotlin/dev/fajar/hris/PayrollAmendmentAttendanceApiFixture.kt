package dev.fajar.hris

import java.util.UUID
import tools.jackson.databind.JsonNode

abstract class PayrollAmendmentAttendanceApiFixture : PayrollAmendmentPublicationApiFixture() {
    protected fun adjustedWork(
        f: AmendmentFixture,
        absence: Boolean = false,
        employee: UUID = f.payroll.employee,
        date: String = "2026-09-01",
    ): UUID {
        val path =
            "/api/v1/companies/${f.payroll.company}/workforce/employees/$employee/attendance/adjustments"
        val review = payrollBody(get(f.payroll.admin, "$path/review?workDate=$date"))
        val body =
            json.writeValueAsString(
                mapOf(
                    "workDate" to date,
                    "reviewRevision" to review["revision"].asString(),
                    "clockIn" to if (absence) null else "${date}T01:00:00Z",
                    "clockOut" to if (absence) null else "${date}T09:00:00Z",
                    "breakMinutes" to if (absence) 0 else 60,
                    "decisions" to emptyList<Any>(),
                    "reason" to "Reviewed attendance after payroll publication",
                )
            )
        return UUID.fromString(
            payrollBody(
                    command(f.payroll.admin, path, body, f.payroll.adminCsrf, UUID.randomUUID())
                )["id"]
                .asString()
        )
    }

    protected fun attendanceProposal(
        preparation: JsonNode,
        adjustment: UUID,
        absence: Boolean = false,
    ): Map<String, Any?> {
        val source = preparation["assessments"][0]
        val input =
            source["input"]
                .propertyNames()
                .associateWith { source["input"][it] as Any? }
                .toMutableMap()
        if (!absence)
            input["dayResolutions"] =
                source["input"]["dayResolutions"].filter {
                    it["workDate"].asString() != "2026-09-01"
                }
        return mapOf(
            "assessmentId" to source["source"]["assessmentId"].asString(),
            "workAdjustmentId" to adjustment,
            "input" to input,
        )
    }

    protected fun attendanceDraft(
        f: AmendmentFixture,
        adjustment: UUID,
        absence: Boolean = false,
    ): DraftFixture {
        val preparation = amendmentPreparation(f)
        val draft =
            DraftFixture(
                f,
                UUID.randomUUID(),
                UUID.randomUUID(),
                draftBody(
                    f,
                    preparation,
                    changes = listOf(attendanceProposal(preparation, adjustment, absence)),
                ),
            )
        payrollBody(saveDraft(f, draft.id, draft.body, draft.operation))
        return draft
    }

    protected fun assessmentFacts(f: AmendmentFixture, revision: Long): JsonNode =
        json.readTree(
            database()
                .queryForObject(
                    "select facts::text from payroll_assessment_evidence where company_id=? and assessment_id=? and revision=?",
                    String::class.java,
                    f.payroll.company,
                    f.assessment,
                    revision,
                )!!
        )
}
