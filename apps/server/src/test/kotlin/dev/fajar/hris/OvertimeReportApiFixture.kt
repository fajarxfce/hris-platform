package dev.fajar.hris

import java.net.http.HttpClient
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import tools.jackson.databind.JsonNode

abstract class OvertimeReportApiFixture : OvertimeApiFixture() {
    protected fun reportPath(f: Fixture, status: String? = null): String =
        "/api/v1/companies/${f.company}/reports/overtime?from=2026-08-31&until=2026-10-01" +
            (status?.let { "&status=$it" } ?: "")

    protected fun report(
        f: Fixture,
        status: String? = null,
        client: HttpClient = f.admin,
    ): JsonNode {
        val response = get(client, reportPath(f, status))
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        return overtimeBody(response)
    }

    protected fun holiday(f: Fixture, date: String) {
        overtimeBody(
            command(
                f.admin,
                "${f.path}/holidays/${UUID.randomUUID()}",
                json.writeValueAsString(
                    mapOf(
                        "workDate" to date,
                        "name" to "Report fixture holiday",
                        "reason" to "Report calendar setup",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
    }
}
