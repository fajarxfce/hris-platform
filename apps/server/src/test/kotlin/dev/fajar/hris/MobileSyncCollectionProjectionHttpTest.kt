package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import java.net.URLEncoder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MobileSyncCollectionProjectionHttpTest : LeaveAccountingApiFixture() {
    @Test
    fun nonselectedBalancesDoNotEnterBootstrapDeltasOrPendingPublication() {
        val f = accountingFixture()
        val path = "/api/v1/companies/${f.company}/sync"
        val requests = accountingBody(get(f.worker, "$path/bootstrap?collections=LEAVE_REQUESTS"))
        val cursor = URLEncoder.encode(requests["changesCursor"].asString(), Charsets.UTF_8)
        accountingBody(accrue(f))
        val waiting =
            accountingBody(get(f.worker, "$path/changes?cursor=$cursor&collections=LEAVE_REQUESTS"))
        assertEquals(0, waiting["items"].size())
        assertFalse(waiting["pendingPublication"].asBoolean())
        assertTrue(mobileSyncTestWorker(postgres.jdbcUrl, database()).publish() is Result.Success)
        val published =
            accountingBody(get(f.worker, "$path/changes?cursor=$cursor&collections=LEAVE_REQUESTS"))
        assertEquals(0, published["items"].size())
        assertFalse(published["hasMore"].asBoolean())
        val snapshot = accountingBody(get(f.worker, "$path/bootstrap?collections=LEAVE_REQUESTS"))
        assertEquals(0, snapshot["items"].size())
        val balances = accountingBody(get(f.worker, "$path/bootstrap?collections=LEAVE_BALANCES"))
        assertEquals(1, balances["items"].size())
        assertEquals("LEAVE_BALANCES", balances["items"][0]["collection"].asString())
        assertEquals(entitlementView(f)["balance"]["accountId"], balances["items"][0]["id"])
    }
}
