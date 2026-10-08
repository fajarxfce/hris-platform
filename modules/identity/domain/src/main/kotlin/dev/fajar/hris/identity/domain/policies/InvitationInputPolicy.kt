package dev.fajar.hris.identity.domain.policies

import dev.fajar.hris.core.domain.*

fun validAccountEmail(email: String): Boolean {
    if (email.length !in 3..254 || email.any { it.code !in 33..126 }) return false
    val parts = email.split('@')
    if (parts.size != 2 || parts[0].length !in 1..64) return false
    val local = parts[0]
    val domain = parts[1]
    return !local.startsWith('.') &&
        !local.endsWith('.') &&
        !local.contains("..") &&
        local.all { it.isLetterOrDigit() || it in ".!#$%&'*+/=?^_`{|}~-" } &&
        domain.split('.').let { labels ->
            labels.size >= 2 &&
                labels.all { label ->
                    label.length in 1..63 &&
                        label.first().isLetterOrDigit() &&
                        label.last().isLetterOrDigit() &&
                        label.all { it.isLetterOrDigit() || it == '-' }
                }
        }
}

fun validateInvitation(
    email: String,
    displayName: String,
    reason: String,
    expectedVersion: Long?,
): Result<Unit> =
    if (
        validAccountEmail(email) &&
            displayName.isNotBlank() &&
            displayName.length <= 200 &&
            displayName.none { it.isISOControl() } &&
            reason.isNotBlank() &&
            reason.length <= 1000 &&
            (expectedVersion == null || expectedVersion >= 0)
    )
        Result.Success(Unit)
    else Result.Failed(Failure(FailureKind.VALIDATION, "invalid_invitation"))
