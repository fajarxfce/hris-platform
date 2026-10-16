package dev.fajar.hris

import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class RosterBulkHttpTest : RosterBulkApiFixture() {
    @Test
    fun reviewReturnsAnExplicitCoherentCrossProductWithoutCreatingAnyBusinessRows() {
        val f = isolatedFixture()
        val second = anotherEmployee(f)
        val selected =
            selection(
                f,
                employees = listOf(second, f.employee),
                dates = listOf("2026-10-03", workDate),
            )
        val reviewed = bulkReview(f, selected)
        assertEquals(4, reviewed["changeCount"].asInt())
        assertEquals(
            listOf(second, f.employee).map(UUID::toString).sorted(),
            reviewed["employeeIds"].iterator().asSequence().map { it.asString() }.toList(),
        )
        assertEquals(
            listOf(workDate, "2026-10-03"),
            reviewed["dates"].iterator().asSequence().map { it.asString() }.toList(),
        )
        assertEquals(2, reviewed["employees"].size())
        for (entry in reviewed["employees"]) {
            assertEquals(
                setOf("id", "employeeNumber", "legalName", "status", "version"),
                entry["employee"].propertyNames().toSet(),
            )
            assertEquals(2, entry["days"].size())
            for (day in entry["days"]) {
                assertTrue(day["expectedVersion"].isNull)
                assertFalse(day["overrideActive"].asBoolean())
                assertTrue(day["changed"].asBoolean())
                assertEquals("UNASSIGNED", day["before"]["kind"].asString())
                assertEquals("OFF", day["after"]["kind"].asString())
                assertEquals("ROSTER", day["after"]["origin"].asString())
            }
        }
        assertTrue(savedRows(f).isEmpty())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from work_periods where company_id=? and month='2026-10-01'::date",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(reviewed, bulkReview(f, selected))
    }

    @Test
    fun oneReceiptCommitsEverySelectedDayAndExplicitRetryDoesNotDuplicateHistory() {
        val f = isolatedFixture()
        val second = anotherEmployee(f)
        val selected =
            selection(
                f,
                employees = listOf(f.employee, second),
                mode = "SHIFT",
                shift = createShift(f),
            )
        val reviewed = bulkReview(f, selected)
        val key = UUID.randomUUID()
        val first = body(bulkApply(f, selected, reviewed, key))
        assertEquals(key.toString(), first["id"].asString())
        assertEquals(0, first["version"].asLong())
        assertEquals(first, body(bulkApply(f, selected, reviewed, key)))
        val rows = savedRows(f)
        assertEquals(4, rows.size)
        assertTrue(rows.all { it["version"] == 0L && it["active"] == true && it["shift"] != null })
        assertEquals(
            4,
            database()
                .queryForObject(
                    "select count(*) from roster_revisions where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where company_id=? and action='workforce.roster_bulk_saved'",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(409, bulkApply(f, selected, reviewed, key, "Another instruction").statusCode())
        assertEquals(rows, savedRows(f))
    }

    @Test
    fun mixedResetRetainsInactiveHeadsAndLeavesUnselectedOverridesUntouched() {
        val f = isolatedFixture()
        pattern(f, createShift(f))
        body(roster(f))
        body(roster(f, day = "2026-10-03"))
        body(reset(f, day = "2026-10-03"))
        body(roster(f, day = "2026-10-05"))
        val selected =
            selection(f, dates = listOf(workDate, "2026-10-03", "2026-10-04"), mode = "CALENDAR")
        val reviewed = bulkReview(f, selected)
        assertEquals(1, reviewed["changeCount"].asInt())
        val original = savedRows(f)
        body(bulkApply(f, selected, reviewed))
        val updated = savedRows(f)
        assertEquals(3, updated.size)
        assertEquals(original.map { it["id"] }, updated.map { it["id"] })
        assertEquals(listOf(1L, 1L, 0L), updated.map { it["version"] })
        assertEquals(listOf(false, false, true), updated.map { it["active"] })
        assertEquals("PATTERN", review(f)["calendar"]["days"][0]["origin"].asString())
        val noChanges = bulkReview(f, selected)
        assertEquals(0, noChanges["changeCount"].asInt())
        val key = UUID.randomUUID()
        val response = bulkApply(f, selected, noChanges, key)
        assertEquals(409, response.statusCode())
        assertEquals("roster_bulk_unchanged", json.readTree(response.body())["code"].asString())
        noBulkCommit(f, key)
    }

    @Test
    fun aChangedReviewedCellPreventsAllOtherCellsFromBeingSaved() {
        val f = isolatedFixture()
        val selected = selection(f)
        val reviewed = bulkReview(f, selected)
        body(roster(f, shift = createShift(f)))
        val original = savedRows(f)
        val key = UUID.randomUUID()
        val failed = bulkApply(f, selected, reviewed, key)
        assertEquals(409, failed.statusCode())
        assertEquals("roster_bulk_changed", json.readTree(failed.body())["code"].asString())
        assertEquals(original, savedRows(f))
        noBulkCommit(f, key)
        body(bulkApply(f, selected, bulkReview(f, selected), key))
        assertEquals(2, savedRows(f).size)
    }

    @Test
    fun updatedEmploymentOrRestoredPatternRequiresAnotherReview() {
        val f = isolatedFixture()
        pattern(f, createShift(f))
        body(roster(f))
        val selected = selection(f, dates = listOf(workDate), mode = "CALENDAR")
        val reviewed = bulkReview(f, selected)
        body(
            command(
                f.admin,
                "${f.path}/employees/${f.employee}/schedule",
                """{"effectiveFrom":"2026-10-02","days":{},"expectedVersion":0,"reason":"Changed weekly pattern"}""",
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        assertEquals(
            "roster_bulk_changed",
            json.readTree(bulkApply(f, selected, reviewed).body())["code"].asString(),
        )
        val again = bulkReview(f, selected)
        body(
            command(
                f.admin,
                "/api/v1/companies/${f.company}/employees/${f.employee}/revisions",
                """{"version":0,"terms":{"effectiveFrom":"2026-11-01","startDate":"2026-01-01","contract":"PERMANENT","status":"ACTIVE"},"reason":"Future employment review"}""",
                f.csrf,
                UUID.randomUUID(),
            )
        )
        assertEquals(
            "roster_bulk_changed",
            json.readTree(bulkApply(f, selected, again).body())["code"].asString(),
        )
        assertEquals(0L, row(f)["version"])
    }

    @Test
    fun aClosingMonthRejectsTheWholeCrossMonthSelectionButPreservesReceiptRecovery() {
        val f = isolatedFixture()
        val selected = selection(f, dates = listOf("2026-09-30", "2026-10-01"))
        val reviewed = bulkReview(f, selected)
        val key = UUID.randomUUID()
        val first = body(bulkApply(f, selected, reviewed, key))
        val change =
            selection(
                f,
                dates = listOf("2026-09-30", "2026-10-02"),
                mode = "SHIFT",
                shift = createShift(f),
            )
        val prepared = bulkReview(f, change)
        body(close(f))
        val original = savedRows(f)
        assertEquals(409, bulkApply(f, change, prepared).statusCode())
        assertEquals(original, savedRows(f))
        assertEquals(first, body(bulkApply(f, selected, reviewed, key)))
    }

    @Test
    fun maximumInteractiveSelectionPersistsExactlyTwoHundredCells() {
        val f = isolatedFixture()
        val employees = listOf(f.employee) + List(49) { anotherEmployee(f) }
        val selected =
            selection(
                f,
                employees,
                List(4) { LocalDate.parse(workDate).plusDays(it.toLong()).toString() },
            )
        val reviewed = bulkReview(f, selected)
        assertEquals(200, reviewed["changeCount"].asInt())
        val key = UUID.randomUUID()
        val saved = body(bulkApply(f, selected, reviewed, key))
        assertEquals(200, savedRows(f).size)
        assertEquals(
            200,
            database()
                .queryForObject(
                    "select count(*) from roster_revisions where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(saved, body(bulkApply(f, selected, reviewed, key)))
        assertEquals(200, savedRows(f).size)
    }

    @Test
    fun twoCompetingBatchesCannotPublishAMixtureOfTheirProposals() {
        val f = isolatedFixture()
        val dayOff = selection(f)
        val working = selection(f, mode = "SHIFT", shift = createShift(f))
        val offReview = bulkReview(f, dayOff)
        val workReview = bulkReview(f, working)
        val ready = CountDownLatch(2)
        val release = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val requests =
                listOf(dayOff to offReview, working to workReview).map { (selected, reviewed) ->
                    pool.submit<Int> {
                        ready.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        bulkApply(f, selected, reviewed).statusCode()
                    }
                }
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                release.countDown()
                assertEquals(
                    listOf(200, 409),
                    requests.map { it.get(10, TimeUnit.SECONDS) }.sorted(),
                )
            } finally {
                release.countDown()
            }
        }
        val rows = savedRows(f)
        assertEquals(2, rows.size)
        assertEquals(1, rows.map { it["shift"] }.distinct().size)
        assertTrue(rows.all { it["version"] == 0L })
    }

    @ParameterizedTest
    @ValueSource(strings = ["employee", "shift"])
    fun foreignAndMissingReferencesDoNotDiscloseAnotherCompany(mode: String) {
        val f = isolatedFixture()
        val other = isolatedFixture()
        val reference = if (mode == "employee") other.employee else createShift(other)
        val responses =
            listOf(reference, UUID.randomUUID()).map { id ->
                val selected =
                    if (mode == "employee") selection(f, employees = listOf(id))
                    else selection(f, mode = "SHIFT", shift = id)
                command(
                    f.admin,
                    "${f.path}/roster/bulk/review",
                    json.writeValueAsString(selected),
                    f.csrf,
                    UUID.randomUUID(),
                )
            }
        assertEquals(responses[0].statusCode(), responses[1].statusCode())
        assertEquals(
            json.readTree(responses[0].body())["code"],
            json.readTree(responses[1].body())["code"],
        )
        assertTrue(responses[0].statusCode() in setOf(404, 422))
        assertTrue(savedRows(f).isEmpty())
    }

    @Test
    fun selectionBoundsRequiredFieldsAndShiftVersionsAreTransportValidated() {
        val f = isolatedFixture()
        val path = "${f.path}/roster/bulk/review"
        val valid = selection(f)
        for (missing in listOf("employeeIds", "dates", "mode")) {
            assertEquals(
                400,
                command(
                        f.admin,
                        path,
                        json.writeValueAsString(valid - missing),
                        f.csrf,
                        UUID.randomUUID(),
                    )
                    .statusCode(),
            )
        }
        for (invalid in
            listOf(
                valid + ("employeeIds" to emptyList<UUID>()),
                valid + ("employeeIds" to listOf(f.employee, f.employee)),
                valid + ("dates" to listOf(workDate, "2026-11-02")),
                valid + ("mode" to "SHIFT"),
            )) assertEquals(
            422,
            command(f.admin, path, json.writeValueAsString(invalid), f.csrf, UUID.randomUUID())
                .statusCode(),
        )
        val shift = createShift(f)
        for (reference in listOf(mapOf("id" to shift), mapOf("id" to shift, "version" to 0.5))) {
            val invalid = valid + mapOf("mode" to "SHIFT", "shift" to reference)
            assertEquals(
                400,
                command(f.admin, path, json.writeValueAsString(invalid), f.csrf, UUID.randomUUID())
                    .statusCode(),
            )
        }
        assertEquals(
            403,
            command(f.admin, path, json.writeValueAsString(valid), "invalid", UUID.randomUUID())
                .statusCode(),
        )
        assertEquals(
            403,
            command(
                    f.employeeClient,
                    path,
                    json.writeValueAsString(valid),
                    f.employeeCsrf,
                    UUID.randomUUID(),
                )
                .statusCode(),
        )
        assertTrue(savedRows(f).isEmpty())
    }
}
