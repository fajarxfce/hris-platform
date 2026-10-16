package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.repositories.AttendanceAdjustmentRepository
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException

class AttendanceAdjustmentHttpTest : AttendanceAdjustmentApiFixture() {
    @Autowired private lateinit var adjustments: AttendanceAdjustmentRepository
    @Autowired private lateinit var transactions: TransactionRunner

    @Test
    fun anExplicitRevisionRetainsOriginalClosingAndUpdatesTheOwnedDailyProjection() {
        val f = closedFixture()
        val late = capture(f)
        val facts = unchangedFacts(f)
        val review = adjustmentReview(f)
        assertEquals(0, review["adjustmentRevision"].asInt())
        assertEquals(1, review["attendance"]["pendingCount"].asInt())
        val saved = adjusted(f, adjustmentBody(review))
        assertEquals(1, saved["version"].asInt())
        assertEquals(facts, unchangedFacts(f))
        val next = adjustmentReview(f)
        assertEquals("UNASSIGNED", next["original"]["fact"].asString())
        assertEquals("WORKED", next["current"]["fact"].asString())
        assertEquals(420, next["current"]["acceptedMinutes"].asInt())
        assertEquals(0, next["attendance"]["pendingCount"].asInt())
        assertEquals(420, next["attendance"]["acceptedMinutes"].asInt())
        assertEquals("ACCEPTED", next["attendance"]["entries"][0]["status"].asString())
        assertEquals("PENDING", next["attendance"]["entries"][0]["initialStatus"].asString())
        assertEquals(late.toString(), next["current"]["evidenceIds"][0].asString())
        val head =
            database()
                .queryForMap(
                    "select id,version from attendance_sync_heads where company_id=? and employment_id=? and work_date='2026-09-30'",
                    f.company,
                    f.employee,
                )
        assertEquals(2L, head["version"])
        assertEquals(
            listOf(0L, 1L, 2L),
            database()
                .queryForList(
                    "select resource_version from mobile_sync_changes where company_id=? and collection='ATTENDANCE_DAYS' order by resource_version",
                    Long::class.java,
                    f.company,
                ),
        )
        val canonical = get(f.employeeClient, "${f.path}/attendance/days/${head["id"]}")
        assertEquals(200, canonical.statusCode(), canonical.body())
        assertEquals(420, json.readTree(canonical.body())["day"]["acceptedMinutes"].asInt())
        assertEquals(0, json.readTree(canonical.body())["day"]["pendingCount"].asInt())
        val details = get(f.employeeClient, "${adjustmentPath(f)}/${saved["id"].asString()}")
        assertEquals(200, details.statusCode(), details.body())
        assertEquals(30, json.readTree(details.body())["snapshot"]["days"].size())
    }

    @Test
    fun laterCorrectionsRetainEveryRevisionAndOriginalOperationRecoveryNeverReappliesOlderFacts() {
        val f = closedFixture()
        val firstBody = adjustmentBody(adjustmentReview(f))
        val key = UUID.randomUUID()
        val first = adjusted(f, firstBody, key)
        val exactPath = "${adjustmentPath(f)}/${first["id"].asString()}"
        val retained = get(f.admin, exactPath).body()
        val secondDate = "2026-09-29"
        val second =
            adjusted(f, adjustmentBody(adjustmentReview(f, secondDate), secondDate, absence = true))
        assertEquals(2, second["version"].asInt())
        val facts = adjustmentState(f)
        val replay = adjusted(f, firstBody, key)
        assertEquals(first["id"], replay["id"])
        assertEquals(first, replay)
        assertEquals(facts, adjustmentState(f))
        assertEquals(retained, get(f.admin, exactPath).body())
        val head =
            json.readTree(get(f.admin, "${adjustmentPath(f)}/${second["id"].asString()}").body())
        assertEquals("ABSENCE_RECORDED", head["snapshot"]["days"][28]["fact"].asString())
        assertEquals("WORKED", head["snapshot"]["days"][29]["fact"].asString())
        val page = json.readTree(get(f.admin, "${adjustmentPath(f)}?month=2026-09&limit=1").body())
        assertEquals(2, page["items"][0]["revision"].asInt())
        assertEquals("2", page["nextCursor"].asString())
        val previous =
            json.readTree(
                get(f.admin, "${adjustmentPath(f)}?month=2026-09&limit=1&before=2").body()
            )
        assertEquals(first["id"], previous["items"][0]["id"])
        assertTrue(previous["nextCursor"].isNull)
        assertEquals(
            409,
            adjust(f, firstBody + ("reason" to "Changed original command"), key).statusCode(),
        )
    }

    @Test
    fun aNewCaptureInvalidatesTheReviewAndMissingOrDuplicateDecisionsCannotPartiallyVerifyIt() {
        val f = closedFixture()
        val old = adjustmentReview(f)
        val late = capture(f)
        val before = adjustmentState(f)
        assertEquals(
            "attendance_adjustment_changed",
            json.readTree(adjust(f, adjustmentBody(old)).body())["code"].asString(),
        )
        val review = adjustmentReview(f)
        val body = adjustmentBody(review)
        val decisions = body["decisions"] as List<*>
        for (invalid in
            listOf(
                body + ("decisions" to emptyList<Any>()),
                body + ("decisions" to (decisions + decisions)),
            )) {
            assertTrue(adjust(f, invalid).statusCode() in setOf(409, 422))
            assertEquals(before, adjustmentState(f))
        }
        val rejected =
            body +
                ("decisions" to
                    listOf(
                        mapOf("eventId" to late, "expectedVersion" to 0, "decision" to "REJECT")
                    ))
        adjusted(f, rejected)
        assertEquals(
            "REJECTED",
            adjustmentReview(f)["attendance"]["entries"][0]["status"].asString(),
        )
        capture(f)
        assertEquals(1, adjustmentReview(f)["attendance"]["pendingCount"].asInt())
    }

    @Test
    fun anOpenOrProcessingPeriodCannotUseTheLateCorrectionWorkflowAndReadCreatesNoPeriod() {
        val f = isolatedFixture()
        val before = unchangedFacts(f)
        assertEquals(
            409,
            get(f.admin, "${adjustmentPath(f)}/review?workDate=$adjustmentDate").statusCode(),
        )
        assertEquals(before, unchangedFacts(f))
        begin(f)
        assertEquals(
            409,
            get(f.admin, "${adjustmentPath(f)}/review?workDate=$adjustmentDate").statusCode(),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from attendance_adjustments where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun ordinaryCorrectionsStayLockedAndIdenticalLateCorrectionsRequireNoNewRevision() {
        val f = closedFixture()
        val body = adjustmentBody(adjustmentReview(f))
        adjusted(f, body)
        val before = adjustmentState(f)
        val duplicate = adjust(f, adjustmentBody(adjustmentReview(f)))
        assertEquals(
            "attendance_adjustment_unchanged",
            json.readTree(duplicate.body())["code"].asString(),
        )
        val ordinary =
            command(
                f.admin,
                "${f.path}/employees/${f.employee}/attendance/corrections",
                json.writeValueAsString(
                    body + ("expectedVersion" to 0) - "reviewRevision" - "decisions"
                ),
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals("work_period_locked", json.readTree(ordinary.body())["code"].asString())
        assertEquals(before, adjustmentState(f))
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "missingDecisions",
                "missingClockIn",
                "missingClockOut",
                "fractionalVersion",
                "fractionalBreak",
                "tooMany",
                "invalidTime",
                "blankReason",
            ]
    )
    fun invalidCommandsCannotWriteAnyPartOfAnAdjustment(mode: String) {
        val f = closedFixture()
        val late = capture(f)
        val body = adjustmentBody(adjustmentReview(f))
        val invalid =
            when (mode) {
                "missingDecisions" -> body - "decisions"
                "missingClockIn" -> body - "clockIn"
                "missingClockOut" -> body - "clockOut"
                "fractionalVersion" ->
                    body +
                        ("decisions" to
                            listOf(
                                mapOf(
                                    "eventId" to late,
                                    "expectedVersion" to 0.5,
                                    "decision" to "ACCEPT",
                                )
                            ))
                "fractionalBreak" -> body + ("breakMinutes" to 0.5)
                "tooMany" ->
                    body +
                        ("decisions" to
                            List(33) {
                                mapOf(
                                    "eventId" to UUID.randomUUID(),
                                    "expectedVersion" to 0,
                                    "decision" to "ACCEPT",
                                )
                            })
                "invalidTime" -> body + ("clockOut" to "2026-10-01T10:00:00Z")
                else -> body + ("reason" to " ")
            }
        val before = adjustmentState(f)
        assertTrue(adjust(f, invalid).statusCode() in setOf(400, 422))
        assertEquals(before, adjustmentState(f))
    }

    @Test
    fun anotherCompanyCannotReadOrWriteAnAdjustmentAndItsStorageRemainsAppendOnly() {
        val f = closedFixture()
        val saved = adjusted(f, adjustmentBody(adjustmentReview(f)))
        val id = UUID.fromString(saved["id"].asString())
        val other = isolatedFixture()
        assertEquals(404, get(other.admin, "${adjustmentPath(other, f.employee)}/$id").statusCode())
        assertEquals(
            404,
            get(other.admin, "${adjustmentPath(other, f.employee)}?month=2026-09").statusCode(),
        )
        assertEquals(
            404,
            get(other.admin, "${adjustmentPath(other, f.employee)}/review?workDate=$adjustmentDate")
                .statusCode(),
        )
        assertEquals(
            Result.Success(null),
            transactions.run(other.actor) { adjustments.find(f.company, id) },
        )
        assertThrows(DataAccessException::class.java) {
            database().update("delete from attendance_adjustments where company_id=?", f.company)
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update attendance_adjustments set reason='Replacement' where company_id=?",
                    f.company,
                )
        }
        assertEquals(422, get(f.admin, "${adjustmentPath(f)}?month=2026-09&limit=51").statusCode())
        assertEquals(422, get(f.admin, "${adjustmentPath(f)}?month=2026-09&before=0").statusCode())
    }

    @Test
    fun correctionAuthorityDoesNotPermitSelfCorrectionOrImplicitVerification() {
        val f = closedFixture()
        capture(f)
        val body = adjustmentBody(adjustmentReview(f))
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission in ('attendance.verify','attendance.team.verify')",
                f.company,
                f.actor.accountId,
            )
        assertEquals(403, adjust(f, body).statusCode())
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'attendance.correct')",
                f.company,
                f.employeeAccount,
            )
        val own = get(f.employeeClient, "${adjustmentPath(f)}/review?workDate=$adjustmentDate")
        assertEquals(200, own.statusCode(), own.body())
        assertFalse(json.readTree(own.body())["canCorrect"].asBoolean())
        val self =
            command(
                f.employeeClient,
                adjustmentPath(f),
                json.writeValueAsString(body),
                f.employeeCsrf,
                UUID.randomUUID(),
            )
        assertEquals("self_correction_denied", json.readTree(self.body())["code"].asString())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from attendance_adjustments where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun differentDaysCompeteForOneMonthlyRevisionAndRereviewPreservesTheWinnersDay() {
        val f = closedFixture()
        val otherDate = "2026-09-29"
        val bodies =
            listOf(
                adjustmentBody(adjustmentReview(f)),
                adjustmentBody(adjustmentReview(f, otherDate), otherDate),
            )
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val results =
            Executors.newFixedThreadPool(2).use { pool ->
                val calls =
                    bodies.map { body ->
                        pool.submit<java.net.http.HttpResponse<String>> {
                            ready.countDown()
                            check(start.await(5, TimeUnit.SECONDS))
                            adjust(f, body)
                        }
                    }
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                start.countDown()
                calls.map { it.get(15, TimeUnit.SECONDS) }
            }
        assertEquals(listOf(200, 409), results.map { it.statusCode() }.sorted())
        val missed = bodies[results.indexOfFirst { it.statusCode() == 409 }]["workDate"] as String
        val second = adjusted(f, adjustmentBody(adjustmentReview(f, missed), missed))
        val details =
            json.readTree(get(f.admin, "${adjustmentPath(f)}/${second["id"].asString()}").body())
        assertEquals(
            listOf(420, 420),
            details["snapshot"]["days"].toList().takeLast(2).map { it["acceptedMinutes"].asInt() },
        )
    }

    @Test
    fun duplicateDeliveryAndRecoveryAfterPermissionRemovalRetainOneCorrection() {
        val f = closedFixture()
        val body = adjustmentBody(adjustmentReview(f))
        val key = UUID.randomUUID()
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val results =
            Executors.newFixedThreadPool(2).use { pool ->
                val calls =
                    List(2) {
                        pool.submit<java.net.http.HttpResponse<String>> {
                            ready.countDown()
                            check(start.await(5, TimeUnit.SECONDS))
                            adjust(f, body, key)
                        }
                    }
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                start.countDown()
                calls.map { it.get(15, TimeUnit.SECONDS) }
            }
        assertTrue(results.all { it.statusCode() == 200 }, results.map { it.body() }.toString())
        assertEquals(results[0].body(), results[1].body())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from attendance_adjustments where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        val before = adjustmentState(f)
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='attendance.correct'",
                f.company,
                f.actor.accountId,
            )
        assertEquals(403, adjust(f, body, key).statusCode())
        assertEquals(before, adjustmentState(f))
    }

    @Test
    fun originalCapturingAccountCannotApproveItsOwnEvidenceAfterOwnershipChanges() {
        val f = closedFixture()
        val original = capture(f)
        database()
            .update(
                """insert into attendance_events(company_id,id,employment_id,account_id,work_date,kind,captured_at,received_at,device_id,offline,mocked,schedule,initial_status,issues,closing_job_id)
            select company_id,?,employment_id,?,work_date,kind,captured_at,received_at,device_id,offline,mocked,schedule,initial_status,issues,closing_job_id
            from attendance_events where company_id=? and id=?""",
                UUID.randomUUID(),
                f.actor.accountId,
                f.company,
                original,
            )
        val review = adjustmentReview(f)
        assertFalse(review["canReviewEvidence"].asBoolean())
        val before = adjustmentState(f)
        assertEquals(
            "attendance_review_denied",
            json.readTree(adjust(f, adjustmentBody(review)).body())["code"].asString(),
        )
        assertEquals(before, adjustmentState(f))
    }

    @Test
    fun thirtyTwoEvidenceDecisionsCommitAsOneBoundedCorrection() {
        val f = closedFixture()
        repeat(32) { capture(f) }
        val body = adjustmentBody(adjustmentReview(f))
        adjusted(f, body)
        val review = adjustmentReview(f)
        assertEquals(32, review["attendance"]["entries"].size())
        assertEquals(0, review["attendance"]["pendingCount"].asInt())
        assertEquals(420, review["attendance"]["acceptedMinutes"].asInt())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from attendance_adjustments where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }
}
