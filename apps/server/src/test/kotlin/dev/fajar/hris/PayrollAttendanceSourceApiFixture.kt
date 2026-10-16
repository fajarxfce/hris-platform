package dev.fajar.hris

import java.util.UUID
import tools.jackson.databind.JsonNode

abstract class PayrollAttendanceSourceApiFixture : PayrollRunApiFixture() {
    protected fun lateAttendance(
        f: ProcessingFixture,
        date: String = "2026-09-01",
        absence: Boolean = false,
        employee: UUID = f.payroll.employee,
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
                    "reason" to "Reviewed late workforce evidence",
                )
            )
        return UUID.fromString(
            payrollBody(
                    command(f.payroll.admin, path, body, f.payroll.adminCsrf, UUID.randomUUID())
                )["id"]
                .asString()
        )
    }

    protected fun selectedInput(
        f: CalculationFixture,
        adjustment: UUID,
        expectedVersion: Long = 1,
    ) {
        payrollBody(
            inputSave(
                f.people,
                f.work,
                inputBody(
                    f.work,
                    expectedVersion,
                    changes = mapOf("workAdjustmentId" to adjustment),
                    termChanges = mapOf("dayResolutions" to runPaidDays().drop(1)),
                ),
            )
        )
        payrollBody(inputVerify(f.people, expectedVersion + 1))
    }

    protected fun result(f: CalculationFixture, run: UUID): JsonNode =
        payrollBody(
            get(
                f.people.reviewer.client,
                runPath(f, run) + "/employees/${f.people.payroll.employee}",
            )
        )

    protected fun inputState(f: ProcessingFixture): Map<String, List<String>> =
        listOf(
                "payroll_inputs",
                "payroll_input_revisions",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )
            .associateWith { table ->
                database()
                    .queryForList(
                        "select to_jsonb(t)::text from $table t where company_id=? order by to_jsonb(t)::text",
                        String::class.java,
                        f.payroll.company,
                    )
                    .map { requireNotNull(it) }
            }
}
