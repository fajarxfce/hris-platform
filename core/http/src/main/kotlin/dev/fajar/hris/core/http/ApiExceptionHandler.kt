package dev.fajar.hris.core.http

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(DomainFailureException::class)
    fun domain(error: DomainFailureException): ProblemDetail =
        apiProblem(error.failure, MDC.get("correlationId"))

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MethodArgumentNotValidException::class,
        org.springframework.beans.TypeMismatchException::class,
    )
    fun invalid(error: Exception): ProblemDetail {
        val causes = generateSequence<Throwable>(error) { it.cause }.take(16).toList()
        if (causes.any { it is RequestBodyTooLargeException }) return tooLarge()
        if (
            causes.any {
                it is RequestBodyTimeoutException || it is java.net.SocketTimeoutException
            }
        )
            return timedOut()
        return apiProblem(
            HttpStatus.BAD_REQUEST,
            "invalid_request",
            MDC.get("correlationId"),
            invalidRequestFields(error),
        )
    }

    @ExceptionHandler(RequestBodyTimeoutException::class)
    fun timedOut(): ProblemDetail =
        apiProblem(HttpStatus.REQUEST_TIMEOUT, "request_body_timeout", MDC.get("correlationId"))

    @ExceptionHandler(RequestBodyTooLargeException::class)
    fun tooLarge(): ProblemDetail =
        apiProblem(
            HttpStatus.CONTENT_TOO_LARGE,
            "request_body_too_large",
            MDC.get("correlationId"),
            parameters = mapOf("maximumBytes" to JsonRequestLimitFilter.MAXIMUM_BYTES.toString()),
        )

    @ExceptionHandler(Exception::class)
    fun unexpected(error: Exception): ProblemDetail {
        val causes = generateSequence<Throwable>(error) { it.cause }.take(16).toList()
        causes.filterIsInstance<java.util.concurrent.CancellationException>().firstOrNull()?.let {
            throw it
        }
        causes.filterIsInstance<InterruptedException>().firstOrNull()?.let {
            Thread.currentThread().interrupt()
            throw it
        }
        if (error is org.springframework.web.ErrorResponse && error.statusCode.is4xxClientError) {
            return apiProblem(
                error.statusCode,
                if (error.statusCode.value() == 404) "not_found" else "invalid_request",
                MDC.get("correlationId"),
            )
        }
        val reference = MDC.get("correlationId") ?: java.util.UUID.randomUUID().toString()
        val frames =
            causes
                .asSequence()
                .flatMap { it.stackTrace.asSequence() }
                .filter { it.className.startsWith("dev.fajar.hris.") }
                .distinct()
                .take(12)
                .joinToString(" | ")
        logger.error(
            "Unhandled request failure reference={} categories={} frames={}",
            reference,
            causes.map { it.javaClass.name }.distinct().joinToString(" > "),
            frames,
        )
        return apiProblem(HttpStatus.INTERNAL_SERVER_ERROR, "unexpected_error", reference)
    }
}
