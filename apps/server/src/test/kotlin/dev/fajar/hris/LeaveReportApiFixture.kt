package dev.fajar.hris

import java.net.URLEncoder
import org.junit.jupiter.api.Assertions.*
import tools.jackson.databind.JsonNode

abstract class LeaveReportApiFixture : LeaveAttachmentApiFixture() {
    protected fun reportPath(
        f: LeaveFixture,
        requests: Boolean = false,
        status: String? = null,
        after: String? = null,
        limit: Int = 50,
    ): String =
        "/api/v1/companies/${f.company}/reports/leave${if (requests) "/requests" else ""}?from=2026-10-06&until=2026-10-09" +
            (if (requests) "&limit=$limit" else "") +
            (status?.let { "&status=$it" } ?: "") +
            (after?.let { "&after=${URLEncoder.encode(it, Charsets.UTF_8)}" } ?: "")

    protected fun report(f: LeaveFixture): JsonNode {
        val response = get(f.admin, reportPath(f))
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        return body(response)
    }

    protected fun requests(
        f: LeaveFixture,
        status: String? = null,
        after: String? = null,
        limit: Int = 50,
    ): JsonNode = body(get(f.admin, reportPath(f, true, status, after, limit)))

    protected fun counts(value: JsonNode, status: String): JsonNode =
        value["statuses"].single { it["status"].asString() == status }["counts"]
}
