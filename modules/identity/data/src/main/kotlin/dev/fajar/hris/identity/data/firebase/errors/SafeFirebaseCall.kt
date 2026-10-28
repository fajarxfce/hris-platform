package dev.fajar.hris.identity.data.firebase.errors

import dev.fajar.hris.core.domain.*
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.SignatureException
import java.util.concurrent.CancellationException
import tools.jackson.core.JacksonException

class FirebaseKeysUnavailableException : RuntimeException("Firebase signing keys are unavailable")

fun <T> safeFirebaseCall(operation: () -> T): Result<T> =
    try {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val value = operation()
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        Result.Success(value)
    } catch (error: Exception) {
        if (error is InterruptedException || error is CancellationException) throw error
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        when (error) {
            is FirebaseKeysUnavailableException,
            is IOException ->
                Result.Failed(Failure(FailureKind.UNAVAILABLE, "firebase_sign_in_unavailable"))
            is IllegalArgumentException,
            is JacksonException,
            is SignatureException ->
                Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "firebase_sign_in_failed"))
            is GeneralSecurityException ->
                Result.Failed(Failure(FailureKind.UNAVAILABLE, "firebase_sign_in_unavailable"))
            else -> Result.Failed(Failure(FailureKind.UNEXPECTED, "firebase_verification_failed"))
        }
    }
