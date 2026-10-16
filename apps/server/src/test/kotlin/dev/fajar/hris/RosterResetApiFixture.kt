package dev.fajar.hris

import java.net.http.HttpResponse
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import tools.jackson.databind.JsonNode

abstract class RosterResetApiFixture : WorkforceAccessApiFixture() {
    protected val workDate = "2026-10-02"

    protected fun roster(
        f: Fixture,
        version: Long? = null,
        shift: UUID? = null,
        day: String = workDate,
        operation: UUID = UUID.randomUUID(),
    ): HttpResponse<String> =
        command(
            f.admin,
            "${f.path}/employees/${f.employee}/roster/$day",
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to version,
                    "shift" to shift?.let { mapOf("id" to it, "version" to 0) },
                    "reason" to "Reviewed roster fixture",
                )
            ),
            f.csrf,
            operation,
            "PUT",
        )

    protected fun reset(
        f: Fixture,
        version: Long = 0,
        operation: UUID = UUID.randomUUID(),
        day: String = workDate,
    ): HttpResponse<String> =
        command(
            f.admin,
            "${f.path}/employees/${f.employee}/roster/$day/reset",
            """{"expectedVersion":$version,"reason":"Restore the reviewed calendar"}""",
            f.csrf,
            operation,
        )

    protected fun pattern(f: Fixture, shift: UUID) {
        body(
            command(
                f.admin,
                "${f.path}/employees/${f.employee}/schedule",
                json.writeValueAsString(
                    mapOf(
                        "effectiveFrom" to "2026-01-01",
                        "days" to
                            java.time.DayOfWeek.entries.associate {
                                it.name to mapOf("id" to shift, "version" to 0)
                            },
                        "reason" to "Frozen weekly fixture",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
    }

    protected fun review(f: Fixture, day: String = workDate): JsonNode =
        body(get(f.admin, "${f.path}/employees/${f.employee}/calendar/review?from=$day&until=$day"))

    protected fun body(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    protected fun row(f: Fixture) =
        database()
            .queryForMap(
                "select id,version,active,shift from roster_days where company_id=? and employment_id=? and work_date=?::date",
                f.company,
                f.employee,
                workDate,
            )

    protected fun revisions(f: Fixture) =
        database()
            .queryForList(
                "select revision,active,shift from roster_revisions where company_id=? and employment_id=? and work_date=?::date order by revision",
                f.company,
                f.employee,
                workDate,
            )
}
