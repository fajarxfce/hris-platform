package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.LeavePolicyRepository
import dev.fajar.hris.leave.domain.usecases.SubmitLeaveRequest
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import java.net.http.HttpResponse
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import tools.jackson.databind.JsonNode

@Import(AccountLockProbeConfiguration::class)
class LeavePreviewSubmissionHttpTest : LeaveApiFixture() {
    @Autowired private lateinit var accounts: AccountLockProbe
    @Autowired private lateinit var policies: LeavePolicyRepository
    @Autowired private lateinit var schedules: ScheduleRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var submission: SubmitLeaveRequest

    @AfterEach
    fun releaseProbe() {
        accounts.current.getAndSet(null)?.release?.countDown()
    }

    @Test
    fun unchangedPreviewCanBeSubmittedAndItsReceiptReplaysAfterTheFactsChange() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        assertEquals(200, adjust(f, "4").statusCode())
        val days = listOf("2026-10-05" to "FIRST_HALF", "2026-10-10" to "FULL")
        val original = preview(f, days)
        val revision = original["revision"].asString()
        assertTrue(revision.matches(Regex("[0-9a-f]{64}")))
        assertEquals(revision, preview(f, days.reversed())["revision"].asString())
        val id = UUID.randomUUID()
        val operation = UUID.randomUUID()
        val accepted = submit(f, id, days, operation, previewRevision = revision)
        assertEquals(200, accepted.statusCode(), accepted.body())
        assertEquals("0.5", balance(f)["reservedDays"].asString())
        assertEquals(200, policy(f, version = 0, paid = false).statusCode())
        assertEquals(200, action(f, id, "withdraw", 0).statusCode())
        val replay = submit(f, id, days, operation, previewRevision = revision)
        assertEquals(200, replay.statusCode(), replay.body())
        assertEquals(json.readTree(accepted.body()), json.readTree(replay.body()))
        assertEquals("0", balance(f)["reservedDays"].asString())
        assertEquals(1, requestCount(f))
        val changedPayload = submit(f, id, days, operation, previewRevision = "0".repeat(64))
        assertEquals(409, changedPayload.statusCode(), changedPayload.body())
    }

    @ParameterizedTest
    @ValueSource(strings = ["balance", "policy", "calendar"])
    fun changedPreparationIsRejectedWithoutEffectsAndTheFailedOperationCanBeReviewedAgain(
        change: String
    ) {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        assertEquals(200, adjust(f, "4").statusCode())
        val days = listOf("2026-10-05" to "FULL", "2026-10-06" to "FULL")
        val revision = preview(f, days)["revision"].asString()
        val changeResult =
            when (change) {
                "balance" -> adjust(f, "1")
                "policy" -> policy(f, version = 0, paid = false)
                else -> dayOff(f)
            }
        assertEquals(200, changeResult.statusCode(), changeResult.body())
        val before = balance(f).toString()
        val id = UUID.randomUUID()
        val operation = UUID.randomUUID()
        val stale = submit(f, id, days, operation, previewRevision = revision)
        assertEquals(409, stale.statusCode(), stale.body())
        assertEquals("leave_preview_changed", json.readTree(stale.body())["code"].asString())
        assertEquals(0, requestCount(f))
        assertEquals(before, balance(f).toString())
        val fresh = preview(f, days)
        assertNotEquals(revision, fresh["revision"].asString())
        val accepted =
            submit(f, id, days, operation, previewRevision = fresh["revision"].asString())
        assertEquals(200, accepted.statusCode(), accepted.body())
        assertEquals(fresh["chargedDays"].asString(), balance(f)["reservedDays"].asString())
        assertEquals(1, requestCount(f))
    }

    @Test
    fun alteredExcludedDatesAndMalformedRevisionsCannotBeConfirmed() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        assertEquals(200, adjust(f, "4").statusCode())
        val days = listOf("2026-10-05" to "FULL", "2026-10-10" to "FULL")
        val revision = preview(f, days)["revision"].asString()
        val altered = submit(f, UUID.randomUUID(), days.take(1), previewRevision = revision)
        assertEquals(409, altered.statusCode(), altered.body())
        assertEquals("leave_preview_changed", json.readTree(altered.body())["code"].asString())
        for (invalid in listOf("", "../other", "A".repeat(64), "a".repeat(65))) {
            val rejected = submit(f, UUID.randomUUID(), days, previewRevision = invalid)
            assertEquals(422, rejected.statusCode(), rejected.body())
            assertEquals("invalid_leave_preview", json.readTree(rejected.body())["code"].asString())
        }
        assertEquals(0, requestCount(f))
        assertEquals("0", balance(f)["reservedDays"].asString())
    }

    @Test
    fun competingConfirmationsCannotReserveTheSameDatesTwice() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        assertEquals(200, adjust(f, "4").statusCode())
        val days = listOf("2026-10-05" to "FULL")
        val revision = preview(f, days)["revision"].asString()
        Executors.newFixedThreadPool(2).use { pool ->
            val commands = List(2) { UUID.randomUUID() to UUID.randomUUID() }
            val pending =
                commands.map { (id, key) ->
                    pool.submit<HttpResponse<String>> {
                        submit(f, id, days, key, previewRevision = revision)
                    }
                }
            val results = pending.map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(listOf(200, 409), results.map { it.statusCode() }.sorted())
            assertEquals(1, requestCount(f))
            assertEquals("1", balance(f)["reservedDays"].asString())
            val winner = results.indexOfFirst { it.statusCode() == 200 }
            val (id, key) = commands[winner]
            assertEquals(200, submit(f, id, days, key, previewRevision = revision).statusCode())
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["policy", "calendar"])
    fun confirmationHoldsItsSourceGuardsUntilTheReviewedFactsCommit(source: String) {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        assertEquals(200, adjust(f, "4").statusCode())
        val days = listOf("2026-10-05" to "FULL")
        val revision = preview(f, days)["revision"].asString()
        val barrier = AccountLockProbe.Barrier(f.account, occurrence = 2)
        accounts.current.set(barrier)
        Executors.newFixedThreadPool(2).use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> {
                    submit(f, UUID.randomUUID(), days, previewRevision = revision)
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val writer =
                    pool.submit<Result<Unit>> {
                        transactions.run(actor(f)) {
                            if (source == "policy") policies.lock(f.company)
                            else schedules.lock(f.company)
                        }
                    }
                awaitSourceWriter(
                    (if (source == "policy") "leave-policy:" else "calendar:") + f.company,
                    writer,
                )
                barrier.release.countDown()
                val response = pending.get(10, TimeUnit.SECONDS)
                assertEquals(200, response.statusCode(), response.body())
                assertInstanceOf(Result.Success::class.java, writer.get(10, TimeUnit.SECONDS))
                assertEquals(1, requestCount(f))
            } finally {
                barrier.release.countDown()
            }
        }
    }

    @Test
    fun cancellationDuringConfirmationReleasesTheGuardsAndRollsBackTheOperation() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        assertEquals(200, adjust(f, "4").statusCode())
        val days = listOf("2026-10-05" to "FULL")
        val revision = preview(f, days)["revision"].asString()
        val id = UUID.randomUUID()
        val operation = UUID.randomUUID()
        val barrier = AccountLockProbe.Barrier(f.account)
        accounts.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<*>> {
                    submission.execute(
                        actor(f),
                        operation,
                        id,
                        f.employee,
                        f.type,
                        listOf(RequestedLeaveDay(LocalDate.parse("2026-10-05"), LeavePortion.FULL)),
                        "Personal leave",
                        previewRevision = revision,
                    )
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                pool.shutdown()
                assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
            } finally {
                barrier.release.countDown()
                accounts.current.set(null)
            }
        }
        assertEquals(0, requestCount(f))
        assertEquals("0", balance(f)["reservedDays"].asString())
        val retried = submit(f, id, days, operation, previewRevision = revision)
        assertEquals(200, retried.statusCode(), retried.body())
        assertEquals(1, requestCount(f))
    }

    private fun actor(f: LeaveFixture) =
        Actor(
            f.account,
            f.company,
            setOf("leave.self.manage"),
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = 0,
        )

    private fun requestCount(f: LeaveFixture) =
        database()
            .queryForObject(
                "select count(*) from leave_requests where company_id=?",
                Int::class.java,
                f.company,
            )

    private fun preview(f: LeaveFixture, days: List<Pair<String, String>>): JsonNode {
        val response =
            command(
                f.worker,
                "/api/v1/companies/${f.company}/leave/me/requests/preview",
                json.writeValueAsString(
                    mapOf(
                        "employeeId" to f.employee,
                        "typeId" to f.type,
                        "days" to days.map { mapOf("workDate" to it.first, "portion" to it.second) },
                    )
                ),
                f.workerCsrf,
                UUID.randomUUID(),
            )
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    private fun dayOff(f: LeaveFixture) =
        command(
            f.admin,
            "/api/v1/companies/${f.company}/workforce/employees/${f.employee}/roster/2026-10-06",
            """{"reason":"Work calendar adjustment"}""",
            f.adminCsrf,
            UUID.randomUUID(),
            "PUT",
        )

    private fun awaitSourceWriter(key: String, writer: Future<*>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
        while (System.nanoTime() < deadline) {
            val waiting =
                database()
                    .queryForObject(
                        """select exists(select 1 from pg_locks
                where locktype='advisory' and mode='ExclusiveLock' and not granted
                and ((classid::bigint << 32) | objid::bigint) = hashtextextended(?,0))""",
                        Boolean::class.java,
                        key,
                    )
            if (waiting == true) return
            if (writer.isDone)
                fail<Unit>("The source guard was released before submission completed")
            Thread.sleep(10)
        }
        fail<Unit>("The source writer did not reach its conflicting guard")
    }
}
