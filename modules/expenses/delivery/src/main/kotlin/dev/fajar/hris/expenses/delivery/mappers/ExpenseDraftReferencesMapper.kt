package dev.fajar.hris.expenses.delivery.mappers

import dev.fajar.hris.expenses.delivery.responses.*
import dev.fajar.hris.expenses.domain.entities.ExpenseDraftReferences

fun ExpenseDraftReferences.toResponse() =
    ExpenseDraftReferencesResponse(
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

/** Keep the existing self-service wire schema while sharing domain acquisition. */
fun ExpenseDraftReferences.toSelfResponse(): SelfExpenseDraftReferencesResponse =
    toResponse().let {
        SelfExpenseDraftReferencesResponse(
            it.employmentId,
            it.categories,
            it.costCenters,
            it.receipts,
        )
    }
