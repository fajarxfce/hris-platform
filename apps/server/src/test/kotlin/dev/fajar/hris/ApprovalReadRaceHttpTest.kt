package dev.fajar.hris

import java.net.http.HttpResponse
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(ApprovalReadProbeConfiguration::class, ApprovalFenceProbeConfiguration::class)
class ApprovalReadRaceHttpTest : ApprovalApiFixture() {
    @Autowired private lateinit var reads: ApprovalReadProbe
    @Autowired private lateinit var writes: ApprovalFenceProbe

    @ParameterizedTest
    @ValueSource(strings = ["inbox", "request"])
    fun assignmentChangesCannotMixWithAnOlderRequestVersion(operation: String) {
        val f = leaveFixture()
        val id = approvalId(f.company, pending(f))
        val next = reviewer(f.company)
        val path =
            "/api/v1/companies/${f.company}/approvals" + if (operation == "request") "/$id" else ""
        val barrier = ApprovalReadProbe.Barrier(f.company)
        reads.current.set(barrier)
        try {
            Executors.newFixedThreadPool(2).use { pool ->
                val reading = pool.submit<HttpResponse<String>> { get(f.supervisor, path) }
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val waiting = ApprovalFenceProbe.Observation(f.company)
                writes.observedLock.set(waiting)
                val writing =
                    pool.submit<HttpResponse<String>> { reassign(f, id, setOf(next.account)) }
                try {
                    assertTrue(waiting.entered.await(5, TimeUnit.SECONDS))
                    assertFalse(writing.isDone)
                    barrier.release.countDown()
                    val response = reading.get(10, TimeUnit.SECONDS)
                    assertEquals(200, response.statusCode(), response.body())
                    val body = json.readTree(response.body())
                    val request = if (operation == "request") body else body.get("items").single()
                    assertEquals(0, request.get("version").asInt())
                    assertEquals(
                        f.managerAccount.toString(),
                        request.get("stages")[0].get("assignees").single().asString(),
                    )
                    val changed = writing.get(10, TimeUnit.SECONDS)
                    assertEquals(200, changed.statusCode(), changed.body())
                    val latest = get(f.admin, "/api/v1/companies/${f.company}/approvals/$id")
                    assertEquals(200, latest.statusCode(), latest.body())
                    assertEquals(1, json.readTree(latest.body()).get("version").asInt())
                    assertEquals(
                        next.account.toString(),
                        json
                            .readTree(latest.body())
                            .get("stages")[0]
                            .get("assignees")
                            .single()
                            .asString(),
                    )
                } finally {
                    barrier.release.countDown()
                    writes.observedLock.set(null)
                }
            }
        } finally {
            barrier.release.countDown()
            reads.current.set(null)
            writes.observedLock.set(null)
        }
    }
}
