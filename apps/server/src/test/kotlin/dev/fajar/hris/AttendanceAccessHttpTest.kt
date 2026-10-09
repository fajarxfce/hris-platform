package dev.fajar.hris

import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(PeopleLockProbeConfiguration::class, AttendanceReadProbeConfiguration::class)
class AttendanceAccessHttpTest : WorkforceAccessApiFixture() {
    @Autowired private lateinit var peopleProbe: PeopleLockProbe
    @Autowired private lateinit var readProbe: AttendanceReadProbe

    private fun path(f: Fixture) = "${f.path}/employees/${f.employee}/attendance"

    private fun days(f: Fixture) = "${path(f)}?from=2026-10-01&until=2026-10-01"

    private fun capture(
        id: UUID,
        device: UUID = UUID.randomUUID(),
        window: UUID? = null,
        at: Instant = clock.instant(),
        date: String = "2026-10-01",
    ) =
        json.writeValueAsString(
            mapOf(
                "id" to id,
                "deviceId" to device,
                "windowId" to window,
                "workDate" to date,
                "capturedAt" to at,
                "kind" to "CLOCK_IN",
                "offline" to (window == null),
            )
        )

    private fun recordPending(f: Fixture): UUID {
        val id = UUID.randomUUID()
        val response =
            command(f.employeeClient, path(f), capture(id), f.employeeCsrf, UUID.randomUUID())
        assertEquals(200, response.statusCode(), response.body())
        return id
    }

    private fun review(f: Fixture, id: UUID, key: UUID) =
        command(
            f.admin,
            "${f.path}/attendance/$id/review",
            """{"version":0,"decision":"REJECT","reason":"Independent review"}""",
            f.csrf,
            key,
        )

    private fun revision(
        f: Fixture,
        version: Long,
        status: String = "ACTIVE",
        manager: UUID? = null,
    ) =
        command(
            f.admin,
            "/api/v1/companies/${f.company}/employees/${f.employee}/revisions",
            json.writeValueAsString(
                mapOf(
                    "version" to version,
                    "terms" to
                        mapOf(
                            "effectiveFrom" to "2026-10-01",
                            "startDate" to "2026-01-01",
                            "contract" to "PERMANENT",
                            "status" to status,
                            "managerId" to manager,
                        ),
                    "reason" to "Employment change",
                )
            ),
            f.csrf,
            UUID.randomUUID(),
        )

    @Test
    fun attendanceCommandsRecheckAccessBeforeEffectsAndReceiptReplay() {
        for (mode in listOf("window", "capture", "review", "correction")) {
            val f = isolatedFixture()
            val key = UUID.randomUUID()
            val self = mode in setOf("window", "capture")
            val account = if (self) f.employeeAccount else f.actor.accountId
            val permission =
                when (mode) {
                    "review" -> "attendance.verify"
                    "correction" -> "attendance.correct"
                    else -> "attendance.self.record"
                }
            val id = if (mode == "review") recordPending(f) else UUID.randomUUID()
            val body =
                when (mode) {
                    "window" -> json.writeValueAsString(mapOf("deviceId" to UUID.randomUUID()))
                    "capture" -> capture(id)
                    else ->
                        """{"workDate":"2026-10-01","breakMinutes":0,"reason":"Verified absence"}"""
                }
            val request =
                when (mode) {
                    "window" -> {
                        {
                            command(
                                f.employeeClient,
                                "${path(f)}/windows",
                                body,
                                f.employeeCsrf,
                                key,
                            )
                        }
                    }
                    "capture" -> {
                        { command(f.employeeClient, path(f), body, f.employeeCsrf, key) }
                    }
                    "review" -> {
                        { review(f, id, key) }
                    }
                    else -> {
                        { command(f.admin, "${path(f)}/corrections", body, f.csrf, key) }
                    }
                }
            val denied =
                waiting(
                    account,
                    {
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                                f.company,
                                account,
                                permission,
                            )
                    },
                    request,
                )
            assertEquals(403, denied.statusCode(), "$mode ${denied.body()}")
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    f.company,
                    account,
                    permission,
                )
            val saved = request()
            assertEquals(200, saved.statusCode(), "$mode ${saved.body()}")
            assertEquals(saved.body(), request().body())
            val revoked =
                waiting(
                    account,
                    {
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                account,
                            )
                    },
                    request,
                )
            assertEquals(401, revoked.statusCode(), "$mode ${revoked.body()}")
        }
    }

    @Test
    fun attendanceAndCorrectionReadsRecheckTheirCurrentScope() {
        for (mode in listOf("days", "history", "membership", "company", "credentials")) {
            val f = isolatedFixture()
            val read =
                if (mode == "history") "${path(f)}/corrections?workDate=2026-10-01" else days(f)
            val denied =
                waiting(
                    f.actor.accountId,
                    {
                        when (mode) {
                            "membership" ->
                                database()
                                    .update(
                                        "update company_memberships set active=false where company_id=? and account_id=?",
                                        f.company,
                                        f.actor.accountId,
                                    )
                            "company" ->
                                database()
                                    .update(
                                        "update companies set active=false where id=?",
                                        f.company,
                                    )
                            "credentials" ->
                                database()
                                    .update(
                                        "update accounts set security_version=security_version+1 where id=?",
                                        f.actor.accountId,
                                    )
                            else ->
                                database()
                                    .update(
                                        "delete from membership_permissions where company_id=? and account_id=? and permission in ('workforce.read','attendance.verify')",
                                        f.company,
                                        f.actor.accountId,
                                    )
                        }
                    },
                ) {
                    get(f.admin, read)
                }
            assertEquals(
                when (mode) {
                    "days",
                    "history" -> 404
                    "credentials" -> 401
                    else -> 403
                },
                denied.statusCode(),
                "$mode ${denied.body()}",
            )
        }
    }

    @Test
    fun newlyGrantedCompanyReviewDoesNotExpandAPendingTeamReview() {
        val f = isolatedFixture()
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'attendance.team.verify')",
                f.company,
                f.actor.accountId,
            )
        val id = recordPending(f)
        val key = UUID.randomUUID()
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='attendance.verify'",
                f.company,
                f.actor.accountId,
            )
        val pending =
            waiting(
                f.actor.accountId,
                {
                    database()
                        .update(
                            "insert into membership_permissions(company_id,account_id,permission) values(?,?,'attendance.verify')",
                            f.company,
                            f.actor.accountId,
                        )
                },
            ) {
                review(f, id, key)
            }
        assertEquals(403, pending.statusCode(), pending.body())
        assertEquals("attendance_review_denied", json.readTree(pending.body())["code"].asString())
        val fresh = review(f, id, key)
        assertEquals(200, fresh.statusCode(), fresh.body())
    }

    @Test
    fun proofThatExpiresWhileWaitingCannotProduceAcceptedAttendance() {
        val f = isolatedFixture()
        val shift = createShift(f)
        val roster =
            command(
                f.admin,
                "${f.path}/employees/${f.employee}/roster/2026-10-01",
                json.writeValueAsString(
                    mapOf(
                        "shift" to mapOf("id" to shift, "version" to 0),
                        "reason" to "Office coverage",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, roster.statusCode(), roster.body())
        val device = UUID.randomUUID()
        val window =
            command(
                f.employeeClient,
                "${path(f)}/windows",
                json.writeValueAsString(mapOf("deviceId" to device)),
                f.employeeCsrf,
                UUID.randomUUID(),
            )
        assertEquals(200, window.statusCode(), window.body())
        val id = UUID.randomUUID()
        val body =
            capture(id, device, UUID.fromString(json.readTree(window.body())["id"].asString()))
        val response =
            waiting(f.employeeAccount, { clock.set(clock.instant().plusSeconds(121)) }) {
                command(f.employeeClient, path(f), body, f.employeeCsrf, UUID.randomUUID())
            }
        assertEquals(200, response.statusCode(), response.body())
        val entry = json.readTree(get(f.employeeClient, days(f)).body())[0]["entries"][0]
        assertEquals("PENDING", entry["status"].asString())
        assertEquals(
            listOf("WINDOW_EXPIRED"),
            entry["issues"].iterator().asSequence().map { it.asString() }.toList(),
        )
        assertEquals(clock.instant(), Instant.parse(entry["receivedAt"].asString()))
    }

    @Test
    fun offlineHistoryWindowIsRecheckedAfterWaitingWithoutConsumingTheOperation() {
        val f = isolatedFixture()
        val originalTime = clock.instant()
        val body =
            capture(
                UUID.randomUUID(),
                at = originalTime.minusSeconds(31 * 86400L - 30),
                date = "2026-08-31",
            )
        val key = UUID.randomUUID()
        val request = { command(f.employeeClient, path(f), body, f.employeeCsrf, key) }
        val expired =
            waiting(f.employeeAccount, { clock.set(originalTime.plusSeconds(31)) }, request)
        assertEquals(422, expired.statusCode(), expired.body())
        assertEquals("invalid_capture_time", json.readTree(expired.body())["code"].asString())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from attendance_events where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        clock.set(originalTime)
        val restored = request()
        assertEquals(200, restored.statusCode(), restored.body())
    }

    @Test
    fun captureWindowsUseCurrentTimeAndReplayNeverExtendsTheirExpiry() {
        val f = isolatedFixture()
        val key = UUID.randomUUID()
        val body = json.writeValueAsString(mapOf("deviceId" to UUID.randomUUID()))
        val request = { command(f.employeeClient, "${path(f)}/windows", body, f.employeeCsrf, key) }
        val issued =
            waiting(f.employeeAccount, { clock.set(clock.instant().plusSeconds(61)) }, request)
        assertEquals(200, issued.statusCode(), issued.body())
        val result = json.readTree(issued.body())
        assertEquals(clock.instant(), Instant.parse(result["issuedAt"].asString()))
        assertEquals(
            clock.instant().plusSeconds(120),
            Instant.parse(result["expiresAt"].asString()),
        )
        clock.set(clock.instant().plusSeconds(121))
        val replay = request()
        assertEquals(200, replay.statusCode(), replay.body())
        assertEquals(issued.body(), replay.body())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from attendance_capture_windows where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun attendanceAggregateHoldsSharedDayGuardsAcrossEventsAndCorrections() {
        val f = isolatedFixture()
        val read = "${path(f)}?from=2026-09-01&until=2026-10-01"
        val barrier = AttendanceReadProbe.Barrier(f.company)
        readProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<String>> { get(f.admin, read) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val concurrent = get(f.employeeClient, read)
                assertEquals(200, concurrent.statusCode(), concurrent.body())
                for (date in listOf("2026-09-01", "2026-10-01")) {
                    assertEquals(
                        false,
                        database()
                            .queryForObject(
                                "select pg_try_advisory_xact_lock(hashtextextended(?,0))",
                                Boolean::class.java,
                                "attendance:${f.company}:${f.employee}:$date",
                            ),
                    )
                }
                barrier.release.countDown()
                val result = pending.get(10, TimeUnit.SECONDS)
                assertEquals(200, result.statusCode(), result.body())
                assertEquals(concurrent.body(), result.body())
                assertEquals(31, json.readTree(result.body()).size())
            } finally {
                barrier.release.countDown()
                readProbe.current.set(null)
            }
        }
        val corrected =
            command(
                f.admin,
                "${path(f)}/corrections",
                """{"workDate":"2026-10-01","breakMinutes":0,"reason":"Verified absence"}""",
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, corrected.statusCode(), corrected.body())
        assertFalse(json.readTree(get(f.admin, days(f)).body())[0]["correction"].isNull)
    }

    @Test
    fun pendingCaptureAndWindowIssuanceRecheckEmploymentStatus() {
        for (mode in listOf("capture", "window")) {
            val f = isolatedFixture()
            val body =
                if (mode == "capture") capture(UUID.randomUUID())
                else json.writeValueAsString(mapOf("deviceId" to UUID.randomUUID()))
            val barrier = PeopleLockProbe.Barrier(f.company)
            peopleProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<HttpResponse<String>> {
                        command(
                            f.employeeClient,
                            path(f) + if (mode == "window") "/windows" else "",
                            body,
                            f.employeeCsrf,
                            UUID.randomUUID(),
                        )
                    }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    val changed = revision(f, 0, "SUSPENDED")
                    assertEquals(200, changed.statusCode(), changed.body())
                    barrier.release.countDown()
                    val result = pending.get(10, TimeUnit.SECONDS)
                    assertEquals(404, result.statusCode(), "$mode ${result.body()}")
                    assertEquals(
                        0,
                        database()
                            .queryForObject(
                                "select count(*) from attendance_events where company_id=?",
                                Int::class.java,
                                f.company,
                            ),
                    )
                    assertEquals(
                        0,
                        database()
                            .queryForObject(
                                "select count(*) from attendance_capture_windows where company_id=?",
                                Int::class.java,
                                f.company,
                            ),
                    )
                } finally {
                    barrier.release.countDown()
                    peopleProbe.current.set(null)
                }
            }
        }
    }

    @Test
    fun aManagerWhoLosesTheReportingAssignmentCannotFinishAPendingReview() {
        val f = isolatedFixture()
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'attendance.team.verify')",
                f.company,
                f.actor.accountId,
            )
        val manager = anotherEmployee(f, f.actor.accountId)
        val assigned = revision(f, 0, manager = manager)
        assertEquals(200, assigned.statusCode(), assigned.body())
        val id = recordPending(f)
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='attendance.verify'",
                f.company,
                f.actor.accountId,
            )
        val barrier = PeopleLockProbe.Barrier(f.company)
        peopleProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<String>> { review(f, id, UUID.randomUUID()) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val changed = revision(f, 1)
                assertEquals(200, changed.statusCode(), changed.body())
                barrier.release.countDown()
                val result = pending.get(10, TimeUnit.SECONDS)
                assertEquals(403, result.statusCode(), result.body())
                assertEquals(
                    "attendance_review_denied",
                    json.readTree(result.body())["code"].asString(),
                )
                assertEquals(
                    0,
                    database()
                        .queryForObject(
                            "select count(*) from attendance_reviews where company_id=?",
                            Int::class.java,
                            f.company,
                        ),
                )
            } finally {
                barrier.release.countDown()
                peopleProbe.current.set(null)
            }
        }
    }
}
