package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.collaborationGatewayRotationAllowed
import dev.fajar.hris.collaboration.domain.policies.validateCollaborationGatewayResponse
import dev.fajar.hris.core.domain.FailureKind
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationGatewayPolicyTest {
    private val at = Instant.parse("2026-10-31T00:00:00Z")
    private val session =
        CollaborationGatewaySession(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            CollaborationPlatform.ANDROID,
            1,
            0,
            at,
            at,
            at.plusSeconds(3600),
            at.plusSeconds(300),
        )
    private val admission =
        CollaborationAdmission(
            session.companyId,
            session.accountId,
            session.deviceId,
            1,
            session.nativeSessionId,
            0,
            0,
            at,
            at.plusSeconds(10),
        )

    private fun tokens(version: Long = 0, replaySeconds: Long = 30) =
        CollaborationGatewayTokens(
            session.id,
            version,
            "fictional-access",
            "fictional-refresh",
            session.accessExpiresAt,
            session.expiresAt,
            at.plusSeconds(replaySeconds),
        )

    @Test
    fun aLiveTokenResponseCannotOutliveItsCurrentParentOrPolicyAdmission() {
        assertTrue(
            validateCollaborationGatewayResponse(
                session,
                tokens(),
                admission,
                session.platform,
                at.plusSeconds(9),
            )
                is Result.Success
        )
        assertTrue(
            validateCollaborationGatewayResponse(
                session,
                tokens(),
                admission,
                session.platform,
                at.plusSeconds(10),
            )
                is Result.Failed
        )
        assertTrue(
            validateCollaborationGatewayResponse(
                session,
                tokens(),
                admission,
                session.platform,
                at.minusNanos(1),
            )
                is Result.Failed
        )
    }

    @Test
    fun aFreshAdmissionCannotExtendAReplayOrPublishAnotherHeadGeneration() {
        val current = admission.copy(expiresAt = at.plusSeconds(60))
        assertTrue(
            validateCollaborationGatewayResponse(
                session,
                tokens(),
                current,
                session.platform,
                at.plusSeconds(30),
            )
                is Result.Failed
        )
        assertTrue(
            validateCollaborationGatewayResponse(
                session,
                tokens(replaySeconds = 31),
                current,
                session.platform,
                at,
            )
                is Result.Failed
        )
        assertTrue(
            validateCollaborationGatewayResponse(
                session,
                tokens(version = 1),
                current,
                session.platform,
                at,
            )
                is Result.Failed
        )
    }

    @Test
    fun exhaustedRotationRequiresReauthenticationWithoutRevokingTheDevice() {
        val retained = session.copy(version = 999999)
        val result = collaborationGatewayRotationAllowed(retained, at.plusSeconds(11))
        assertTrue(result is Result.Failed)
        assertEquals(FailureKind.UNAUTHENTICATED, (result as Result.Failed).failure.kind)
        assertEquals("collaboration_gateway_renewal_required", result.failure.code)
        assertNull(retained.revokedAt)
    }
}
