package dev.fajar.hris.expenses.domain.entities

import java.util.UUID

/** Missing and out-of-scope references carry only the requested identifier. */
data class ExpenseDraftLabelReference(
    val id: UUID,
    val code: String? = null,
    val name: String? = null,
    val active: Boolean? = null,
)
