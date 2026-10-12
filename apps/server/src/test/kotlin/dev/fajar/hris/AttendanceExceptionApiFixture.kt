package dev.fajar.hris

import java.net.URLEncoder
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals

abstract class AttendanceExceptionApiFixture : WorkforceAccessApiFixture() {
    protected fun queuePath(
        f: Fixture,
        kind: String? = null,
        after: String? = null,
        limit: Int = 50,
    ): String =
        "${f.path}/attendance/exceptions?from=2026-09-01&until=2026-10-01&limit=$limit" +
            (kind?.let { "&kind=$it" } ?: "") +
            (after?.let { "&after=${URLEncoder.encode(it, Charsets.UTF_8)}" } ?: "")

    protected fun exceptionPage(
        f: Fixture,
        kind: String? = null,
        after: String? = null,
        limit: Int = 50,
    ): tools.jackson.databind.JsonNode {
        val response = get(f.admin, queuePath(f, kind, after, limit))
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    protected fun recordOfflineEvent(
        f: Fixture,
        date: String = "2026-09-30",
        kind: String = "CLOCK_IN",
        at: Instant = Instant.parse("${date}T01:00:00Z"),
    ): UUID {
        val id = UUID.randomUUID()
        val response =
            command(
                f.employeeClient,
                "${f.path}/employees/${f.employee}/attendance",
                json.writeValueAsString(
                    mapOf(
                        "id" to id,
                        "workDate" to date,
                        "kind" to kind,
                        "capturedAt" to at,
                        "deviceId" to UUID.randomUUID(),
                        "offline" to true,
                    )
                ),
                f.employeeCsrf,
                UUID.randomUUID(),
            )
        assertEquals(200, response.statusCode(), response.body())
        return id
    }

    protected fun reviewEvent(f: Fixture, id: UUID, decision: String) {
        val response =
            command(
                f.admin,
                "${f.path}/attendance/$id/review",
                """{"version":0,"decision":"$decision","reason":"Independent review"}""",
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, response.statusCode(), response.body())
    }
}
