package dev.fajar.hris

import java.net.http.HttpResponse
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import tools.jackson.databind.JsonNode

abstract class RosterBulkApiFixture : RosterResetApiFixture() {
    protected fun selection(
        f: Fixture,
        employees: List<UUID> = listOf(f.employee),
        dates: List<String> = listOf(workDate, "2026-10-03"),
        mode: String = "DAY_OFF",
        shift: UUID? = null,
    ): Map<String, Any?> =
        mapOf(
            "employeeIds" to employees,
            "dates" to dates,
            "mode" to mode,
            "shift" to shift?.let { mapOf("id" to it, "version" to 0) },
        )

    protected fun bulkReview(f: Fixture, selected: Map<String, Any?> = selection(f)): JsonNode =
        body(
            command(
                f.admin,
                "${f.path}/roster/bulk/review",
                json.writeValueAsString(selected),
                f.csrf,
                UUID.randomUUID(),
            )
        )

    protected fun bulkApply(
        f: Fixture,
        selected: Map<String, Any?>,
        reviewed: JsonNode,
        operation: UUID = UUID.randomUUID(),
        reason: String = "Reviewed roster changes",
    ): HttpResponse<String> =
        command(
            f.admin,
            "${f.path}/roster/bulk",
            json.writeValueAsString(
                mapOf(
                    "selection" to selected,
                    "reviewRevision" to reviewed["revision"].asString(),
                    "reason" to reason,
                )
            ),
            f.csrf,
            operation,
        )

    protected fun savedRows(f: Fixture) =
        database()
            .queryForList(
                "select employment_id,work_date,id,version,active,shift::text from roster_days where company_id=? order by employment_id,work_date",
                f.company,
            )

    protected fun noBulkCommit(f: Fixture, key: UUID) {
        for (query in
            listOf(
                "select count(*) from operation_receipts where company_id=? and operation_id=?",
                "select count(*) from audit_entries where company_id=? and resource_id=?",
            )) assertEquals(0, database().queryForObject(query, Int::class.java, f.company, key))
    }
}
