package dev.fajar.hris.expenses.delivery.responses

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class ExpenseClaimPaymentsResponse(
    val claimId: UUID,
    @field:Schema(minimum = "0", maximum = "60") val version: Long,
    @field:ArraySchema(maxItems = 20) val attempts: List<ExpensePaymentProgressResponse>,
)
