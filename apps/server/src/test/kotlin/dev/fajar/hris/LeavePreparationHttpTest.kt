package dev.fajar.hris

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeavePreparationHttpTest : LeaveAttachmentApiFixture() {
    @Test
    fun managementDiscoveryReturnsBoundedMinimalCompanyReferences() {
        val f = leaveFixture()
        val foreign = leaveFixture()
        val (_, operator) = member(f, listOf("leave.manage"))
        val first = body(get(operator, "${path(f)}?limit=1"))
        assertEquals(
            setOf("companyDate", "timezone", "employees"),
            first.properties().map { it.key }.toSet(),
        )
        assertEquals(
            LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Jakarta")).toString(),
            first["companyDate"].asString(),
        )
        assertEquals("Asia/Jakarta", first["timezone"].asString())
        val row = first["employees"]["items"][0]
        assertEquals(
            setOf("id", "employeeNumber", "legalName", "status", "version"),
            row.properties().map { it.key }.toSet(),
        )
        val next =
            body(
                get(
                    operator,
                    "${path(f)}?limit=1&after=${first["employees"]["nextCursor"].asString()}",
                )
            )["employees"]
        assertTrue(next["nextCursor"].isNull)
        assertEquals(
            setOf(f.employee.toString(), f.manager.toString()),
            setOf(row["id"].asString(), next["items"][0]["id"].asString()),
        )
        val searched = body(get(operator, "${path(f)}?query=${row["employeeNumber"].asString()}"))
        assertEquals(row["id"].asString(), searched["employees"]["items"][0]["id"].asString())
        assertEquals(403, get(operator, path(foreign)).statusCode())
        assertEquals(
            403,
            get(operator, "/api/v1/companies/${f.company}/employees?asOf=2026-10-01").statusCode(),
        )
        for (query in
            listOf(
                "limit=0",
                "limit=51",
                "query=${"x".repeat(121)}",
                "after=",
                "after=${"a".repeat(33)}",
            )) error(get(operator, "${path(f)}?$query"), 422, "invalid_page")
    }

    @Test
    fun evidenceSelectionIncludesOnlyPublishedPersonalMetadataForTheSelectedEmployee() {
        val at = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val f = leaveFixture(at)
        val foreign = leaveFixture(at)
        val ready = setOf(evidence(f), evidence(f))
        evidence(f, ready = false)
        evidence(f, employee = f.manager)
        evidence(f, classification = "RECEIPT")
        evidence(f, classification = "HR_ONLY")
        evidence(foreign)
        val (_, operator) = member(f, listOf("leave.manage"))
        storageProbe.beforeRead = { error("Preparation must not read document bytes") }
        val first = body(get(operator, "${path(f)}/${f.employee}/evidence?limit=1"))
        assertEquals(1, first["items"].size())
        assertEquals(
            setOf(
                "documentId",
                "revisionId",
                "revisionNumber",
                "title",
                "fileName",
                "mediaType",
                "size",
                "validatedAt",
            ),
            first["items"][0].properties().map { it.key }.toSet(),
        )
        val second =
            body(
                get(
                    operator,
                    "${path(f)}/${f.employee}/evidence?limit=1&after=${first["nextCursor"].asString()}",
                )
            )
        assertTrue(second["nextCursor"].isNull)
        assertEquals(
            ready.map(UUID::toString).toSet(),
            setOf(
                first["items"][0]["revisionId"].asString(),
                second["items"][0]["revisionId"].asString(),
            ),
        )
        error(get(operator, "${path(f)}/${foreign.employee}/evidence"), 404, "employee_not_found")
        assertEquals(
            403,
            get(operator, "/api/v1/companies/${f.company}/documents?employmentId=${f.employee}")
                .statusCode(),
        )
        for (limit in listOf(0, 51)) error(
            get(operator, "${path(f)}/${f.employee}/evidence?limit=$limit"),
            422,
            "invalid_page",
        )
    }

    @Test
    fun administrativePreviewReservesNothingAndItsRevisionProtectsSubmissionAndReplay() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "4"))
        val (_, operator) = member(f, listOf("leave.manage"))
        val selected =
            listOf("2026-10-05" to "FIRST_HALF", "2026-10-06" to "FULL", "2026-10-10" to "FULL")
        val before = balance(f).toString()
        val review = body(preview(f, operator, selected))
        assertEquals("1.5", review["chargedDays"].asString())
        assertEquals("2026-10-10", review["excludedDates"][0].asString())
        assertEquals("2.5", review["balances"][0]["remainingDays"].asString())
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
        body(adjust(f, "1"))
        val id = UUID.randomUUID()
        val operation = UUID.randomUUID()
        val csrf = body(get(operator, "/api/v1/auth/csrf"))["token"].asString()
        fun send(revision: String) =
            command(
                operator,
                "/api/v1/companies/${f.company}/leave/requests",
                json.writeValueAsString(
                    mapOf(
                        "id" to id,
                        "employeeId" to f.employee,
                        "typeId" to f.type,
                        "days" to
                            selected.map { mapOf("workDate" to it.first, "portion" to it.second) },
                        "reason" to "Employee leave request recorded by HR",
                        "previewRevision" to revision,
                    )
                ),
                csrf,
                operation,
            )
        error(send(review["revision"].asString()), 409, "leave_preview_changed")
        val current = body(preview(f, operator, selected))["revision"].asString()
        val receipt = body(send(current))
        assertEquals(id.toString(), receipt["id"].asString())
        assertEquals("1.5", balance(f)["reservedDays"].asString())
        body(adjust(f, "1"))
        assertEquals(receipt, body(send(current)))
        assertEquals("1.5", balance(f)["reservedDays"].asString())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from leave_requests where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun administrativePermissionDoesNotBroadenSelfPreviewAndOtherGrantsDoNotPrepareRequests() {
        val f = leaveFixture()
        val foreign = leaveFixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "2"))
        val (_, manager) = member(f, listOf("leave.manage", "leave.self.manage"))
        val days = listOf("2026-10-05" to "FULL")
        error(preview(f, manager, days, self = true), 404, "employee_not_found")
        error(preview(f, manager, days, employee = foreign.employee), 404, "employee_not_found")
        for (permission in
            listOf(
                "leave.read",
                "leave.team.read",
                "leave.team.approve",
                "leave.self.manage",
                "documents.self.read",
            )) {
            val (_, operator) = member(f, listOf(permission))
            for (suffix in listOf("", "/${f.employee}/evidence")) error(
                get(operator, path(f) + suffix),
                403,
                "access_denied",
            )
            error(preview(f, operator, days), 403, "access_denied")
        }
    }

    @Test
    fun administrativePreviewCanReviewEligibleDatesBeforeEmploymentEnded() {
        val f = leaveFixture(Instant.parse("2026-10-13T05:00:00Z"))
        configureWorkAndApprovals(f)
        body(adjust(f, "2"))
        body(
            command(
                f.admin,
                "/api/v1/companies/${f.company}/employees/${f.employee}/revisions",
                json.writeValueAsString(
                    mapOf(
                        "version" to 0,
                        "terms" to
                            mapOf(
                                "effectiveFrom" to "2026-10-07",
                                "startDate" to "2025-01-01",
                                "endDate" to "2026-10-06",
                                "status" to "ENDED",
                                "contract" to "PERMANENT",
                            ),
                        "reason" to "Historical request preparation after employment ended",
                    )
                ),
                f.adminCsrf,
                UUID.randomUUID(),
            )
        )
        val (_, operator) = member(f, listOf("leave.manage"))
        val selected = listOf("2026-10-05" to "FULL")
        assertEquals("1", body(preview(f, operator, selected))["chargedDays"].asString())
        error(preview(f, f.worker, selected, self = true), 404, "employee_not_found")
        assertEquals("0", balance(f, f.admin)["reservedDays"].asString())
    }

    @Test
    fun preparationRequiresLeaveAvailabilityWithoutDependingOnOtherModules() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "2"))
        val (_, operator) = member(f, listOf("leave.manage"))
        fun disable(version: Long?, modules: List<String>) =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/settings/client-policy",
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to version,
                        "disabledModules" to modules,
                        "minimumBuilds" to mapOf("android" to 0, "ios" to 0, "web" to 0),
                        "reason" to "Validate leave preparation module boundary",
                    )
                ),
                f.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        body(disable(null, listOf("PEOPLE", "WORKFORCE", "DOCUMENTS")))
        body(get(operator, path(f)))
        body(get(operator, "${path(f)}/${f.employee}/evidence"))
        body(preview(f, operator, listOf("2026-10-05" to "FULL")))
        body(disable(0, listOf("LEAVE")))
        for (suffix in listOf("", "/${f.employee}/evidence")) error(
            get(operator, path(f) + suffix),
            403,
            "company_module_disabled",
        )
        error(preview(f, operator, listOf("2026-10-05" to "FULL")), 403, "company_module_disabled")
    }

    private fun path(f: LeaveFixture) = "/api/v1/companies/${f.company}/leave/preparation/employees"

    private fun preview(
        f: LeaveFixture,
        browser: HttpClient,
        days: List<Pair<String, String>>,
        employee: UUID = f.employee,
        self: Boolean = false,
    ): HttpResponse<String> {
        val csrf = body(get(browser, "/api/v1/auth/csrf"))["token"].asString()
        val route = if (self) "me/requests" else "requests"
        return browser.send(
            HttpRequest.newBuilder(
                    URI("http://127.0.0.1:$port/api/v1/companies/${f.company}/leave/$route/preview")
                )
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", csrf)
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(
                            mapOf(
                                "employeeId" to employee,
                                "typeId" to f.type,
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
}
