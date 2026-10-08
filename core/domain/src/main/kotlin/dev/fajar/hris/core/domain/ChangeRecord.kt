package dev.fajar.hris.core.domain

import java.util.UUID

data class ChangeRecord(
    val resourceType: String,
    val resourceId: UUID,
    val action: String,
    val details: Map<String, String> = emptyMap(),
    val reason: String? = null,
)
