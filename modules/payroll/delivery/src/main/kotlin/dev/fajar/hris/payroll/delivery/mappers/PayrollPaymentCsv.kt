package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.payroll.domain.entities.PayrollPaymentBatch

/** Generic finance worksheet. Bank-specific upload formats must have a reviewed adapter. */
fun PayrollPaymentBatch.toPaymentCsv(): ByteArray {
    require(items.size in 1..100)
    val headers =
        listOf(
            "payment_reference",
            "batch_id",
            "assessment_id",
            "employee_number",
            "employee_name",
            "tax_month",
            "planned_payment_date",
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
                    item.assessmentId.toString(),
                    "'" + item.employeeNumber,
                    "'" + item.employeeName,
                    item.taxMonth.toString(),
                    item.plannedPaymentDate.toString(),
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
