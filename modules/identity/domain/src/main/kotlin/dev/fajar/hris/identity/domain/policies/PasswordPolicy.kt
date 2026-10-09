package dev.fajar.hris.identity.domain.policies

import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.core.domain.FailureKind
import dev.fajar.hris.core.domain.Result

fun validateNewPassword(password: String): Result<Unit> =
    if (password.length in 12..128) Result.Success(Unit)
    else
        Result.Failed(
            Failure(
                FailureKind.VALIDATION,
                "password_length",
                mapOf("password" to "length_12_128"),
                mapOf("minimumLength" to "12", "maximumLength" to "128"),
            )
        )
