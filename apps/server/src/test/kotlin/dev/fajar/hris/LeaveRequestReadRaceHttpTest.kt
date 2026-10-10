package dev.fajar.hris

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.leave.domain.usecases.GetLeaveRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(ApprovalReadProbeConfiguration::class)
class LeaveRequestReadRaceHttpTest : ApprovalApiFixture() {
    @Autowired private lateinit var reads: ApprovalReadProbe
    @Autowired private lateinit var details: GetLeaveRequest

    @Test
    fun cancellingAPendingReadReleasesItsApprovalGuard() {
        val f = leaveFixture()
        val leave = pending(f)
        val approval = approvalId(f.company, leave)
        val next = reviewer(f.company)
        val barrier = ApprovalReadProbe.Barrier(f.company)
        reads.current.set(barrier)
        val actor =
            Actor(
                f.managerAccount,
                f.company,
                setOf("leave.team.read", "leave.team.approve"),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        try {
            Executors.newSingleThreadExecutor().use { executor ->
                val reading = executor.submit<Result<*>> { details.execute(actor, leave, null, 20) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    assertTrue(reading.cancel(true))
                    executor.shutdown()
                    assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
                    val changed = reassign(f, approval, setOf(next.account))
                    assertEquals(200, changed.statusCode(), changed.body())
                } finally {
                    barrier.release.countDown()
                }
            }
        } finally {
            barrier.release.countDown()
            reads.current.set(null)
        }
    }

    @Test
    fun leaveDetailsRetainOneApprovalSnapshotDuringReassignment() {
        val f = leaveFixture()
        val leave = pending(f)
        val approval = approvalId(f.company, leave)
        val next = reviewer(f.company)
        val barrier = ApprovalReadProbe.Barrier(f.company)
        reads.current.set(barrier)
        try {
            Executors.newFixedThreadPool(2).use { executor ->
                val reading =
                    executor.submit<HttpResponse<String>> {
                        get(f.supervisor, "/api/v1/companies/${f.company}/leave/requests/$leave")
                    }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    val writing =
                        executor.submit<HttpResponse<String>> {
                            reassign(f, approval, setOf(next.account))
                        }
                    awaitApprovalWriter(f.company, writing)
                    barrier.release.countDown()
                    val response = reading.get(10, TimeUnit.SECONDS)
                    assertEquals(200, response.statusCode(), response.body())
                    val snapshot = json.readTree(response.body())
                    assertEquals(0, snapshot.get("approval").get("version").asInt())
                    assertEquals(
                        f.managerAccount.toString(),
                        snapshot.get("approval").get("stages")[0].single().asString(),
                    )
                    assertTrue(snapshot.get("availableActions").any { it.asString() == "DECIDE" })
                    val changed = writing.get(10, TimeUnit.SECONDS)
                    assertEquals(200, changed.statusCode(), changed.body())
                    val fresh = details(f, leave, f.supervisor)
                    assertEquals(1, fresh.get("approval").get("version").asInt())
                    assertEquals(
                        next.account.toString(),
                        fresh.get("approval").get("stages")[0].single().asString(),
                    )
                    assertFalse(fresh.get("availableActions").any { it.asString() == "DECIDE" })
                } finally {
                    barrier.release.countDown()
                }
            }
        } finally {
            barrier.release.countDown()
            reads.current.set(null)
        }
    }

    private fun awaitApprovalWriter(company: UUID, writer: Future<*>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
        while (System.nanoTime() < deadline) {
            val waiting =
                database()
                    .queryForObject(
                        """select exists(select 1 from pg_locks
                    where locktype='advisory' and mode='ExclusiveLock' and not granted
                      and ((classid::bigint << 32) | objid::bigint) = hashtextextended(?,0))""",
                        Boolean::class.java,
                        "approvals:$company",
                    )
            if (waiting == true) return
            if (writer.isDone)
                fail<Unit>(
                    "Reassignment completed before the leave reader released its approval snapshot"
                )
            Thread.sleep(10)
        }
        fail<Unit>("Reassignment did not reach its conflicting approval guard")
    }
}
