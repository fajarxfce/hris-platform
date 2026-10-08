package dev.fajar.hris.core.http

import dev.fajar.hris.core.domain.FailureKind
import org.slf4j.LoggerFactory
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
    fun domain(error: DomainFailureException): ProblemDetail {
        val status =
            when (error.failure.kind) {
                FailureKind.VALIDATION -> HttpStatus.UNPROCESSABLE_CONTENT
                FailureKind.NOT_FOUND -> HttpStatus.NOT_FOUND
                FailureKind.CONFLICT -> HttpStatus.CONFLICT
                FailureKind.FORBIDDEN -> HttpStatus.FORBIDDEN
                FailureKind.UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED
                FailureKind.RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS
                FailureKind.UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE
                FailureKind.UNEXPECTED -> HttpStatus.INTERNAL_SERVER_ERROR
            }
        return ProblemDetail.forStatus(status).apply {
            setProperty("correlationId", org.slf4j.MDC.get("correlationId"))
            setProperty("code", error.failure.code)
            setProperty("fields", error.failure.fields)
        }
    }

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MethodArgumentNotValidException::class,
        org.springframework.beans.TypeMismatchException::class,
    )
    fun invalid(error: Exception): ProblemDetail {
        val tooLarge =
            generateSequence<Throwable>(error) { it.cause }
                .take(16)
                .any { it is RequestBodyTooLargeException }
        return ProblemDetail.forStatus(
                if (tooLarge) HttpStatus.CONTENT_TOO_LARGE else HttpStatus.BAD_REQUEST
            )
            .apply {
                setProperty("code", if (tooLarge) "request_body_too_large" else "invalid_request")
                setProperty("correlationId", org.slf4j.MDC.get("correlationId"))
            }
    }

    @ExceptionHandler(RequestBodyTooLargeException::class)
    fun tooLarge(): ProblemDetail =
        ProblemDetail.forStatus(HttpStatus.CONTENT_TOO_LARGE).apply {
            setProperty("code", "request_body_too_large")
            setProperty("correlationId", org.slf4j.MDC.get("correlationId"))
        }

    @ExceptionHandler(Exception::class)
    fun unexpected(error: Exception): ProblemDetail {
        if (error is java.util.concurrent.CancellationException) throw error
        if (error is InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        }
        if (error is org.springframework.web.ErrorResponse && error.statusCode.is4xxClientError) {
            return ProblemDetail.forStatus(error.statusCode).apply {
                setProperty(
                    "code",
                    if (error.statusCode.value() == 404) "not_found" else "invalid_request",
                )
                setProperty("correlationId", org.slf4j.MDC.get("correlationId"))
            }
        }
        val reference = org.slf4j.MDC.get("correlationId") ?: java.util.UUID.randomUUID().toString()
        logger.error(
            "Unhandled request failure reference={} category={}",
            reference,
            error.javaClass.name,
        )
        return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR).apply {
            setProperty("code", "unexpected_error")
            setProperty("correlationId", reference)
        }
    }
}
