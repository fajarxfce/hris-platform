package dev.fajar.hris.jobs.domain.entities

import java.time.Instant
import java.util.UUID

data class JobLease(
    val job: BackgroundJob,
    val owner: UUID,
    val token: UUID,
    val expiresAt: Instant,
)
