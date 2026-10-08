package dev.fajar.hris.core.domain

enum class FailureKind {
    VALIDATION,
    NOT_FOUND,
    CONFLICT,
    FORBIDDEN,
    UNAUTHENTICATED,
    UNAVAILABLE,
    UNEXPECTED,
}
