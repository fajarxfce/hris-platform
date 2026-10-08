package dev.fajar.hris.expenses.delivery.mappers

import dev.fajar.hris.expenses.domain.entities.ExpensePaymentBatch

/** Generic finance worksheet. Bank-specific upload formats must have a reviewed adapter. */
fun ExpensePaymentBatch.toPaymentCsv(): ByteArray {
    require(items.size in 1..100)
    val headers =
        listOf(
            "payment_reference",
            "batch_id",
            "claim_id",
            "submission_id",
            "employee_number",
            "employee_name",
            "bank_code",
            "account_number",
            "account_name",
            "currency",
            "amount",
        )
    val rows =
        items
            .sortedBy { it.id }
            .map { item ->
                listOf(
                    item.id.toString(),
                    id.toString(),
                    item.claimId.toString(),
                    item.submissionId.toString(),
                    "'" + item.employeeNumber,
                    "'" + item.employeeName,
                    "'" + item.destination.bankCode,
                    "'" + item.destination.accountNumber,
                    "'" + item.destination.accountName,
                    "IDR",
                    item.amount.setScale(2).toPlainString(),
                )
            }
    return (listOf(headers) + rows)
        .joinToString("\r\n", postfix = "\r\n") { row ->
            row.joinToString(",") { paymentCsvCell(it) }
        }
        .toByteArray(Charsets.UTF_8)
}

/** Prevent spreadsheet formula evaluation in text fields; account numbers carry a text prefix. */
fun paymentCsvCell(value: String): String {
    val visible = value.trimStart()
    val safe =
        if (
            visible.firstOrNull() in setOf('=', '+', '-', '@') ||
                value.firstOrNull() in setOf('\t', '\r', '\n')
        )
            "'" + value
        else value
    return "\"" + safe.replace("\"", "\"\"") + "\""
}
