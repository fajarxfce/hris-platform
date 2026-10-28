package dev.fajar.hris.reporting.delivery

import dev.fajar.hris.reporting.delivery.mappers.toResponse
import dev.fajar.hris.reporting.domain.entities.ExpenseReportCounts
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExpenseReportResponseTest {
    @Test
    fun amountsRemainExactBeyondFloatingPointAndIndividualClaimBounds() {
        for (amount in
            listOf(
                "0.00",
                "0.01",
                "123.45",
                "1000000000000.03",
                "9007199254740993.01",
            )) assertEquals(
            amount,
            ExpenseReportCounts(amount = BigDecimal(amount)).toResponse().amount,
        )
    }
}
