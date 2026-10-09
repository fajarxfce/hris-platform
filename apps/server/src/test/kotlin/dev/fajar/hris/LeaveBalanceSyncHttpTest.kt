package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import java.net.URLEncoder
import java.time.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveBalanceSyncHttpTest : LeaveAccountingApiFixture() {
    @Test
    fun ownedBalanceFeedTracksVersionedGrantsAndBothSidesOfClosing() {
        val f = accountingFixture(Instant.parse("2027-01-05T03:00:00Z"))
        val initial = accountingBody(get(f.worker, "/api/v1/companies/${f.company}/sync/bootstrap"))
        assertEquals(0, initial["items"].size())
        accountingBody(accrue(f))
        accountingBody(closeYear(f))
        val published = mobileSyncTestWorker(postgres.jdbcUrl, database()).publish()
        assertTrue(published is Result.Success, published.toString())
        val cursor = URLEncoder.encode(initial["changesCursor"].asString(), Charsets.UTF_8)
        val delta =
            accountingBody(
                get(f.worker, "/api/v1/companies/${f.company}/sync/changes?cursor=$cursor")
            )
        val events = delta["items"].iterator().asSequence().toList()
        assertEquals(4, events.size)
        assertTrue(events.all { it["collection"].asString() == "LEAVE_BALANCES" })
        assertFalse(delta.toString().contains("Monthly entitlement"))
        val source = entitlementView(f)["balance"]
        val sourceEvents = events.filter { it["id"].asString() == source["accountId"].asString() }
        assertEquals(listOf(1L, 2L, 3L), sourceEvents.map { it["version"].asLong() })
        val snapshot =
            accountingBody(get(f.worker, "/api/v1/companies/${f.company}/sync/bootstrap"))
        assertEquals(2, snapshot["items"].size())
        for (item in snapshot["items"].iterator().asSequence()) {
            val canonical =
                accountingBody(
                    get(
                        f.worker,
                        "/api/v1/companies/${f.company}/leave/balances/${item["id"].asString()}",
                    )
                )
            assertEquals(item["version"].asLong(), canonical["version"].asLong())
        }
        assertEquals(
            403,
            get(f.supervisor, "/api/v1/companies/${f.company}/sync/bootstrap").statusCode(),
        )
    }

    @Test
    fun ownerRevocationInvalidatesThePartitionAndNeverExposesAnotherEmployeeBalance() {
        val f = accountingFixture()
        accountingBody(accrue(f))
        assertTrue(mobileSyncTestWorker(postgres.jdbcUrl, database()).publish() is Result.Success)
        val snapshot =
            accountingBody(get(f.worker, "/api/v1/companies/${f.company}/sync/bootstrap"))
        val id = snapshot["items"][0]["id"].asString()
        val cursor = URLEncoder.encode(snapshot["changesCursor"].asString(), Charsets.UTF_8)
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.self.manage')",
                f.company,
                f.managerAccount,
            )
        val manager =
            accountingBody(get(f.supervisor, "/api/v1/companies/${f.company}/sync/bootstrap"))
        assertEquals(0, manager["items"].size())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='leave.self.manage'",
                f.company,
                f.account,
            )
        assertEquals(
            403,
            get(f.worker, "/api/v1/companies/${f.company}/sync/changes?cursor=$cursor").statusCode(),
        )
        assertEquals(
            404,
            get(f.worker, "/api/v1/companies/${f.company}/leave/balances/$id").statusCode(),
        )
    }
}
