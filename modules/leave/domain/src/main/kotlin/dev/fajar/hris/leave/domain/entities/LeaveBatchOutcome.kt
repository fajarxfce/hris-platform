package dev.fajar.hris.leave.domain.entities

import dev.fajar.hris.core.domain.Failure
import java.util.UUID

data class LeaveBatchOutcome(
    val status: LeaveBatchResultStatus,
    val resourceId: UUID? = null,
    val failure: Failure? = null,
)
