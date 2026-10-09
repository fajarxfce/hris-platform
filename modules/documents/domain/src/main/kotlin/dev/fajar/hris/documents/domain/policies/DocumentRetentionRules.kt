package dev.fajar.hris.documents.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.*
import java.time.Instant

fun validateDocumentRetentionAccess(
    actor: Actor,
    at: Instant,
    security: IdentitySecurityPolicy,
): Result<Unit> =
    actor.requirePermission("documents.retention").flatMap {
        if (security.enforceMfa) requireRecentMfa(actor, at, security.recentAuthenticationAge)
        else requireRecentAuthentication(actor, at, security.recentAuthenticationAge)
    }

fun validateDocumentRetentionPolicy(days: Int?, reason: String): Result<Unit> =
    if (
        (days != null && days !in 1..36500) ||
            reason.isBlank() ||
            reason.length > 1000 ||
            reason.any { it.isISOControl() }
    )
        Result.Failed(Failure(FailureKind.VALIDATION, "invalid_document_retention_policy"))
    else Result.Success(Unit)

/** Computes one state transition; use cases own access, live-upload checks and persistence. */
fun transitionDocumentRetention(
    state: DocumentRetentionState,
    action: DocumentRetentionAction,
    policy: DocumentRetentionPolicy?,
    at: Instant,
): Result<DocumentRetentionState> {
    if ((state.version ?: -1) >= 9999)
        return Result.Failed(Failure(FailureKind.CONFLICT, "document_retention_history_limit"))
    val next = state.copy(version = (state.version ?: -1) + 1)
    return when (action) {
        DocumentRetentionAction.ARCHIVE -> {
            if (state.archive != null)
                Result.Failed(Failure(FailureKind.CONFLICT, "document_already_archived"))
            else
                Result.Success(
                    next.copy(
                        archive =
                            DocumentArchive(
                                at,
                                policy?.id,
                                policy?.version,
                                policy?.retentionDays,
                                policy?.retentionDays?.let { at.plusSeconds(it.toLong() * 86400) },
                            )
                    )
                )
        }
        DocumentRetentionAction.RESTORE ->
            if (state.archive == null)
                Result.Failed(Failure(FailureKind.CONFLICT, "document_not_archived"))
            else Result.Success(next.copy(archive = null))
        DocumentRetentionAction.PLACE_HOLD ->
            if (state.legalHold)
                Result.Failed(Failure(FailureKind.CONFLICT, "document_already_held"))
            else Result.Success(next.copy(legalHold = true))
        DocumentRetentionAction.RELEASE_HOLD ->
            if (!state.legalHold) Result.Failed(Failure(FailureKind.CONFLICT, "document_not_held"))
            else Result.Success(next.copy(legalHold = false))
    }
}
