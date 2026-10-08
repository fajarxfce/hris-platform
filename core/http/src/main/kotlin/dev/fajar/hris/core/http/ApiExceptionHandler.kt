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
    )
    fun invalid(): ProblemDetail =
        ProblemDetail.forStatus(HttpStatus.BAD_REQUEST).apply {
            setProperty("code", "invalid_request")
        }

    @ExceptionHandler(Exception::class)
    fun unexpected(error: Exception): ProblemDetail {
        if (error is java.util.concurrent.CancellationException) throw error
        if (error is InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
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
