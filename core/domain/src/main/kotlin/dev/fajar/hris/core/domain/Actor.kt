package dev.fajar.hris.core.domain

import java.time.Instant
import java.util.UUID

data class Actor(
    val accountId: UUID,
    val companyId: UUID?,
    val permissions: Set<String>,
    val authenticatedAt: Instant,
    val correlationId: UUID,
) {
    fun requirePermission(permission: String): Result<Unit> =
        if (permission in permissions) Result.Success(Unit)
        else Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
}
