package dev.fajar.hris

import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(ScheduleLockProbeConfiguration::class)
class WorkCalendarAccessHttpTest : WorkforceAccessApiFixture() {
    @Autowired private lateinit var scheduleProbe: ScheduleLockProbe

    private fun calendarPath(f: Fixture, employee: UUID = f.employee) =
        "${f.path}/employees/$employee/calendar?from=2026-10-01&until=2026-10-01"

    private fun scheduleBody(shift: UUID) =
        json.writeValueAsString(
            mapOf(
                "effectiveFrom" to "2026-10-01",
                "days" to mapOf("THURSDAY" to mapOf("id" to shift, "version" to 0)),
                "reason" to "Schedule assignment",
            )
        )

    @Test
    fun scheduleCommandsRevalidateBeforeWritingAndReplayingReceipts() {
        for (mode in listOf("shift", "holiday", "weekly", "roster")) {
            val f = isolatedFixture()
            val shift = createShift(f)
            val id = UUID.randomUUID()
            val key = UUID.randomUUID()
            val path =
                when (mode) {
                    "shift" -> "${f.path}/shifts/$id"
                    "holiday" -> "${f.path}/holidays/$id"
                    "weekly" -> "${f.path}/employees/${f.employee}/schedule"
                    else -> "${f.path}/employees/${f.employee}/roster/2026-10-01"
                }
            val body =
                when (mode) {
                    "shift" -> shiftBody(id)
                    "holiday" ->
                        """{"workDate":"2026-10-01","name":"Company holiday","reason":"Calendar setup"}"""
                    "weekly" -> scheduleBody(shift)
                    else ->
                        json.writeValueAsString(
                            mapOf(
                                "shift" to mapOf("id" to shift, "version" to 0),
                                "reason" to "Coverage",
                            )
                        )
                }
            val request = { command(f.admin, path, body, f.csrf, key, "PUT") }
            val denied =
                waiting(
                    f.actor.accountId,
                    {
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission='workforce.manage'",
                                f.company,
                                f.actor.accountId,
                            )
                    },
                    request,
                )
            assertEquals(403, denied.statusCode(), "$mode ${denied.body()}")
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'workforce.manage')",
                    f.company,
                    f.actor.accountId,
                )
            val saved = request()
            assertEquals(200, saved.statusCode(), "$mode ${saved.body()}")
            assertEquals(saved.body(), request().body())
            val replay =
                waiting(
                    f.actor.accountId,
                    {
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                f.actor.accountId,
                            )
                    },
                    request,
                )
            assertEquals(401, replay.statusCode(), "$mode ${replay.body()}")
        }
    }

    @Test
    fun calendarReadsUseCurrentPermissionsMembershipCompanyAndCredentials() {
        for (mode in
            listOf("shifts", "holidays", "calendar", "membership", "company", "credentials")) {
            val f = isolatedFixture()
            val path =
                when (mode) {
                    "shifts" -> "${f.path}/shifts"
                    "holidays" -> "${f.path}/holidays?from=2026-09-01&until=2026-10-01"
                    else -> calendarPath(f)
                }
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
                                        "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                                        f.company,
                                        f.actor.accountId,
                                        if (mode == "holidays") "company.read" else "workforce.read",
                                    )
                        }
                    },
                ) {
                    get(f.admin, path)
                }
            assertEquals(
                when (mode) {
                    "calendar" -> 404
                    "credentials" -> 401
                    else -> 403
                },
                denied.statusCode(),
                "$mode ${denied.body()}",
            )
        }
    }

    @Test
    fun aPermissionGrantedWhileWaitingCannotExpandAnExistingSelfServiceRequest() {
        val f = isolatedFixture()
        val other = anotherEmployee(f)
        val path = calendarPath(f, other)
        val pending =
            waiting(
                f.employeeAccount,
                {
                    database()
                        .update(
                            "insert into membership_permissions(company_id,account_id,permission) values(?,?,'workforce.read')",
                            f.company,
                            f.employeeAccount,
                        )
                },
            ) {
                get(f.employeeClient, path)
            }
        assertEquals(404, pending.statusCode(), pending.body())
        val fresh = get(f.employeeClient, path)
        assertEquals(200, fresh.statusCode(), fresh.body())
    }

    @Test
    fun calendarReadsShareTheirResourceGuardAndExcludeConcurrentScheduleWrites() {
        val f = isolatedFixture()
        val barrier = AccountLockProbe.Barrier(f.actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val first = pool.submit<HttpResponse<String>> { get(f.admin, calendarPath(f)) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val second = get(f.employeeClient, calendarPath(f))
                assertEquals(200, second.statusCode(), second.body())
                assertEquals(
                    false,
                    database()
                        .queryForObject(
                            "select pg_try_advisory_xact_lock(hashtextextended(?,0))",
                            Boolean::class.java,
                            "calendar:${f.company}",
                        ),
                )
                barrier.release.countDown()
                val response = first.get(10, TimeUnit.SECONDS)
                assertEquals(200, response.statusCode(), response.body())
                assertEquals(second.body(), response.body())
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        val saved =
            command(
                f.admin,
                "${f.path}/employees/${f.employee}/roster/2026-10-01",
                """{"reason":"Planned day off"}""",
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, saved.statusCode(), saved.body())
        assertEquals(
            "ROSTER",
            json.readTree(get(f.admin, calendarPath(f)).body())["days"][0]["origin"].asString(),
        )
    }

    @Test
    fun pendingScheduleChangesRecheckEmploymentEligibility() {
        for (mode in listOf("weekly", "roster")) {
            val f = isolatedFixture()
            val shift = createShift(f)
            val barrier = ScheduleLockProbe.Barrier(f.company)
            scheduleProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<HttpResponse<String>> {
                        if (mode == "weekly")
                            command(
                                f.admin,
                                "${f.path}/employees/${f.employee}/schedule",
                                scheduleBody(shift),
                                f.csrf,
                                UUID.randomUUID(),
                                "PUT",
                            )
                        else
                            command(
                                f.admin,
                                "${f.path}/employees/${f.employee}/roster/2026-10-01",
                                """{"reason":"Planned day off"}""",
                                f.csrf,
                                UUID.randomUUID(),
                                "PUT",
                            )
                    }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    val changed =
                        command(
                            f.admin,
                            "/api/v1/companies/${f.company}/employees/${f.employee}/revisions",
                            json.writeValueAsString(
                                mapOf(
                                    "version" to 0,
                                    "terms" to
                                        mapOf(
                                            "effectiveFrom" to "2026-10-01",
                                            "startDate" to "2026-01-01",
                                            "contract" to "PERMANENT",
                                            "status" to "SUSPENDED",
                                        ),
                                    "reason" to "Employment change",
                                )
                            ),
                            f.csrf,
                            UUID.randomUUID(),
                        )
                    assertEquals(200, changed.statusCode(), changed.body())
                    barrier.release.countDown()
                    val denied = pending.get(10, TimeUnit.SECONDS)
                    assertEquals(422, denied.statusCode(), "$mode ${denied.body()}")
                    assertEquals(
                        "employee_unavailable",
                        json.readTree(denied.body())["code"].asString(),
                    )
                } finally {
                    barrier.release.countDown()
                    scheduleProbe.current.set(null)
                }
            }
        }
    }
}
