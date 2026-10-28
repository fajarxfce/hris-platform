package dev.fajar.hris.core.domain

import java.util.UUID

/**
 * Keeps the actor's company as the write scope while adding SELECT access to at most 32 report
 * source companies. The use case must authorize the anchor and every source inside this scope. This
 * does not grant source-company writes or make successive queries one database snapshot.
 */
interface ReportSourceTransactionRunner {
    fun <T> runWithReportSources(
        actor: Actor,
        companies: Set<UUID>,
        operation: () -> Result<T>,
    ): Result<T>
}
