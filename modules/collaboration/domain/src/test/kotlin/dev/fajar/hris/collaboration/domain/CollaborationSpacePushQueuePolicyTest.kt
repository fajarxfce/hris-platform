package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpacePushQueuePolicyTest {
    private val at = Instant.parse("2026-11-04T00:00:00Z")
    private val space = UUID.randomUUID()
    private val account = UUID.randomUUID()
    private val device = UUID.randomUUID()
    private val parent = UUID.randomUUID()
    private val lease =
        CollaborationSpacePushLease(
            space,
            account,
            device,
            parent,
            UUID.randomUUID(),
            UUID.randomUUID(),
            2,
            at.plusSeconds(30),
        )
    private val head =
        CollaborationSpacePushHead(
            space,
            account,
            device,
            parent,
            2,
            1,
            at.minusSeconds(10),
            at,
            at.plusSeconds(300),
            2,
            CollaborationPushEvent("!room:example.test", "$" + "event"),
            at,
            2,
            at,
            2,
            1,
            lease,
        )

    @Test
    fun boundsRetainQuietVisibleAndSupersededLeaseStates() {
        assertTrue(validCollaborationSpacePushHead(head))
        assertTrue(validCollaborationSpacePushHead(head.copy(expiresAt = at.plusSeconds(1))))
        assertTrue(
            validCollaborationSpacePushHead(head.copy(revision = 3, lastHintAt = at.plusSeconds(1)))
        )
        assertTrue(validCollaborationSpacePushHead(head.copy(lease = null)))
        assertTrue(
            validCollaborationSpacePushHead(
                head.copy(
                    revision = 1,
                    deliveredRevision = 0,
                    visibleRevision = 0,
                    event = null,
                    visibleAt = null,
                    recentEventCount = 0,
                    attemptRevision = 0,
                    attempts = 0,
                    lease = null,
                )
            )
        )
    }

    @Test
    fun invalidRevisionsLeaseOwnersAndFiniteAttemptBoundsFailClosed() {
        val invalid =
            listOf(
                head.copy(spaceId = UUID(0, 0)),
                head.copy(revision = 0),
                head.copy(revision = 1000000000000),
                head.copy(deliveredRevision = 3),
                head.copy(deliveredRevision = -1),
                head.copy(attempts = 9),
                head.copy(attemptRevision = 0),
                head.copy(attemptRevision = 3),
                head.copy(lease = lease.copy(ownerId = UUID(0, 0))),
                head.copy(lease = lease.copy(token = UUID(0, 0))),
                head.copy(lease = lease.copy(nativeSessionId = UUID.randomUUID())),
                head.copy(lease = lease.copy(spaceId = UUID.randomUUID())),
                head.copy(lease = lease.copy(revision = 1)),
                head.copy(lease = lease.copy(expiresAt = head.createdAt)),
            )
        invalid.forEach { assertFalse(validCollaborationSpacePushHead(it)) }
    }

    @Test
    fun malformedVisibilityTimestampsAndRecentEventWindowsFailClosed() {
        val invalid =
            listOf(
                head.copy(createdAt = at.plusSeconds(1)),
                head.copy(expiresAt = at),
                head.copy(expiresAt = at.plusSeconds(86401)),
                head.copy(availableAt = head.createdAt.minusSeconds(1)),
                head.copy(visibleRevision = 0),
                head.copy(visibleRevision = 3),
                head.copy(visibleAt = null),
                head.copy(visibleAt = head.createdAt.minusSeconds(1)),
                head.copy(visibleAt = at.plusSeconds(1)),
                head.copy(event = null),
                head.copy(event = CollaborationPushEvent("invalid", "$" + "event")),
                head.copy(recentEventCount = 0),
                head.copy(recentEventCount = 65),
            )
        invalid.forEach { assertFalse(validCollaborationSpacePushHead(it)) }
    }
}
