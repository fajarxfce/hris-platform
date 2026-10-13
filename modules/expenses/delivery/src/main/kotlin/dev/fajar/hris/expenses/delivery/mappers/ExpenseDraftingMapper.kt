package dev.fajar.hris.expenses.delivery.mappers

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.expenses.delivery.responses.*
import dev.fajar.hris.expenses.domain.entities.ExpenseDraftDirectory
import dev.fajar.hris.expenses.domain.entities.ExpenseDraftReceiptOption

fun ExpenseDraftDirectory.toResponse() =
    ExpenseDraftDirectoryResponse(
        companyDate,
        timezone,
        Page(
            employees.items.map {
                ExpenseDraftEmployeeResponse(
                    it.id,
                    it.employeeNumber,
                    it.legalName,
                    it.status.name,
                    it.version,
                )
            },
            employees.nextCursor,
        ),
    )

fun ExpenseDraftReceiptOption.toResponse() =
    ExpenseDraftReceiptOptionResponse(
        documentId,
        revisionId,
        revisionNumber,
        title,
        fileName,
        mediaType,
        size,
        validatedAt,
    )
