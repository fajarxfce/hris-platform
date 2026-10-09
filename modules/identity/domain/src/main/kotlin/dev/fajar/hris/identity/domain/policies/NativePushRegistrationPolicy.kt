package dev.fajar.hris.identity.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import java.time.Instant

fun validateNativePushInput(input: SaveNativePushRegistrationCommand): Result<Unit> {
    val fields = linkedMapOf<String, String>()
    if (input.expectedVersion != null && input.expectedVersion !in 0..10000)
        fields["expectedVersion"] = "out_of_range"
    if (input.token.length !in 16..2048 || input.token.any { it.code !in 33..126 })
        fields["token"] = "invalid_token"
    return if (fields.isEmpty()) Result.Success(Unit)
    else Result.Failed(Failure(FailureKind.VALIDATION, "invalid_push_registration", fields))
}

fun validateNativePushActor(
    actor: Actor,
    account: Account?,
    session: NativeSession?,
    expectedSessionVersion: Long,
    now: Instant,
): Result<Unit> =
    if (
        account != null &&
            account.id == actor.accountId &&
            account.active &&
            account.securityVersion == actor.credentialVersion &&
            session != null &&
            session.accountId == actor.accountId &&
            session.credentialVersion == account.securityVersion &&
            session.version == expectedSessionVersion &&
            nativeSessionActive(session, now) &&
            session.accessExpiresAt.isAfter(now)
    )
        Result.Success(Unit)
    else nativeAuthenticationRequired()
