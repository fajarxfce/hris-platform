package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.usecases.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode

class LeaveCarryExpiryHttpTest : LeaveCarryStorageFixture() {
    @Autowired private lateinit var advanceExpiry: AdvanceLeaveCarryExpiry
    @Autowired private lateinit var abortExpiry: AbortLeaveCarryExpiry

    private fun path(c: PreparedCarry) = "/api/v1/companies/${c.f.company}/leave/carry-expiries"

    private fun origin(c: PreparedCarry): LeaveCarryExpiry =
        success(
                transactions.run(accountingActor(c.f)) { carryExpiries.list(c.f.company, null, 10) }
            )
            .items
            .single()

    private fun detail(c: PreparedCarry, id: UUID = origin(c).id): JsonNode =
        accountingBody(get(c.f.admin, "${path(c)}/$id"))

    private fun activate(c: PreparedCarry, jobId: UUID = origin(c).jobId): JobLease {
        val owner = UUID.randomUUID()
        val token = UUID.randomUUID()
        assertEquals(
            1,
            database()
                .update(
                    """update background_jobs set status='RUNNING',attempts=attempts+1,
            lease_owner=?,lease_token=?,lease_until=clock_timestamp()+interval '120 seconds',version=version+1
            where company_id=? and id=? and status='QUEUED'""",
                    owner,
                    token,
                    c.f.company,
                    jobId,
                ),
        )
        val job =
            success(
                transactions.run(accountingActor(c.f)) { carryExpiryJobs.find(c.f.company, jobId) }
            )!!
        return JobLease(job, owner, token, Instant.now().plusSeconds(120))
    }

    @Test
    fun queuedHistoryRetainsOriginalPolicyAndDeadlineWithoutWorkerAuthority() {
        val c = carried()
        val origin = origin(c)
        val first = detail(c)
        val item = first["expiry"]
        assertEquals(origin.id.toString(), item["id"].asString())
        assertEquals("CLOSING", item["sourceKind"].asString())
        assertEquals(c.closing.id.toString(), item["sourceId"].asString())
        assertEquals(c.f.employee.toString(), item["employeeId"].asString())
        assertEquals("ANNUAL", item["typeCode"].asString())
        assertEquals("Annual leave", item["typeName"].asString())
        assertEquals(2027, item["year"].asInt())
        assertEquals("Asia/Jakarta", item["timezone"].asString())
        assertEquals("2027-03-31", item["validThrough"].asString())
        assertEquals(carryDeadline, Instant.parse(item["expiresAt"].asString()))
        assertEquals("QUEUED", item["status"].asString())
        assertEquals(0, item["completedEmployees"].asInt())
        assertEquals(1, item["totalEmployees"].asInt())
        assertEquals(listOf(item), accountingBody(get(c.f.admin, path(c)))["items"].toList())
        assertTrue(first["results"]["items"].isEmpty)
        assertTrue(first["employees"].isEmpty)
        assertTrue(first["availableActions"].isEmpty)
        assertEquals(1, first["attempts"].size())
        assertEquals(
            setOf(
                "id",
                "status",
                "completedItems",
                "totalItems",
                "attempts",
                "version",
                "failureCode",
                "scheduledFor",
                "availableAt",
                "finishedAt",
            ),
            first["job"].propertyNames().toSet(),
        )

        accountingBody(
            command(
                c.f.admin,
                "/api/v1/companies/${c.f.company}/leave/types/${c.f.type}",
                json.writeValueAsString(
                    mapOf(
                        "code" to "ANNUAL",
                        "name" to "Updated annual policy",
                        "effectiveFrom" to "2026-01-01",
                        "paid" to true,
                        "allowPartialDays" to true,
                        "minServiceMonths" to 0,
                        "expectedVersion" to 2,
                        "accrual" to
                            mapOf(
                                "frequency" to "MONTHLY",
                                "daysPerPeriod" to "1",
                                "carryLimitDays" to "3",
                            ),
                        "reason" to "Policy rename",
                    )
                ),
                c.f.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        assertEquals(item, detail(c)["expiry"])
    }

    @Test
    fun employeeProgressExcludesTheFinalizationStepAndPagesOnlyMinimalReferences() {
        val c = carried()
        val lease = activate(c)
        clock.set(carryDeadline)
        success(advanceExpiry.execute(lease))
        login(c.f.admin)
        val inProgress = detail(c)
        assertEquals("RUNNING", inProgress["expiry"]["status"].asString())
        assertEquals(1, inProgress["expiry"]["completedEmployees"].asInt())
        assertEquals(1, inProgress["job"]["completedItems"].asInt())
        assertEquals(2, inProgress["job"]["totalItems"].asInt())
        val outcome = inProgress["results"]["items"].single()
        assertEquals("EXPIRED", outcome["status"].asString())
        assertEquals("3", outcome["expiredDays"].asString())
        assertEquals(c.f.employee.toString(), outcome["employeeId"].asString())
        val employee = inProgress["employees"].single()
        assertEquals(c.f.employee.toString(), employee["id"].asString())
        assertEquals("Example Employee", employee["name"].asString())
        assertEquals(setOf("id", "name", "number"), employee.propertyNames().toSet())
        success(advanceExpiry.execute(lease))
        val completed = detail(c)
        assertEquals("SUCCEEDED", completed["expiry"]["status"].asString())
        assertEquals(1, completed["expiry"]["completedEmployees"].asInt())
        assertEquals(2, completed["job"]["completedItems"].asInt())
        assertTrue(completed["availableActions"].isEmpty)
        val tail = accountingBody(get(c.f.admin, "${path(c)}/${origin(c).id}?after=1&limit=1"))
        assertTrue(tail["results"]["items"].isEmpty)
        assertTrue(tail["employees"].isEmpty)
        assertTrue(tail["results"]["nextCursor"].isNull)
    }

    @Test
    fun recoveryPreservesOutcomesAndItsReceiptWithoutAnotherDebit() {
        val c = carried()
        val previous = origin(c)
        val lease = activate(c)
        clock.set(carryDeadline)
        success(advanceExpiry.execute(lease))
        success(
            abortExpiry.execute(lease, Failure(FailureKind.CONFLICT, "fixture_processing_stopped"))
        )
        val csrf = login(c.f.admin)
        val failed = detail(c)
        assertEquals(listOf("RESUME"), failed["availableActions"].toList().map { it.asString() })
        assertEquals("fixture_processing_stopped", failed["expiry"]["failureCode"].asString())
        val entries = accountingRows(c.f, "leave_ledger")
        val operation = UUID.randomUUID()
        val body =
            json.writeValueAsString(
                mapOf("expectedVersion" to 0, "reason" to "Resume remaining work")
            )
        val url = "${path(c)}/${previous.id}/resume"
        val receipt = accountingBody(command(c.f.admin, url, body, csrf, operation))
        assertEquals(1, receipt["version"].asLong())
        assertEquals(receipt, accountingBody(command(c.f.admin, url, body, csrf, operation)))
        val resumed = detail(c)
        assertEquals(1, resumed["expiry"]["version"].asLong())
        assertEquals("QUEUED", resumed["expiry"]["status"].asString())
        assertEquals(carryDeadline, Instant.parse(resumed["expiry"]["expiresAt"].asString()))
        assertEquals(1, resumed["expiry"]["completedEmployees"].asInt())
        assertEquals(1, resumed["job"]["totalItems"].asInt())
        assertEquals(listOf(0, 1), resumed["attempts"].toList().map { it["baseCompleted"].asInt() })
        assertEquals(
            previous.jobId.toString(),
            resumed["results"]["items"].single()["jobId"].asString(),
        )
        assertEquals(2, accountingRows(c.f, "background_jobs"))
        assertEquals(entries, accountingRows(c.f, "leave_ledger"))
        assertTrue(resumed["availableActions"].isEmpty)
        val fresh =
            json.writeValueAsString(mapOf("expectedVersion" to 1, "reason" to "Already running"))
        accountingCode(
            command(c.f.admin, url, fresh, csrf, UUID.randomUUID()),
            409,
            "leave_carry_expiry_not_stopped",
        )
        success(advanceExpiry.execute(activate(c, origin(c).jobId)))
        assertEquals("SUCCEEDED", detail(c)["expiry"]["status"].asString())
        assertEquals(entries, accountingRows(c.f, "leave_ledger"))
    }

    @Test
    fun companyAndReadPermissionsNeverGrantRecoveryOrExposeOtherOrigins() {
        val c = carried()
        val id = origin(c).id
        val other = accountingFixture(clock.instant())
        accountingCode(
            get(other.admin, "/api/v1/companies/${other.company}/leave/carry-expiries/$id"),
            404,
            "leave_carry_expiry_not_found",
        )
        assertTrue(
            accountingBody(
                    get(other.admin, "/api/v1/companies/${other.company}/leave/carry-expiries")
                )["items"]
                .isEmpty
        )
        accountingCode(get(c.f.worker, path(c)), 403, "access_denied")
        accountingCode(get(c.f.worker, "${path(c)}/$id"), 403, "access_denied")
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.read')",
                c.f.company,
                c.f.account,
            )
        database()
            .update(
                "update background_jobs set status='FAILED',finished_at=now() where company_id=?",
                c.f.company,
            )
        val allowedRead = accountingBody(get(c.f.worker, "${path(c)}/$id"))
        assertTrue(allowedRead["availableActions"].isEmpty)
        accountingCode(
            command(
                c.f.worker,
                "${path(c)}/$id/resume",
                json.writeValueAsString(
                    mapOf("expectedVersion" to 0, "reason" to "Not authorized")
                ),
                c.f.workerCsrf,
                UUID.randomUUID(),
            ),
            403,
            "access_denied",
        )
        assertEquals(1, accountingRows(c.f, "background_jobs"))
        assertEquals(0, origin(c).version)
    }

    @Test
    fun invalidPagesAndRecoveryCommandsCannotChangeTheQueue() {
        val c = carried()
        val id = origin(c).id
        for (query in listOf("?limit=0", "?limit=101", "?limit=-1")) {
            accountingCode(get(c.f.admin, "${path(c)}$query"), 422, "invalid_page")
            accountingCode(get(c.f.admin, "${path(c)}/$id$query"), 422, "invalid_page")
        }
        for (after in listOf(0, -1, 5001)) accountingCode(
            get(c.f.admin, "${path(c)}/$id?after=$after"),
            422,
            "invalid_page",
        )
        for ((version, reason) in
            listOf(
                -1 to "Invalid version",
                8 to "Invalid version",
                0 to " ",
                0 to "x".repeat(1001),
            )) {
            accountingCode(
                command(
                    c.f.admin,
                    "${path(c)}/$id/resume",
                    json.writeValueAsString(
                        mapOf("expectedVersion" to version, "reason" to reason)
                    ),
                    c.f.adminCsrf,
                    UUID.randomUUID(),
                ),
                422,
                "invalid_leave_carry_expiry_recovery",
            )
        }
        val body = json.writeValueAsString(mapOf("expectedVersion" to 0, "reason" to "Resume work"))
        assertEquals(
            400,
            post(c.f.admin, "${path(c)}/$id/resume", body, c.f.adminCsrf).statusCode(),
        )
        accountingCode(
            command(
                c.f.admin,
                "${path(c)}/${UUID.randomUUID()}/resume",
                body,
                c.f.adminCsrf,
                UUID.randomUUID(),
            ),
            404,
            "leave_carry_expiry_not_found",
        )
        assertEquals(1, accountingRows(c.f, "background_jobs"))
        assertEquals(1, accountingRows(c.f, "leave_carry_expiry_attempts"))
    }

    @Test
    fun listUsesBoundedStableIdPaginationWithinOneCompany() {
        val c = carried()
        val otherEmployee = c.f.copy(employee = c.f.manager, account = c.f.managerAccount)
        accountingBody(adjust(otherEmployee, "3"))
        val id = UUID.randomUUID()
        val second =
            c.copy(
                f = otherEmployee,
                closing = c.closing.copy(id = id, employeeId = otherEmployee.employee),
                award =
                    requireNotNull(c.award)
                        .copy(
                            bucket = requireNotNull(c.award).bucket.copy(id = id),
                            employeeId = otherEmployee.employee,
                        ),
            )
        success(storeCarry(second))
        val firstPage = accountingBody(get(c.f.admin, "${path(c)}?limit=1"))
        val cursor = firstPage["nextCursor"].asString()
        val next = accountingBody(get(c.f.admin, "${path(c)}?limit=1&after=$cursor"))
        assertEquals(1, firstPage["items"].size())
        assertEquals(1, next["items"].size())
        assertNotEquals(firstPage["items"].single()["id"], next["items"].single()["id"])
        assertTrue(next["nextCursor"].isNull)
        assertEquals(
            setOf(c.f.employee.toString(), c.f.manager.toString()),
            (firstPage["items"].toList() + next["items"].toList())
                .map { it["employeeId"].asString() }
                .toSet(),
        )
    }
}
