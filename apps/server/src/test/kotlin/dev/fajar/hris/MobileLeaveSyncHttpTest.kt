package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import java.net.URLEncoder
import java.net.http.HttpResponse
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.JsonNode

@TestPropertySource(properties = [MOBILE_SYNC_TEST_KEYS])
class MobileLeaveSyncHttpTest : LeaveApiFixture() {
    private fun body(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    private fun bootstrap(f: Fixture) =
        body(get(f.worker, "/api/v1/companies/${f.company}/sync/bootstrap"))

    private fun changes(f: Fixture, cursor: String) =
        body(
            get(
                f.worker,
                "/api/v1/companies/${f.company}/sync/changes?cursor=${URLEncoder.encode(cursor,Charsets.UTF_8)}",
            )
        )

    private fun publish(): Int {
        val result = mobileSyncTestWorker(postgres.jdbcUrl, database()).publish()
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    @Test
    fun leaveSubmissionApprovalAndCancellationPublishVersionsWithoutPrivateReasons() {
        val f = fixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "2"))
        val initial = bootstrap(f).get("changesCursor").asString()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val days = listOf("2026-10-05" to "FULL")
        val submitted = body(submit(f, id, days, key))
        assertEquals(submitted, body(submit(f, id, days, key)))
        body(decide(f, id, 0))
        body(action(f, id, "cancellation", 1))
        body(decide(f, id, 2))
        publish()
        val delta = changes(f, initial)
        val items = delta.get("items").iterator().asSequence().toList()
        assertEquals(listOf(0L, 1L, 2L, 3L), items.map { it.get("version").asLong() })
        assertTrue(
            items.all {
                it.get("collection").asString() == "LEAVE_REQUESTS" &&
                    it.get("id").asString() == id.toString() &&
                    it.get("operation").asString() == "UPSERT"
            }
        )
        assertFalse(delta.toString().contains("Personal leave"))
        assertFalse(delta.toString().contains(f.employee.toString()))
        val snapshot = bootstrap(f)
        assertEquals(1, snapshot.get("items").size())
        assertEquals(3, snapshot.get("items")[0].get("version").asLong())
        assertEquals("CANCELLED", details(f, id).get("status").asString())
        assertEquals(
            403,
            get(f.supervisor, "/api/v1/companies/${f.company}/sync/bootstrap").statusCode(),
        )
    }

    @Test
    fun withdrawnRequestsKeepTheirCancelledCanonicalStateAndOwnerScope() {
        val f = fixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "2"))
        val id = UUID.randomUUID()
        body(submit(f, id, listOf("2026-10-05" to "FULL")))
        body(action(f, id, "withdraw", 0))
        publish()
        val snapshot = bootstrap(f)
        assertEquals(id.toString(), snapshot.get("items")[0].get("id").asString())
        assertEquals("CANCELLED", details(f, id).get("status").asString())
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.self.manage')",
                f.company,
                f.managerAccount,
            )
        val manager = body(get(f.supervisor, "/api/v1/companies/${f.company}/sync/bootstrap"))
        assertEquals(0, manager.get("items").size())
        assertEquals(0, changes(f, snapshot.get("changesCursor").asString()).get("items").size())
    }
}
