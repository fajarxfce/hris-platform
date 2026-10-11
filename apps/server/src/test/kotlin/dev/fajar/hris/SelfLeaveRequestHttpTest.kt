package dev.fajar.hris

import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
class SelfLeaveRequestHttpTest : LeaveApiFixture() {
    @Autowired private lateinit var accounts: AccountLockProbe

    @AfterEach
    fun releaseAccountProbe() {
        accounts.current.getAndSet(null)?.release?.countDown()
    }

    @Test
    fun selfDetailsNeverExpandToHrOrAssignedApproverAccess() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        assertEquals(200, adjust(f, "2").statusCode())
        val id = UUID.randomUUID()
        assertEquals(200, submit(f, id, listOf("2026-10-05" to "FIRST_HALF")).statusCode())
        val root = "/api/v1/companies/${f.company}/leave"
        val path = "$root/me/requests/$id"
        val owned = get(f.worker, path)
        assertEquals(200, owned.statusCode(), owned.body())
        assertEquals(f.account.toString(), json.readTree(owned.body())["ownerAccountId"].asString())
        assertEquals("0.5", json.readTree(owned.body())["chargedDays"].asString())
        assertEquals(200, get(f.admin, "$root/requests/$id").statusCode())
        assertEquals(200, get(f.supervisor, "$root/requests/$id").statusCode())
        for (reader in listOf(f.admin, f.supervisor)) {
            val denied = get(reader, path)
            assertEquals(404, denied.statusCode(), denied.body())
            assertEquals("leave_request_not_found", json.readTree(denied.body())["code"].asString())
            assertFalse(denied.body().contains("chargedDays"))
        }
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.read')",
                f.company,
                f.managerAccount,
            )
        assertEquals(404, get(f.supervisor, path).statusCode())
        val other = leaveFixture()
        assertEquals(
            404,
            get(f.admin, "/api/v1/companies/${other.company}/leave/me/requests/$id").statusCode(),
        )
        for (query in listOf("historyAfter=-1", "historyLimit=0", "historyLimit=51")) {
            val invalid = get(f.worker, "$path?$query")
            assertEquals(422, invalid.statusCode(), invalid.body())
            assertEquals("invalid_page", json.readTree(invalid.body())["code"].asString())
        }
        assertEquals(200, action(f, id, "withdraw", 0).statusCode())
        val first = json.readTree(get(f.worker, "$path?historyLimit=1").body())
        assertEquals("CANCELLED", first["status"].asString())
        assertEquals("1", first["history"]["nextCursor"].asString())
        val older = get(f.worker, "$path?historyLimit=1&historyAfter=1")
        assertEquals(200, older.statusCode(), older.body())
        val tail = json.readTree(older.body())
        assertEquals("CANCELLED", tail["status"].asString())
        assertEquals("SUBMITTED", tail["history"]["items"][0]["kind"].asString())
        assertTrue(tail["history"]["nextCursor"].isNull)
    }

    @ParameterizedTest
    @ValueSource(strings = ["permission", "credential", "membership"])
    fun ownDetailsRecheckAuthorityAfterTheResourceAndAccountWait(change: String) {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        assertEquals(200, adjust(f, "2").statusCode())
        val id = UUID.randomUUID()
        assertEquals(200, submit(f, id, listOf("2026-10-05" to "FULL")).statusCode())
        val barrier = AccountLockProbe.Barrier(f.account, occurrence = 2)
        accounts.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> {
                    get(f.worker, "/api/v1/companies/${f.company}/leave/me/requests/$id")
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                when (change) {
                    "credential" ->
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                f.account,
                            )
                    "membership" ->
                        database()
                            .update(
                                "update company_memberships set active=false where company_id=? and account_id=?",
                                f.company,
                                f.account,
                            )
                    else ->
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission='leave.self.manage'",
                                f.company,
                                f.account,
                            )
                }
                barrier.release.countDown()
                val response = pending.get(10, TimeUnit.SECONDS)
                assertEquals(
                    when (change) {
                        "credential" -> 401
                        "membership" -> 403
                        else -> 404
                    },
                    response.statusCode(),
                    response.body(),
                )
                assertFalse(response.body().contains("chargedDays"))
            } finally {
                barrier.release.countDown()
            }
        }
    }
}
