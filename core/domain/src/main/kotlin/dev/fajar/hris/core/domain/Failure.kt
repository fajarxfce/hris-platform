package dev.fajar.hris.core.domain

data class Failure(
    val kind: FailureKind,
    val code: String,
    val fields: Map<String, String> = emptyMap(),
)
