package dev.fajar.hris.jobs.domain.entities

import java.time.Instant
import java.util.UUID

data class JobRequest(
    val id: UUID,
    val companyId: UUID,
    val actorId: UUID,
    val kind: JobKind,
    val operationId: UUID,
    val values: Map<String, String>,
    val authenticatedAt: Instant,
    val credentialVersion: Long,
    val correlationId: UUID,
    val createdAt: Instant,
    val totalItems: Int,
)
