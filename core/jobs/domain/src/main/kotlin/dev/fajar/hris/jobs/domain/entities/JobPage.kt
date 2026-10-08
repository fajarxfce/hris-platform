package dev.fajar.hris.jobs.domain.entities

import java.time.Instant
import java.util.UUID

data class JobPage(val items: List<BackgroundJob>, val nextCreatedAt: Instant?, val nextId: UUID?)
