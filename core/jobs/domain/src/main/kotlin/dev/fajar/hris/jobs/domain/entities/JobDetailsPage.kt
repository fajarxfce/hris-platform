package dev.fajar.hris.jobs.domain.entities

import java.time.Instant
import java.util.UUID

data class JobDetailsPage(
    val items: List<JobDetails>,
    val nextCreatedAt: Instant?,
    val nextId: UUID?,
)
