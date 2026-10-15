package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.jobs.domain.entities.JobLease
import dev.fajar.hris.leave.domain.usecases.AdvanceLeaveCarryExpiry
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode

class LeaveCarryBalanceHttpTest : LeaveCarryStorageFixture() {
    @Autowired private lateinit var advanceCarryExpiry: AdvanceLeaveCarryExpiry

    private fun balance(c: PreparedCarry): JsonNode =
        accountingBody(
            get(
                c.f.worker,
                "/api/v1/companies/${c.f.company}/leave/employees/${c.f.employee}/balances/${c.f.type}/2027",
            )
        )["balance"]

    private fun account(c: PreparedCarry, id: String): JsonNode =
        accountingBody(get(c.f.worker, "/api/v1/companies/${c.f.company}/leave/balances/$id"))

    @Test
    fun directoryLedgerEntitlementsAndCanonicalAccountRetainTheSameCarryFacts() {
        val c = carried()
        val read = balance(c)
        val carry = read["carry"]
        assertEquals(c.closing.id.toString(), carry["closingId"].asString())
        assertEquals("3", carry["awardedDays"].asString())
        assertEquals("3", carry["availableDays"].asString())
        assertEquals("0", carry["reservedDays"].asString())
        assertEquals("0", carry["expiredDays"].asString())
        assertEquals("2027-03-31", carry["validThrough"].asString())
        assertEquals(carryDeadline.toString(), carry["expiresAt"].asString())
        assertEquals("Asia/Jakarta", carry["timezone"].asString())
        assertEquals(0L, carry["version"].asLong())
        val directory =
            accountingBody(
                get(
                    c.f.worker,
                    "/api/v1/companies/${c.f.company}/leave/employees/${c.f.employee}/balances?year=2027",
                )
            )
        assertEquals(read, directory["balances"]["items"][0]["balance"])
        assertEquals(read, entitlementView(c.f, 2027)["balance"])
        val canonical = account(c, read["accountId"].asString())
        assertEquals(carry, canonical["carry"])
        assertEquals(read["version"], canonical["version"])
        assertTrue(entitlementView(c.f, 2026)["balance"]["carry"].isNull)
    }

    @Test
    fun elapsedTimeDoesNotRewriteStoredBalancesOrDiscardAnExpiredAward() {
        val c = carried()
        val before = balance(c)
        clock.set(carryDeadline.plusSeconds(1))
        login(c.f.worker, "${c.f.account}@example.test")
        assertEquals(before, balance(c))
        assertEquals(before["carry"], account(c, before["accountId"].asString())["carry"])
    }

    @Test
    fun unboundedValidityRemainsExplicitAndHalfDayReservationRetainsItsSource() {
        val c = carried(months = null)
        configureWorkAndApprovals(c.f, false)
        val id = UUID.randomUUID()
        accountingBody(submit(c.f, id, listOf("2027-03-15" to "FIRST_HALF")))
        val reserved = balance(c)["carry"]
        assertTrue(reserved["validThrough"].isNull)
        assertTrue(reserved["expiresAt"].isNull)
        assertEquals("2.5", reserved["availableDays"].asString())
        assertEquals("0.5", reserved["reservedDays"].asString())
        accountingBody(decide(c.f, id, 0))
        val consumed = balance(c)["carry"]
        assertEquals("0", consumed["reservedDays"].asString())
        assertEquals("0.5", consumed["consumedDays"].asString())
        assertEquals("2.5", consumed["availableDays"].asString())
    }

    @Test
    fun expiryPublishesItsMainVersionAndCanonicalReadIncludesTheMatchingBreakdown() {
        val c = carried()
        val before = balance(c)
        clock.set(carryDeadline)
        login(c.f.worker, "${c.f.account}@example.test")
        val initial =
            accountingBody(get(c.f.worker, "/api/v1/companies/${c.f.company}/sync/bootstrap"))
        val cursor = URLEncoder.encode(initial["changesCursor"].asString(), Charsets.UTF_8)
        val actor = accountingActor(c.f)
        val expiry =
            success(transactions.run(actor) { carryExpiries.list(c.f.company, null, 10) })
                .items
                .single()
        val owner = UUID.randomUUID()
        val token = UUID.randomUUID()
        database()
            .update(
                """update background_jobs set status='RUNNING',attempts=attempts+1,
            lease_owner=?,lease_token=?,lease_until=clock_timestamp()+interval '120 seconds',version=version+1
            where company_id=? and id=? and status='QUEUED'""",
                owner,
                token,
                c.f.company,
                expiry.jobId,
            )
        val job =
            requireNotNull(
                success(transactions.run(actor) { carryExpiryJobs.find(c.f.company, expiry.jobId) })
            )
        val lease = JobLease(job, owner, token, Instant.now().plusSeconds(120))
        success(advanceCarryExpiry.execute(lease))
        success(advanceCarryExpiry.execute(lease))
        assertTrue(mobileSyncTestWorker(postgres.jdbcUrl, database()).publish() is Result.Success)
        val changed =
            accountingBody(
                get(c.f.worker, "/api/v1/companies/${c.f.company}/sync/changes?cursor=$cursor")
            )
        val fresh = balance(c)
        assertEquals(before["version"].asLong() + 1, fresh["version"].asLong())
        assertEquals("0", fresh["availableDays"].asString())
        assertEquals("0", fresh["carry"]["availableDays"].asString())
        assertEquals("3", fresh["carry"]["expiredDays"].asString())
        assertEquals(1L, fresh["carry"]["version"].asLong())
        assertEquals(before["carry"]["expiresAt"], fresh["carry"]["expiresAt"])
        assertTrue(
            changed["items"].any {
                it["collection"].asString() == "LEAVE_BALANCES" &&
                    it["id"] == fresh["accountId"] &&
                    it["version"] == fresh["version"]
            }
        )
        val canonical = account(c, fresh["accountId"].asString())
        assertEquals(fresh["carry"], canonical["carry"])
        assertEquals(fresh["version"], canonical["version"])
        val other = leaveFixture(clock.instant())
        assertEquals(
            404,
            get(
                    other.admin,
                    "/api/v1/companies/${other.company}/leave/balances/${fresh["accountId"].asString()}",
                )
                .statusCode(),
        )
    }
}
