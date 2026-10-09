package dev.fajar.hris.documents.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DocumentRetentionPolicyTest {
    private val now = Instant.parse("2026-10-24T12:00:00Z")
    private val id = UUID.randomUUID()
    private val policy =
        DocumentRetentionPolicy(
            UUID.randomUUID(),
            DocumentClassification.PERSONAL,
            3,
            1,
            UUID.randomUUID(),
            now,
            "Approved",
        )

    @Test
    fun deadlinesUseElapsedDaysAndMissingPoliciesHaveNoDeadline() {
        val archive =
            (transitionDocumentRetention(
                    DocumentRetentionState(id),
                    DocumentRetentionAction.ARCHIVE,
                    policy,
                    now,
                )
                    as Result.Success)
                .value
        assertEquals(now.plusSeconds(86400), archive.archive?.eligibleAt)
        assertEquals(3L, archive.archive?.policyVersion)
        val indefinite =
            (transitionDocumentRetention(
                    DocumentRetentionState(id),
                    DocumentRetentionAction.ARCHIVE,
                    null,
                    now,
                )
                    as Result.Success)
                .value
        assertNull(indefinite.archive?.eligibleAt)
        assertEquals(0L, indefinite.version)
    }

    @Test
    fun holdsAndRestoreDoNotSilentlyChangeTheFrozenPolicyOrClearAHold() {
        val archived =
            (transitionDocumentRetention(
                    DocumentRetentionState(id),
                    DocumentRetentionAction.ARCHIVE,
                    policy,
                    now,
                )
                    as Result.Success)
                .value
        val held =
            (transitionDocumentRetention(
                    archived,
                    DocumentRetentionAction.PLACE_HOLD,
                    policy.copy(retentionDays = 7),
                    now.plusSeconds(1),
                )
                    as Result.Success)
                .value
        assertEquals(archived.archive, held.archive)
        val restored =
            (transitionDocumentRetention(
                    held,
                    DocumentRetentionAction.RESTORE,
                    null,
                    now.plusSeconds(2),
                )
                    as Result.Success)
                .value
        assertNull(restored.archive)
        assertTrue(restored.legalHold)
        assertTrue(
            transitionDocumentRetention(restored, DocumentRetentionAction.PLACE_HOLD, null, now)
                is Result.Failed
        )
    }

    @Test
    fun policyAndTransitionHistoriesAreFinite() {
        for (days in listOf(0, -1, 36501)) assertTrue(
            validateDocumentRetentionPolicy(days, "Approved") is Result.Failed
        )
        assertEquals(Result.Success(Unit), validateDocumentRetentionPolicy(null, "Approved"))
        assertEquals(Result.Success(Unit), validateDocumentRetentionPolicy(36500, "Approved"))
        assertTrue(
            transitionDocumentRetention(
                DocumentRetentionState(id, 9999),
                DocumentRetentionAction.PLACE_HOLD,
                null,
                now,
            )
                is Result.Failed
        )
        assertTrue(
            transitionDocumentRetention(
                DocumentRetentionState(id),
                DocumentRetentionAction.RELEASE_HOLD,
                null,
                now,
            )
                is Result.Failed
        )
    }

    @Test
    fun destructiveLifecycleAuthorityRequiresItsOwnGrantAndRecentMfaWhenEnforced() {
        val actor =
            Actor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                setOf("documents.retention"),
                now,
                UUID.randomUUID(),
            )
        val security = IdentitySecurityPolicy(enforceMfa = true)
        assertTrue(validateDocumentRetentionAccess(actor, now, security) is Result.Failed)
        assertEquals(
            Result.Success(Unit),
            validateDocumentRetentionAccess(actor.copy(mfaVerifiedAt = now), now, security),
        )
        assertTrue(
            validateDocumentRetentionAccess(
                actor.copy(permissions = setOf("documents.manage"), mfaVerifiedAt = now),
                now,
                security,
            )
                is Result.Failed
        )
        assertTrue(
            validateDocumentRetentionAccess(
                actor.copy(authenticatedAt = now.minusSeconds(601)),
                now,
                security.copy(enforceMfa = false),
            )
                is Result.Failed
        )
    }
}
