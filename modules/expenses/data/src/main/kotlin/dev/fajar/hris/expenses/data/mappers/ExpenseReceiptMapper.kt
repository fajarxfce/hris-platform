package dev.fajar.hris.expenses.data.mappers

import dev.fajar.hris.expenses.data.models.ExpenseReceiptRow
import dev.fajar.hris.expenses.domain.entities.ExpenseReceipt

fun ExpenseReceiptRow.toReceipt() =
    ExpenseReceipt(
        requireNotNull(receipt.submissionId),
        requireNotNull(receipt.claimId),
        employmentId,
        approvalId,
        requireNotNull(receipt.documentRevisionId),
        requireNotNull(receipt.fileName),
        requireNotNull(receipt.mediaType),
        requireNotNull(receipt.size),
        requireNotNull(receipt.sha256),
    )
