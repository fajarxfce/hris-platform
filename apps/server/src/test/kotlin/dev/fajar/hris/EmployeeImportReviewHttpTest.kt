package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode

class EmployeeImportReviewHttpTest : EmployeeImportApiFixture() {
    private fun assertActions(value: JsonNode, vararg actions: String) {
        assertEquals(
            actions.toList(),
            value["availableActions"].iterator().asSequence().map { it.asString() }.toList(),
        )
    }

    @Test
    fun reviewTracksPreviewApplicationCancellationAndResumableJobState() {
        val f = fixture()
        val id =
            begin(
                f,
                "$header\nR01,First import,ID,2026-01-01,PERMANENT\nR02,Bad date,ID,invalid,PERMANENT",
            )
        val queued = details(f, id)
        assertEquals("QUEUED", queued["jobStatus"].asString())
        assertFalse(queued["cancellationRequested"].asBoolean())
        assertActions(queued, "cancel")
        drain(f, lease(id, JobKind.EMPLOYEE_IMPORT_PREVIEW))
        val review = details(f, id)
        assertEquals("SUCCEEDED", review["jobStatus"].asString())
        assertEquals(1, review["counts"]["READY"].asInt())
        assertEquals(1, review["counts"]["INVALID"].asInt())
        assertActions(review, "apply", "cancel")
        assertEquals(409, confirm(f, id).statusCode())
        assertEquals(200, confirm(f, id, partial = true).statusCode())
        assertActions(details(f, id), "cancel")
        val running = lease(id, JobKind.EMPLOYEE_IMPORT_APPLY)
        assertEquals("RUNNING", details(f, id)["jobStatus"].asString())
        assertEquals(200, changeImport(f, id, "cancel", 2).statusCode())
        val cancelling = details(f, id)
        assertTrue(cancelling["cancellationRequested"].asBoolean())
        assertActions(cancelling)
        assertEquals(
            Result.Success(Unit),
            abort.execute(running, Failure(FailureKind.CONFLICT, "job_cancellation_requested")),
        )
        val stopped = details(f, id)
        assertEquals("STOPPED", stopped["batch"]["status"].asString())
        assertEquals("CANCELLED", stopped["jobStatus"].asString())
        assertActions(stopped, "resume", "cancel")
        assertEquals(200, changeImport(f, id, "resume", 4).statusCode())
        assertActions(details(f, id), "cancel")
        drain(f, lease(id, JobKind.EMPLOYEE_IMPORT_APPLY))
        val completed = details(f, id)
        assertEquals("COMPLETED", completed["batch"]["status"].asString())
        assertEquals("SUCCEEDED", completed["jobStatus"].asString())
        assertActions(completed)
        assertFalse(completed.has("checkpoint"))
        assertFalse(completed.has("values"))
    }

    @Test
    fun emptyReadySetAndTerminalHeaderCannotAdvertiseApplicationOrResume() {
        val f = fixture()
        val id = begin(f, "$header\nR03,Invalid import,ID,invalid,PERMANENT")
        drain(f, lease(id, JobKind.EMPLOYEE_IMPORT_PREVIEW))
        assertActions(details(f, id), "cancel")
        assertEquals(200, changeImport(f, id, "cancel", 1).statusCode())
        val cancelled = details(f, id)
        assertEquals("CANCELLED", cancelled["batch"]["status"].asString())
        assertActions(cancelled)
    }

    @Test
    fun summaryDoesNotAcquireTheJobLockAfterHoldingTheImportGuard() {
        val f = fixture()
        val id = begin(f, csv("R04"))
        val job = UUID.fromString(details(f, id)["batch"]["jobId"].asString())
        database().dataSource!!.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection
                    .prepareStatement(
                        "select id from background_jobs where company_id=? and id=? for update"
                    )
                    .use { statement ->
                        statement.setObject(1, f.company)
                        statement.setObject(2, job)
                        statement.executeQuery().use { assertTrue(it.next()) }
                    }
                val response = get(f.browser, "${f.path}/$id")
                assertEquals(200, response.statusCode(), response.body())
                assertActions(json.readTree(response.body()), "cancel")
            } finally {
                connection.rollback()
            }
        }
    }
}
