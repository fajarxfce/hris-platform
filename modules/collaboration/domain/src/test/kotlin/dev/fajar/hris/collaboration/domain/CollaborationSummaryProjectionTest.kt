package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.projectCollaborationSummary
import dev.fajar.hris.core.domain.Result
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSummaryProjectionTest {
    private val target = CollaborationSummaryTarget(UUID.randomUUID(), UUID.randomUUID())
    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()
    private val candidate = CollaborationSummaryCandidate(target, first, second, "First", "Second")

    private fun project(
        candidate: CollaborationSummaryCandidate = this.candidate,
        current: List<CollaborationSummaryRecord> = emptyList(),
    ): List<CollaborationSummaryRecord> =
        (projectCollaborationSummary(candidate, current) as Result.Success).value

    @Test
    fun twoOwnersSeeTheirPeerAndIdenticalAcquisitionWritesNothing() {
        val current = project()
        assertEquals(
            listOf(
                CollaborationSummaryRecord(target, first, second, 0, "Second"),
                CollaborationSummaryRecord(target, second, first, 0, "First"),
            ),
            current,
        )
        assertTrue(project(current = current).isEmpty())
        assertEquals(
            listOf(current[0].copy(version = 1, peerDisplayName = "Renamed")),
            project(candidate.copy(secondDisplayName = "Renamed"), current),
        )
    }

    @Test
    fun withdrawalRestoresAtANewRevisionWithoutReplacingPeerIdentity() {
        val current = project().map { it.copy(version = 12, peerDisplayName = null) }
        assertEquals(project().map { it.copy(version = 13) }, project(current = current))
    }

    @Test
    fun unchangedMaximumRevisionIsReadableButCannotOverflow() {
        val current = project().map { it.copy(version = Long.MAX_VALUE) }
        assertTrue(project(current = current).isEmpty())
        val failure =
            projectCollaborationSummary(candidate.copy(firstDisplayName = "Changed"), current)
                as Result.Failed
        assertEquals("collaboration_summary_revision_exhausted", failure.failure.code)
    }

    @Test
    fun foreignDuplicateAndMalformedRecordsCannotBeSilentlyRepaired() {
        val current = project()
        for (rows in
            listOf(
                current + current[0],
                listOf(current[0], current[0]),
                listOf(current[0].copy(target = target.copy(companyId = UUID.randomUUID()))),
                listOf(current[0].copy(ownerAccountId = UUID.randomUUID())),
                listOf(current[0].copy(peerAccountId = first)),
                listOf(current[0].copy(version = -1)),
                listOf(current[0].copy(peerDisplayName = " ")),
            )) assertTrue(projectCollaborationSummary(candidate, rows) is Result.Failed)
        for (source in
            listOf(
                candidate.copy(secondAccountId = first),
                candidate.copy(firstDisplayName = " "),
                candidate.copy(secondDisplayName = "x".repeat(201)),
            )) assertTrue(projectCollaborationSummary(source, emptyList()) is Result.Failed)
    }
}
