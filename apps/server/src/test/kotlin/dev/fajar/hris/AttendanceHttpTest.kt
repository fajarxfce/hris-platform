package dev.fajar.hris

import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import tools.jackson.databind.JsonNode

@Import(TestClockConfiguration::class)
class AttendanceHttpTest : ApiIntegrationTest() {
    @Autowired private lateinit var clock: MutableTestClock

    private data class Fixture(
        val company: UUID,
        val employee: UUID,
        val account: UUID,
        val device: UUID,
        val admin: HttpClient,
        val adminCsrf: String,
        val worker: HttpClient,
        val workerCsrf: String,
    )

    private fun fixture(): Fixture {
        clock.set(Instant.parse("2026-10-01T15:00:00Z"))
        val admin = client()
        val csrf = login(admin)
        val companyResponse =
            command(
                admin,
                "/api/v1/companies",
                json.writeValueAsString(
                    mapOf(
                        "code" to "A${UUID.randomUUID().toString().take(8)}",
                        "name" to "Attendance Test",
                        "timezone" to "Asia/Jakarta",
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, companyResponse.statusCode(), companyResponse.body())
        val company = UUID.fromString(json.readTree(companyResponse.body()).get("id").asString())
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Example Employee',password_hash from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'attendance.self.record')",
                company,
                account,
            )
        val employee = UUID.randomUUID()
        val create =
            command(
                admin,
                "/api/v1/companies/$company/employees",
                json.writeValueAsString(
                    mapOf(
                        "id" to employee,
                        "employeeNumber" to "E001",
                        "person" to
                            mapOf(
                                "id" to UUID.randomUUID(),
                                "accountId" to account,
                                "legalName" to "Example Employee",
                                "nationality" to "ID",
                            ),
                        "terms" to
                            mapOf(
                                "effectiveFrom" to "2026-01-01",
                                "startDate" to "2026-01-01",
                                "contract" to "PERMANENT",
                                "status" to "ACTIVE",
                            ),
                        "reason" to "Onboarding",
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, create.statusCode(), create.body())
        val shift = UUID.randomUUID()
        val createdShift =
            command(
                admin,
                "/api/v1/companies/$company/workforce/shifts/$shift",
                json.writeValueAsString(
                    mapOf(
                        "code" to "NIGHT",
                        "name" to "Night",
                        "startsAt" to "22:00",
                        "endsAt" to "06:00",
                        "breakMinutes" to 30,
                        "timezone" to "Asia/Jakarta",
                        "mode" to "REMOTE",
                        "reason" to "Initial shift",
                    )
                ),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, createdShift.statusCode(), createdShift.body())
        val assigned =
            command(
                admin,
                "/api/v1/companies/$company/workforce/employees/$employee/schedule",
                json.writeValueAsString(
                    mapOf(
                        "effectiveFrom" to "2026-01-01",
                        "days" to
                            java.time.DayOfWeek.entries.associate {
                                it.name to mapOf("id" to shift, "version" to 0)
                            },
                        "reason" to "Initial schedule",
                    )
                ),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, assigned.statusCode(), assigned.body())
        val worker = client()
        val workerCsrf = login(worker, "$account@example.test")
        return Fixture(
            company,
            employee,
            account,
            UUID.randomUUID(),
            admin,
            csrf,
            worker,
            workerCsrf,
        )
    }

    private fun window(f: Fixture, key: UUID = UUID.randomUUID()): UUID {
        val response =
            command(
                f.worker,
                "/api/v1/companies/${f.company}/workforce/employees/${f.employee}/attendance/windows",
                json.writeValueAsString(mapOf("deviceId" to f.device)),
                f.workerCsrf,
                key,
            )
        assertEquals(200, response.statusCode(), response.body())
        return UUID.fromString(json.readTree(response.body()).get("id").asString())
    }

    private fun capture(
        f: Fixture,
        id: UUID,
        kind: String,
        at: String,
        window: UUID?,
        offline: Boolean,
        device: UUID = f.device,
    ): String =
        json.writeValueAsString(
            mapOf(
                "id" to id,
                "workDate" to "2026-10-01",
                "kind" to kind,
                "capturedAt" to at,
                "windowId" to window,
                "deviceId" to device,
                "offline" to offline,
            )
        )

    private fun record(f: Fixture, body: String, key: UUID): HttpResponse<String> =
        command(
            f.worker,
            "/api/v1/companies/${f.company}/workforce/employees/${f.employee}/attendance",
            body,
            f.workerCsrf,
            key,
        )

    private fun day(f: Fixture): JsonNode {
        val response =
            get(
                f.worker,
                "/api/v1/companies/${f.company}/workforce/employees/${f.employee}/attendance?from=2026-10-01&until=2026-10-01",
            )
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())[0]
    }

    private fun review(f: Fixture, id: UUID, key: UUID = UUID.randomUUID()): HttpResponse<String> =
        command(
            f.admin,
            "/api/v1/companies/${f.company}/workforce/attendance/$id/review",
            """{"version":0,"decision":"ACCEPT","reason":"Supervisor verification"}""",
            f.adminCsrf,
            key,
        )

    @Test
    fun OnlineOvernightEventsKeepTheirSnapshotAndLostResponseReplay() {
        val f = fixture()
        val first = UUID.randomUUID()
        val key = UUID.randomUUID()
        val body = capture(f, first, "CLOCK_IN", "2026-10-01T15:00:00Z", window(f), false)
        val recorded = record(f, body, key)
        assertEquals(200, recorded.statusCode(), recorded.body())
        assertEquals(recorded.body(), record(f, body, key).body())
        assertEquals("ACCEPTED", day(f).get("entries")[0].get("status").asString())
        val off =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/workforce/employees/${f.employee}/roster/2026-10-01",
                """{"reason":"Roster changed after capture"}""",
                f.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, off.statusCode(), off.body())
        clock.set(Instant.parse("2026-10-01T23:00:00Z"))
        val out =
            record(
                f,
                capture(
                    f,
                    UUID.randomUUID(),
                    "CLOCK_OUT",
                    "2026-10-01T23:00:00Z",
                    window(f),
                    false,
                ),
                UUID.randomUUID(),
            )
        assertEquals(200, out.statusCode(), out.body())
        val summary = day(f)
        assertEquals(450, summary.get("acceptedMinutes").asLong())
        assertEquals(0, summary.get("pendingCount").asInt())
        assertEquals("WORK", summary.get("entries")[1].get("schedule").get("kind").asString())
        clock.set(Instant.parse("2026-11-05T15:00:00Z"))
        assertEquals(recorded.body(), record(f, body, key).body())
        assertEquals(409, record(f, body.replace("CLOCK_IN", "CLOCK_OUT"), key).statusCode())
        assertThrows(DataAccessException::class.java) {
            database().update("update attendance_events set captured_at=now() where id=?", first)
        }
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from attendance_events where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun OfflineAndExpiredEventsRequireExactlyOneIndependentReview() {
        val f = fixture()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val offline =
            record(
                f,
                capture(f, first, "CLOCK_IN", "2026-10-01T15:00:00Z", window(f), true),
                UUID.randomUUID(),
            )
        assertEquals(200, offline.statusCode(), offline.body())
        clock.set(Instant.parse("2026-10-01T23:00:00Z"))
        val expired = window(f)
        clock.set(Instant.parse("2026-10-01T23:03:00Z"))
        val out =
            record(
                f,
                capture(f, second, "CLOCK_OUT", "2026-10-01T23:00:00Z", expired, false),
                UUID.randomUUID(),
            )
        assertEquals(200, out.statusCode(), out.body())
        assertEquals(2, day(f).get("pendingCount").asInt())
        assertEquals(0, day(f).get("acceptedMinutes").asLong())
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'attendance.verify')",
                f.company,
                f.account,
            )
        val own =
            command(
                f.worker,
                "/api/v1/companies/${f.company}/workforce/attendance/$first/review",
                """{"version":0,"decision":"ACCEPT","reason":"Self review"}""",
                f.workerCsrf,
                UUID.randomUUID(),
            )
        assertEquals(403, own.statusCode(), own.body())
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val futures =
                (1..2).map {
                    pool.submit<Int> {
                        ready.countDown()
                        assertTrue(start.await(5, TimeUnit.SECONDS))
                        review(f, first).statusCode()
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            assertEquals(listOf(200, 409), futures.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        val key = UUID.randomUUID()
        val reviewed = review(f, second, key)
        assertEquals(200, reviewed.statusCode(), reviewed.body())
        assertEquals(reviewed.body(), review(f, second, key).body())
        assertEquals(450, day(f).get("acceptedMinutes").asLong())
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from attendance_reviews where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where company_id=? and action='attendance.reviewed'",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun FailedCaptureDoesNotConsumeProofAndAccessRevocationStopsNewWork() {
        val f = fixture()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val proof = window(f)
        val bad =
            record(
                f,
                capture(f, id, "CLOCK_IN", "2026-10-01T15:00:00Z", proof, false, UUID.randomUUID()),
                key,
            )
        assertEquals(422, bad.statusCode(), bad.body())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from attendance_events where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        val valid = record(f, capture(f, id, "CLOCK_IN", "2026-10-01T15:00:00Z", proof, false), key)
        assertEquals(200, valid.statusCode(), valid.body())
        val reused =
            record(
                f,
                capture(f, UUID.randomUUID(), "CLOCK_IN", "2026-10-01T15:00:00Z", proof, false),
                UUID.randomUUID(),
            )
        assertEquals(409, reused.statusCode(), reused.body())
        assertEquals("capture_window_consumed", json.readTree(reused.body()).get("code").asString())
        val unverified =
            record(
                f,
                capture(f, UUID.randomUUID(), "CLOCK_OUT", "2026-10-01T15:00:10Z", null, false),
                UUID.randomUUID(),
            )
        assertEquals(200, unverified.statusCode(), unverified.body())
        assertEquals(1, day(f).get("pendingCount").asInt())
        val foreign =
            command(
                f.worker,
                "/api/v1/companies/${f.company}/workforce/employees/${UUID.randomUUID()}/attendance/windows",
                json.writeValueAsString(mapOf("deviceId" to f.device)),
                f.workerCsrf,
                UUID.randomUUID(),
            )
        assertEquals(404, foreign.statusCode(), foreign.body())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='attendance.self.record'",
                f.company,
                f.account,
            )
        assertEquals(
            403,
            record(
                    f,
                    capture(f, UUID.randomUUID(), "CLOCK_OUT", "2026-10-01T15:00:20Z", null, true),
                    UUID.randomUUID(),
                )
                .statusCode(),
        )
    }
}
