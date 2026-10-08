package dev.fajar.hris.expenses.domain

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.policies.*
import dev.fajar.hris.people.domain.entities.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExpenseSubmissionPolicyTest {
    private val today = LocalDate.of(2026, 10, 9)
    private val category =
        ExpenseCategory(
            UUID.randomUUID(),
            "TRAVEL",
            0,
            0,
            today.minusDays(1000),
            ExpensePolicy(
                "Travel",
                BigDecimal("500000.00"),
                BigDecimal("500000.00"),
                true,
                true,
                30,
                setOf(ContractKind.PERMANENT),
            ),
            true,
        )
    private val line =
        ExpenseLine(
            UUID.randomUUID(),
            category.id,
            today,
            BigDecimal("300000.00"),
            "Travel",
            UUID.randomUUID(),
            listOf(UUID.randomUUID()),
        )
    private val terms =
        EmploymentTerms(
            today.minusYears(2),
            ContractKind.PERMANENT,
            today.minusYears(2),
            null,
            EmploymentStatus.ACTIVE,
            null,
            null,
            null,
            null,
            null,
        )

    @Test
    fun datesAreBoundedBeforeLoadingHistoryAndEmptyDraftsCannotBeSubmitted() {
        val draft =
            ExpenseDraft(
                UUID.randomUUID(),
                0,
                "E1",
                "Example Employee",
                "Travel",
                "",
                line.amount,
                listOf(line),
                UUID.randomUUID(),
                "Submit",
                Instant.EPOCH,
            )
        assertTrue(validateExpenseSubmissionDraft(draft, today) is Result.Success)
        assertEquals(
            "expense_lines_required",
            (validateExpenseSubmissionDraft(draft.copy(lines = emptyList()), today)
                    as Result.Failed)
                .failure
                .code,
        )
        for (date in listOf(today.plusDays(1), today.minusDays(367))) assertEquals(
            "expense_transaction_date_invalid",
            (validateExpenseSubmissionDraft(
                    draft.copy(lines = listOf(line.copy(occurredOn = date))),
                    today,
                )
                    as Result.Failed)
                .failure
                .code,
        )
        assertTrue(
            validateExpenseSubmissionDraft(
                draft.copy(lines = listOf(line.copy(occurredOn = today.minusDays(366)))),
                today,
            )
                is Result.Success
        )
    }

    @Test
    fun eligibilityAndRequirementsUseTheTermsOnTheTransactionDate() {
        assertTrue(validateExpenseLinePolicy(line, category, terms, today) is Result.Success)
        for (ineligible in
            listOf(
                terms.copy(contract = ContractKind.FIXED_TERM),
                terms.copy(startDate = today.plusDays(1)),
                terms.copy(endDate = today.minusDays(1)),
                terms.copy(status = EmploymentStatus.SUSPENDED),
            )) assertEquals(
            "expense_employment_ineligible",
            (validateExpenseLinePolicy(line, category, ineligible, today) as Result.Failed)
                .failure
                .code,
        )
        assertTrue(
            validateExpenseLinePolicy(
                line.copy(occurredOn = today.minusDays(30)),
                category,
                terms,
                today,
            )
                is Result.Success
        )
        assertEquals(
            "expense_transaction_expired",
            (validateExpenseLinePolicy(
                    line.copy(occurredOn = today.minusDays(31)),
                    category,
                    terms,
                    today,
                )
                    as Result.Failed)
                .failure
                .code,
        )
        assertEquals(
            "expense_category_unavailable",
            (validateExpenseLinePolicy(line, category.copy(active = false), terms, today)
                    as Result.Failed)
                .failure
                .code,
        )
        assertEquals(
            "expense_receipt_required",
            (validateExpenseLinePolicy(
                    line.copy(receiptRevisionIds = emptyList()),
                    category,
                    terms,
                    today,
                )
                    as Result.Failed)
                .failure
                .code,
        )
        assertEquals(
            "expense_cost_center_required",
            (validateExpenseLinePolicy(line.copy(costCenterId = null), category, terms, today)
                    as Result.Failed)
                .failure
                .code,
        )
        assertEquals(
            "expense_line_limit_exceeded",
            (validateExpenseLinePolicy(
                    line.copy(amount = BigDecimal("500000.01")),
                    category,
                    terms,
                    today,
                )
                    as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun categoryCapsAggregateAcrossPolicyRevisionsUsingDecimalAmounts() {
        val first =
            ExpenseSubmittedLine(
                line.id,
                line.occurredOn,
                line.amount,
                line.description,
                category,
                null,
                emptyList(),
            )
        val second =
            first.copy(
                id = UUID.randomUUID(),
                category =
                    category.copy(
                        appliedRevision = 1,
                        version = 1,
                        policy = category.policy.copy(maximumClaimAmount = BigDecimal("800000.00")),
                    ),
            )
        assertEquals(
            "expense_category_limit_exceeded",
            (validateExpenseCategoryTotals(listOf(first, second)) as Result.Failed).failure.code,
        )
        assertTrue(
            validateExpenseCategoryTotals(
                listOf(first, second.copy(amount = BigDecimal("200000.00")))
            )
                is Result.Success
        )
        assertTrue(
            validateExpenseCategoryTotals(
                listOf(
                    first,
                    second.copy(
                        category = second.category.copy(id = UUID.randomUUID(), code = "MEALS")
                    ),
                )
            )
                is Result.Success
        )
        val cents =
            first.copy(
                amount = BigDecimal("0.10"),
                category =
                    category.copy(
                        policy = category.policy.copy(maximumClaimAmount = BigDecimal("0.30"))
                    ),
            )
        assertTrue(
            validateExpenseCategoryTotals(
                listOf(cents, cents.copy(id = UUID.randomUUID(), amount = BigDecimal("0.20")))
            )
                is Result.Success
        )
    }
}
