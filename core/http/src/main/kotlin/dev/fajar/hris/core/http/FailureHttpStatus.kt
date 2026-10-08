package dev.fajar.hris.core.http

import dev.fajar.hris.core.domain.FailureKind
import org.springframework.http.HttpStatus

fun failureHttpStatus(kind: FailureKind): HttpStatus =
    when (kind) {
        FailureKind.VALIDATION -> HttpStatus.UNPROCESSABLE_CONTENT
        FailureKind.NOT_FOUND -> HttpStatus.NOT_FOUND
        FailureKind.CONFLICT -> HttpStatus.CONFLICT
        FailureKind.FORBIDDEN -> HttpStatus.FORBIDDEN
        FailureKind.UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED
        FailureKind.RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS
        FailureKind.UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE
        FailureKind.UNEXPECTED -> HttpStatus.INTERNAL_SERVER_ERROR
    }
