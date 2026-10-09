package dev.fajar.hris.leave.domain.entities

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.jobs.domain.entities.BackgroundJob

data class LeaveBatchDetails(
    val batch: LeaveBatch,
    val counts: LeaveBatchCounts,
    val attempts: List<LeaveBatchAttempt>,
    val job: BackgroundJob,
    val results: Page<LeaveBatchResult>,
)
