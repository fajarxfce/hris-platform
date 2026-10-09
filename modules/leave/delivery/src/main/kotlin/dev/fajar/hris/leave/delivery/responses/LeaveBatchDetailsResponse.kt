package dev.fajar.hris.leave.delivery.responses

import dev.fajar.hris.core.domain.Page

data class LeaveBatchDetailsResponse(
    val batch: LeaveBatchResponse,
    val counts: LeaveBatchCountsResponse,
    val attempts: List<LeaveBatchAttemptResponse>,
    val job: LeaveBatchProgressResponse,
    val results: Page<LeaveBatchResultResponse>,
)
