package dev.fajar.hris.jobs.delivery.responses

import java.util.UUID

data class JobPageResponse(
    val items: List<JobResponse>,
    val nextCreatedAt: String?,
    val nextId: UUID?,
)
