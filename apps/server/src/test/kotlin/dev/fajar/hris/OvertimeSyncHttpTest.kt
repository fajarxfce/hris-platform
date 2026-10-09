package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import java.net.URLEncoder
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OvertimeSyncHttpTest : OvertimeApiFixture() {
    @Test
    fun ownedOvertimeSnapshotsAndCommittedChangesUseTheMobileSyncPartition() {
        val f = overtimeFixture()
        val id = UUID.randomUUID()
        overtimeBody(plan(f, id))
        val other = overtimeFixture()
        val foreign = pending(other)
        val worker = mobileSyncTestWorker(postgres.jdbcUrl, database())
        repeat(4) { assertTrue(worker.maintain.execute() is Result.Success) }
        val path = "/api/v1/companies/${f.company}/sync"
        val bootstrap = overtimeBody(get(f.employeeClient, "$path/bootstrap?limit=1"))
        assertEquals(
            listOf("OVERTIME_REQUESTS"),
            bootstrap["collections"].iterator().asSequence().map { it.asString() }.toList(),
        )
        assertEquals(1, bootstrap["items"].size())
        assertEquals(id.toString(), bootstrap["items"][0]["id"].asString())
        assertEquals(0, bootstrap["items"][0]["version"].asLong())
        val cursor = bootstrap["changesCursor"].asString()
        overtimeBody(actual(f, id))
        overtimeBody(decide(f, id))
        assertTrue(worker.maintain.execute() is Result.Success)
        val page =
            overtimeBody(
                get(
                    f.employeeClient,
                    "$path/changes?cursor=${URLEncoder.encode(cursor,Charsets.UTF_8)}",
                )
            )
        assertEquals(
            listOf(1L, 2L),
            page["items"].iterator().asSequence().map { it["version"].asLong() }.toList(),
        )
        assertTrue(
            page["items"].iterator().asSequence().all {
                it["collection"].asString() == "OVERTIME_REQUESTS" &&
                    it["id"].asString() == id.toString() &&
                    it["id"].asString() != foreign.toString()
            }
        )
        assertEquals(404, get(f.employeeClient, "${f.path}/overtime/$foreign").statusCode())
        assertEquals(
            403,
            get(
                    other.employeeClient,
                    "$path/changes?cursor=${URLEncoder.encode(cursor,Charsets.UTF_8)}",
                )
                .statusCode(),
        )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='overtime.self.manage'",
                f.company,
                f.employeeAccount,
            )
        val revoked =
            get(
                f.employeeClient,
                "$path/changes?cursor=${URLEncoder.encode(page["cursor"].asString(),Charsets.UTF_8)}",
            )
        assertTrue(revoked.statusCode() in setOf(403, 409), revoked.body())
        assertEquals(404, get(f.employeeClient, "${f.path}/overtime/$id").statusCode())
    }
}
