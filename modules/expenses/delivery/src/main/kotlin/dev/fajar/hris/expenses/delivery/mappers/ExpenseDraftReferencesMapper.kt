package dev.fajar.hris.expenses.delivery.mappers

import dev.fajar.hris.expenses.delivery.responses.*
import dev.fajar.hris.expenses.domain.entities.SelfExpenseDraftReferences

fun SelfExpenseDraftReferences.toResponse() =
    SelfExpenseDraftReferencesResponse(
        employmentId,
        categories.map { ExpenseDraftLabelReferenceResponse(it.id, it.code, it.name, it.active) },
        costCenters.map { ExpenseDraftLabelReferenceResponse(it.id, it.code, it.name, it.active) },
        receipts.map {
            ExpenseDraftReceiptReferenceResponse(
                it.revisionId,
                it.documentId,
                it.fileName,
                it.mediaType,
                it.size,
                it.status?.name,
            )
        },
    )
