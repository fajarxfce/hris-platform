package dev.fajar.hris.core.domain

import java.util.UUID

/** Additional SELECT scope for at most 32 companies; the use case must authorize every company. */
interface CompanyReadTransactionRunner {
    fun <T> run(actor: Actor, companies: Set<UUID>, operation: () -> Result<T>): Result<T>
}
