package dev.fajar.hris.reporting.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReportExportExecutionPolicyTest {
    @Test
    fun executionAcceptsOnlyItsImmutableIdentityAndBoundedProgressContract() {
        val id = UUID.randomUUID()
        val company = UUID.randomUUID()
        val account = UUID.randomUUID()
        val at = Instant.parse("2026-10-28T00:00:00Z")
        val request =
            JobRequest(
                UUID.randomUUID(),
                company,
                account,
                JobKind.REPORT_EXPORT,
                UUID.randomUUID(),
                mapOf("exportId" to id.toString()),
                at,
                4,
                UUID.randomUUID(),
                at,
                REPORT_EXPORT_JOB_STEPS,
                JobProgressMode.UPPER_BOUND,
            )
        val actor =
            Actor(
                account,
                company,
                setOf("reports.read", "people.read"),
                at,
                UUID.randomUUID(),
                credentialVersion = 4,
            )
        assertEquals(Result.Success(id), validateReportExportJobActor(actor, request))
        for (changed in
            listOf(
                request.copy(kind = JobKind.WORKFORCE_CLOSE),
                request.copy(values = mapOf("exportId" to id.toString().uppercase())),
                request.copy(values = request.values + ("sql" to "untrusted")),
                request.copy(values = mapOf("exportId" to "1-1-1-1-1")),
                request.copy(totalItems = REPORT_EXPORT_JOB_STEPS + 1),
                request.copy(progressMode = JobProgressMode.FIXED_TOTAL),
                request.copy(credentialVersion = 3),
                request.copy(authenticatedAt = at.minusSeconds(1)),
                request.copy(actorId = UUID.randomUUID()),
                request.copy(companyId = UUID.randomUUID()),
            )) assertTrue(validateReportExportJobActor(actor, changed) is Result.Failed)
    }

    @Test
    fun sourceScopeCannotRestoreAnchorGrantsFromAStoredExport() {
        val company = UUID.randomUUID()
        val account = UUID.randomUUID()
        val actor = Actor(account, company, setOf("reports.read"), Instant.EPOCH, UUID.randomUUID())
        val saved = setOf("reports.read", "people.read")
        assertEquals(setOf("reports.read"), reportExportScope(actor, company, saved).permissions)
        assertEquals(saved, reportExportScope(actor, UUID.randomUUID(), saved).permissions)
        assertEquals(setOf("reports.read"), actor.permissions)
    }
}
