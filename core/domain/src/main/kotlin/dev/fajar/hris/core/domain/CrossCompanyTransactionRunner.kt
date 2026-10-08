package dev.fajar.hris.core.domain

import java.util.UUID

/** The use case authorizes both companies inside this explicitly bounded scope. */
interface CrossCompanyTransactionRunner {
    fun <T> run(actor: Actor, secondaryCompanyId: UUID, operation: () -> Result<T>): Result<T>
}
