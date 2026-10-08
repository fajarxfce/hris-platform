package dev.fajar.hris.core.http

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
        val status = failureHttpStatus(error.failure.kind)
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
        val causes = generateSequence<Throwable>(error) { it.cause }.take(16).toList()
        val tooLarge = causes.any { it is RequestBodyTooLargeException }
        val timedOut =
            causes.any {
                it is RequestBodyTimeoutException || it is java.net.SocketTimeoutException
            }
        val status =
            when {
                tooLarge -> HttpStatus.CONTENT_TOO_LARGE
                timedOut -> HttpStatus.REQUEST_TIMEOUT
                else -> HttpStatus.BAD_REQUEST
            }
        return ProblemDetail.forStatus(status).apply {
            setProperty(
                "code",
                when {
                    tooLarge -> "request_body_too_large"
                    timedOut -> "request_body_timeout"
                    else -> "invalid_request"
                },
            )
            setProperty("correlationId", org.slf4j.MDC.get("correlationId"))
        }
    }

    @ExceptionHandler(RequestBodyTimeoutException::class)
    fun timedOut(): ProblemDetail =
        ProblemDetail.forStatus(HttpStatus.REQUEST_TIMEOUT).apply {
            setProperty("code", "request_body_timeout")
            setProperty("correlationId", org.slf4j.MDC.get("correlationId"))
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
