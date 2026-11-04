package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpacePushBinding
import dev.fajar.hris.collaboration.domain.policies.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpacePushBindingPolicyTest {
    @Test
    fun originalUserMayKeepItsRetainedOrFreshDeviceWhileGroupIdentityCannotImpersonateLegacy() {
        val space = UUID.randomUUID()
        val account = UUID.randomUUID()
        val device = UUID.randomUUID()
        val group =
            CollaborationSpacePushBinding(
                space,
                account,
                device,
                UUID.randomUUID(),
                "@${collaborationSpaceMatrixLocalpart(space, account)}:matrix.example.test",
                "HRISG_${compact(device)}",
                0,
                Instant.parse("2026-11-04T10:00:00Z"),
                null,
            )
        val original =
            group.copy(
                matrixUserId =
                    "@${collaborationMatrixLocalpart(UUID.randomUUID(), account)}:matrix.example.test"
            )
        val retained = original.copy(matrixDeviceId = "HRIS_${compact(UUID.randomUUID())}")
        assertTrue(validCollaborationSpacePushBinding(group))
        assertTrue(validCollaborationSpacePushBinding(original))
        assertTrue(validCollaborationSpacePushBinding(retained))
        assertFalse(
            validCollaborationSpacePushBinding(group.copy(matrixDeviceId = retained.matrixDeviceId))
        )
        assertFalse(validCollaborationSpacePushBinding(group.copy(spaceId = UUID.randomUUID())))
        assertFalse(
            validCollaborationSpacePushBinding(original.copy(accountId = UUID.randomUUID()))
        )
        assertFalse(validCollaborationSpacePushBinding(retained.copy(nativeSessionId = UUID(0, 0))))
        assertFalse(validCollaborationSpacePushKey("hriscp1_" + "a".repeat(64)))
        assertTrue(validCollaborationSpacePushKey("hrisgp1_" + "a".repeat(64)))
    }

    @Test
    fun publicationCannotLoseReservationTimeOrAcquireAnUnboundedRevision() {
        val space = UUID.randomUUID()
        val account = UUID.randomUUID()
        val device = UUID.randomUUID()
        val at = Instant.parse("2026-11-04T10:00:00.123456Z")
        val value =
            CollaborationSpacePushBinding(
                space,
                account,
                device,
                UUID.randomUUID(),
                "@${collaborationSpaceMatrixLocalpart(space, account)}:matrix.example.test",
                "HRISG_${compact(device)}",
                0,
                at,
                null,
            )
        assertTrue(validCollaborationSpacePushBinding(value.copy(version = 1, publishedAt = at)))
        for (bad in
            listOf(
                value.copy(version = 1),
                value.copy(publishedAt = at),
                value.copy(version = 2, publishedAt = at),
                value.copy(version = 1, publishedAt = at.minusNanos(1000)),
                value.copy(reservedAt = at.plusNanos(1)),
                value.copy(version = 1, publishedAt = Instant.MAX),
            )) assertFalse(validCollaborationSpacePushBinding(bad))
    }

    private fun compact(value: UUID) = value.toString().replace("-", "")
}
