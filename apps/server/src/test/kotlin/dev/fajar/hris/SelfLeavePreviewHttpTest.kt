package dev.fajar.hris

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
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
class SelfLeavePreviewHttpTest : LeaveApiFixture() {
    @Autowired private lateinit var accounts: AccountLockProbe

    @AfterEach
    fun releaseProbe() {
        accounts.current.getAndSet(null)?.release?.countDown()
    }

    @Test
    fun previewUsesWorkingDatesAndHalfDaysWithoutReservingOrCreatingARequest() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        assertEquals(200, adjust(f, "2").statusCode())
        val selected =
            listOf("2026-10-05" to "FIRST_HALF", "2026-10-06" to "FULL", "2026-10-10" to "FULL")
        val before = balance(f).toString()
        repeat(2) {
            val response = preview(f, selected)
            assertEquals(200, response.statusCode(), response.body())
            val value = json.readTree(response.body())
            assertEquals(f.employee.toString(), value["employeeId"].asString())
            assertEquals("Asia/Jakarta", value["timezone"].asString())
            assertEquals(clock.instant().toString(), value["evaluatedAt"].asString())
            assertEquals("1.5", value["chargedDays"].asString())
            assertEquals(2, value["days"].size())
            assertEquals("2026-10-05T15:00:00Z", value["days"][0]["startsAt"].asString())
            assertEquals("2026-10-05T23:00:00Z", value["days"][0]["endsAt"].asString())
            assertEquals(225, value["days"][0]["chargedMinutes"].asInt())
            assertEquals(
                listOf("2026-10-10"),
                value["excludedDates"].iterator().asSequence().map { it.asString() }.toList(),
            )
            assertEquals(1, value["balances"].size())
            val available = value["balances"][0]
            assertEquals("2", available["availableDays"].asString())
            assertEquals("0", available["reservedDays"].asString())
            assertEquals("1.5", available["requestedDays"].asString())
            assertEquals("0.5", available["remainingDays"].asString())
            assertEquals(before, balance(f).toString())
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from leave_requests where company_id=?",
                        Int::class.java,
                        f.company,
                    ),
            )
        }
        val submitted = submit(f, UUID.randomUUID(), selected)
        assertEquals(200, submitted.statusCode(), submitted.body())
        val id = UUID.fromString(json.readTree(submitted.body())["id"].asString())
        assertEquals("1.5", details(f, id, f.worker)["chargedDays"].asString())
        assertEquals("1.5", balance(f)["reservedDays"].asString())
    }

    @Test
    fun previewReportsEvidenceRequirementsAndRechecksCurrentBalanceAndOccupancy() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        val selected = listOf("2026-10-05" to "FULL")
        val insufficient = preview(f, selected)
        assertEquals(409, insufficient.statusCode(), insufficient.body())
        assertEquals(
            "insufficient_leave_balance",
            json.readTree(insufficient.body())["code"].asString(),
        )
        assertEquals(200, adjust(f, "3").statusCode())
        assertEquals(200, preview(f, selected).statusCode())
        assertEquals(200, submit(f, UUID.randomUUID(), selected).statusCode())
        val occupied = preview(f, selected)
        assertEquals(409, occupied.statusCode(), occupied.body())
        assertEquals("leave_overlap", json.readTree(occupied.body())["code"].asString())
        assertEquals(200, policy(f, version = 0, attachmentRequired = true).statusCode())
        val available = preview(f, listOf("2026-10-06" to "FULL"))
        assertEquals(200, available.statusCode(), available.body())
        assertTrue(json.readTree(available.body())["policy"]["attachmentRequired"].asBoolean())
        val missing = submit(f, UUID.randomUUID(), listOf("2026-10-06" to "FULL"))
        assertEquals(422, missing.statusCode(), missing.body())
        assertEquals("leave_attachment_required", json.readTree(missing.body())["code"].asString())
    }

    @Test
    fun preparationRemainsOwnedAndRejectsUnboundedOrNonworkingSelections() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        assertEquals(200, adjust(f, "2").statusCode())
        val selected = listOf("2026-10-05" to "FULL")
        val other = leaveFixture()
        for (response in
            listOf(
                preview(f, selected, employee = f.manager),
                preview(f, selected, employee = other.employee),
            )) {
            assertEquals(404, response.statusCode(), response.body())
            assertFalse(response.body().contains("chargedDays"))
        }
        val reader = preview(f, selected, client = f.supervisor, csrf = f.supervisorCsrf)
        assertEquals(403, reader.statusCode(), reader.body())
        val admin = preview(f, selected, client = f.admin, csrf = f.adminCsrf)
        assertEquals(403, admin.statusCode(), admin.body())
        for (permission in listOf("leave.self.manage", "leave.manage")) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                f.company,
                f.managerAccount,
                permission,
            )
        val broader = preview(f, selected, client = f.supervisor, csrf = f.supervisorCsrf)
        assertEquals(404, broader.statusCode(), broader.body())
        assertFalse(broader.body().contains("chargedDays"))
        for (days in
            listOf(
                emptyList(),
                selected + selected,
                listOf("2026-10-05" to "FULL", "2027-10-06" to "FULL"),
                listOf("1899-10-05" to "FULL"),
            )) {
            val response = preview(f, days)
            assertEquals(422, response.statusCode(), response.body())
        }
        val weekend = preview(f, listOf("2026-10-10" to "FULL"))
        assertEquals(422, weekend.statusCode(), weekend.body())
        assertEquals("no_leave_working_days", json.readTree(weekend.body())["code"].asString())
        val foreignPolicy = preview(f, selected, type = other.type)
        assertEquals(422, foreignPolicy.statusCode(), foreignPolicy.body())
        assertEquals(
            "leave_type_unavailable",
            json.readTree(foreignPolicy.body())["code"].asString(),
        )
    }

    @Test
    fun leavePreparationDoesNotRequireOtherEmployeeModulesToBeEnabled() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        assertEquals(200, adjust(f, "2").statusCode())
        val path = "/api/v1/companies/${f.company}/settings/client-policy"
        fun save(version: Long?, disabled: List<String>): HttpResponse<String> =
            command(
                f.admin,
                path,
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to version,
                        "disabledModules" to disabled,
                        "minimumBuilds" to mapOf("android" to 0, "ios" to 0, "web" to 0),
                        "reason" to "Self-service module independence",
                    )
                ),
                f.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        val selected = listOf("2026-10-05" to "FULL")
        val disabled = save(null, listOf("PEOPLE", "WORKFORCE"))
        assertEquals(200, disabled.statusCode(), disabled.body())
        assertEquals(200, preview(f, selected).statusCode())
        assertEquals(200, save(0, listOf("LEAVE")).statusCode())
        val denied = preview(f, selected)
        assertEquals(403, denied.statusCode(), denied.body())
        assertEquals("company_module_disabled", json.readTree(denied.body())["code"].asString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["permission", "credential", "membership"])
    fun previewRechecksAuthorityAfterResourceAndAccountWaits(change: String) {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        assertEquals(200, adjust(f, "2").statusCode())
        val barrier = AccountLockProbe.Barrier(f.account, occurrence = 2)
        accounts.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> { preview(f, listOf("2026-10-05" to "FULL")) }
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
                    if (change == "credential") 401 else 403,
                    response.statusCode(),
                    response.body(),
                )
                assertFalse(response.body().contains("chargedDays"))
            } finally {
                barrier.release.countDown()
            }
        }
    }

    private fun preview(
        f: LeaveFixture,
        days: List<Pair<String, String>>,
        employee: UUID = f.employee,
        type: UUID = f.type,
        client: HttpClient = f.worker,
        csrf: String = f.workerCsrf,
    ): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(
                    URI(
                        "http://127.0.0.1:$port/api/v1/companies/${f.company}/leave/me/requests/preview"
                    )
                )
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", csrf)
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(
                            mapOf(
                                "employeeId" to employee,
                                "typeId" to type,
                                "days" to
                                    days.map {
                                        mapOf("workDate" to it.first, "portion" to it.second)
                                    },
                            )
                        )
                    )
                )
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
}
