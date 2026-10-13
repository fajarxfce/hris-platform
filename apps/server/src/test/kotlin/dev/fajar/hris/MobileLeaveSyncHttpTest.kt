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
    @Test
    fun explicitReadOnlyLeaveProjectionRemainsOwnedAndRevocationStopsItsCursor() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "2"))
        val id = UUID.randomUUID()
        body(submit(f, id, listOf("2026-10-05" to "FULL")))
        publish()
        database()
            .update(
                "update membership_permissions set permission='leave.read' where company_id=? and account_id=? and permission='leave.self.manage'",
                f.company,
                f.account,
            )
        val path = "/api/v1/companies/${f.company}/sync"
        val selection = "collections=LEAVE_REQUESTS,LEAVE_BALANCES"
        assertEquals(403, get(f.worker, "$path/bootstrap").statusCode())
        val snapshot = body(get(f.worker, "$path/bootstrap?$selection"))
        val refs = snapshot["items"].iterator().asSequence().toList()
        assertEquals(2, refs.size)
        assertEquals(
            id.toString(),
            refs.single { it["collection"].asString() == "LEAVE_REQUESTS" }["id"].asString(),
        )
        val accountId =
            refs.single { it["collection"].asString() == "LEAVE_BALANCES" }["id"].asString()
        assertEquals(
            f.employee.toString(),
            body(get(f.worker, "/api/v1/companies/${f.company}/leave/balances/$accountId"))[
                    "employeeId"]
                .asString(),
        )

        // An HR read grant does not export a colleague's requests or balances through sync.
        assertEquals(403, get(f.supervisor, "$path/bootstrap?$selection").statusCode())
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.read')",
                f.company,
                f.managerAccount,
            )
        assertEquals(0, body(get(f.supervisor, "$path/bootstrap?$selection"))["items"].size())
        val cursor = URLEncoder.encode(snapshot["changesCursor"].asString(), Charsets.UTF_8)
        assertEquals(
            0,
            body(get(f.worker, "$path/changes?$selection&cursor=$cursor"))["items"].size(),
        )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='leave.read'",
                f.company,
                f.account,
            )
        assertEquals(403, get(f.worker, "$path/changes?$selection&cursor=$cursor").statusCode())
    }

    private fun body(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    private fun bootstrap(f: LeaveFixture) =
        body(get(f.worker, "/api/v1/companies/${f.company}/sync/bootstrap"))

    private fun changes(f: LeaveFixture, cursor: String) =
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
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "2"))
        publish()
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
        val items =
            delta
                .get("items")
                .iterator()
                .asSequence()
                .filter { it.get("collection").asString() == "LEAVE_REQUESTS" }
                .toList()
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
        assertEquals(2, snapshot.get("items").size())
        val request =
            snapshot.get("items").iterator().asSequence().single {
                it.get("collection").asString() == "LEAVE_REQUESTS"
            }
        assertEquals(3, request.get("version").asLong())
        assertEquals("CANCELLED", details(f, id).get("status").asString())
        assertEquals(
            403,
            get(f.supervisor, "/api/v1/companies/${f.company}/sync/bootstrap").statusCode(),
        )
    }

    @Test
    fun withdrawnRequestsKeepTheirCancelledCanonicalStateAndOwnerScope() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "2"))
        val id = UUID.randomUUID()
        body(submit(f, id, listOf("2026-10-05" to "FULL")))
        body(action(f, id, "withdraw", 0))
        publish()
        val snapshot = bootstrap(f)
        assertEquals(
            id.toString(),
            snapshot
                .get("items")
                .iterator()
                .asSequence()
                .single { it.get("collection").asString() == "LEAVE_REQUESTS" }
                .get("id")
                .asString(),
        )
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
