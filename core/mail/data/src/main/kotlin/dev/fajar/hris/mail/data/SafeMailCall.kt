package dev.fajar.hris.mail.data

import dev.fajar.hris.core.domain.*
import jakarta.mail.MessagingException
import jakarta.mail.SendFailedException
import jakarta.mail.internet.AddressException
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException
import org.eclipse.angus.mail.smtp.SMTPSendFailedException
import org.eclipse.angus.mail.smtp.SMTPSenderFailedException
import org.slf4j.LoggerFactory
import org.springframework.mail.*

fun mailFailure(error: Exception): Failure {
    val pending = java.util.ArrayDeque<Throwable>().apply { add(error) }
    val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    while (pending.isNotEmpty() && visited.size < 16) {
        val cause = pending.removeFirst()
        if (!visited.add(cause)) continue
        if (visited.size + pending.size < 16) cause.cause?.let { pending.add(it) }
        if (visited.size + pending.size < 16 && cause is MessagingException)
            cause.nextException?.let { pending.add(it) }
        if (cause is MailSendException)
            cause.failedMessages.values.take(16 - visited.size - pending.size).forEach(pending::add)
    }
    visited
        .firstOrNull { it is InterruptedException || it is CancellationException }
        ?.let { throw it }
    val replyCodes =
        visited
            .mapNotNull {
                when (it) {
                    is SMTPAddressFailedException -> it.returnCode
                    is SMTPSenderFailedException -> it.returnCode
                    is SMTPSendFailedException -> it.returnCode
                    else -> null
                }
            }
            .filter { it in 100..599 }
            .distinct()
    val failure =
        when {
            visited.any { it is MailAuthenticationException } ->
                Failure(FailureKind.UNEXPECTED, "mail_configuration_failed")
            visited.any { it is MailParseException || it is AddressException } ->
                Failure(FailureKind.VALIDATION, "invalid_mail_envelope")
            visited.filterIsInstance<SendFailedException>().any {
                !it.invalidAddresses.isNullOrEmpty()
            } -> Failure(FailureKind.VALIDATION, "mail_recipient_rejected")
            replyCodes.any { it in 500..599 } ->
                Failure(FailureKind.UNEXPECTED, "mail_delivery_rejected")
            visited.any {
                it is MailSendException || it is java.io.IOException || it is MessagingException
            } -> Failure(FailureKind.UNAVAILABLE, "mail_delivery_unavailable")
            else -> Failure(FailureKind.UNEXPECTED, "mail_delivery_failed")
        }
    val frames =
        visited
            .asSequence()
            .flatMap { it.stackTrace.asSequence() }
            .filter { it.className.startsWith("dev.fajar.hris.") }
            .distinct()
            .take(8)
            .joinToString(" | ")
    LoggerFactory.getLogger("dev.fajar.hris.mail")
        .warn(
            "Mail operation failed code={} categories={} smtpCodes={} frames={}",
            failure.code,
            visited.map { it.javaClass.name }.distinct(),
            replyCodes,
            frames,
        )
    return failure
}

fun <T> safeMailCall(operation: () -> T): Result<T> =
    try {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val result = operation()
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        Result.Success(result)
    } catch (error: Exception) {
        if (error is InterruptedException || error is CancellationException) throw error
        if (Thread.currentThread().isInterrupted)
            throw InterruptedException().apply { initCause(error) }
        Result.Failed(mailFailure(error))
    }
