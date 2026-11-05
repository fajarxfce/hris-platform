package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.projectCollaborationSummary
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSummaryProjectionRepository
import dev.fajar.hris.core.domain.*
import java.time.Clock
import java.util.UUID

/** Each invocation expands at most 200 targets and projects at most 200 pairs in four companies. */
class RefreshCollaborationSummaries(
    private val projections: CollaborationSummaryProjectionRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(): Result<CollaborationSummaryRefresh> {
        val worker = Actor(UUID(0, 0), null, emptySet(), clock.instant(), UUID.randomUUID())
        val expanded = transactions.run(worker) { expand() }
        if (expanded is Result.Failed) return expanded
        val due = transactions.run(worker) { projections.dueCompanies(clock.instant(), 4) }
        if (due is Result.Failed) return due
        val companies = (due as Result.Success).value
        if (companies.size > 4 || companies.distinct().size != companies.size) return invalid()
        var result = CollaborationSummaryRefresh((expanded as Result.Success).value, 0, 0)
        if (companies.isEmpty()) return Result.Success(result)
        var failure: Result.Failed? = null
        for (company in companies) {
            when (
                val projected =
                    transactions.run(worker.copy(companyId = company)) {
                        project(company, 200 / companies.size)
                    }
            ) {
                is Result.Failed -> if (failure == null) failure = projected
                is Result.Success ->
                    result =
                        result.copy(
                            projectedPairs = result.projectedPairs + projected.value.first,
                            changedRecords = result.changedRecords + projected.value.second,
                        )
            }
        }
        return failure ?: Result.Success(result)
    }

    private fun expand(): Result<Int> {
        val now = clock.instant()
        val locked = projections.lockAccounts(now, 4)
        if (locked is Result.Failed) return locked
        val accounts = (locked as Result.Success).value
        if (accounts.size > 4 || accounts.map { it.accountId }.distinct().size != accounts.size)
            return invalid()
        if (accounts.isEmpty()) return Result.Success(0)
        val limit = 200 / accounts.size
        val queued = mutableSetOf<CollaborationSummaryTarget>()
        val progress = mutableListOf<Pair<UUID, CollaborationSummaryTarget?>>()
        for (account in accounts) {
            val found = projections.targets(account.accountId, account.after, limit + 1)
            if (found is Result.Failed) return found
            val page = (found as Result.Success).value
            if (
                page.size > limit + 1 ||
                    page.distinct().size != page.size ||
                    page.zipWithNext().any { (a, b) -> targetOrder.compare(a, b) >= 0 } ||
                    account.after?.let { after ->
                        page.firstOrNull()?.let { targetOrder.compare(after, it) >= 0 }
                    } == true
            )
                return invalid()
            queued += page.take(limit)
            progress += account.accountId to if (page.size > limit) page[limit - 1] else null
        }
        // Across all locked accounts, enqueue in one stable global order to avoid opposing pair
        // locks.
        if (queued.isNotEmpty()) {
            val enqueued = projections.enqueue(queued.sortedWith(targetOrder), now)
            if (enqueued is Result.Failed) return enqueued
            if (!(enqueued as Result.Success).value) return conflict()
        }
        for ((account, after) in progress) {
            val saved = projections.advanceAccount(account, after, now.takeIf { after != null })
            if (saved is Result.Failed) return saved
            if (!(saved as Result.Success).value) return conflict()
        }
        return Result.Success(queued.size)
    }

    private fun project(company: UUID, limit: Int): Result<Pair<Int, Int>> {
        // Source writers invalidate these queues. No account/source guards may follow these locks.
        val locked = projections.lockDue(company, clock.instant(), limit)
        if (locked is Result.Failed) return locked
        val ids = (locked as Result.Success).value
        if (ids.size > limit || ids.distinct().size != ids.size) return invalid()
        if (ids.isEmpty()) return Result.Success(0 to 0)
        val loaded = projections.candidates(company, ids)
        if (loaded is Result.Failed) return loaded
        val candidates = (loaded as Result.Success).value
        if (
            candidates.size != ids.size ||
                candidates.map { it.target.conversationId }.toSet() != ids.toSet() ||
                candidates.any { it.target.companyId != company }
        )
            return invalid()
        val read = projections.records(company, ids)
        if (read is Result.Failed) return read
        val records = (read as Result.Success).value
        if (
            records.size > 2 * ids.size ||
                records.any { it.target.companyId != company || it.target.conversationId !in ids }
        )
            return invalid()
        val byConversation = records.groupBy { it.target.conversationId }
        val changes = mutableListOf<CollaborationSummaryRecord>()
        for (candidate in candidates) {
            val planned =
                projectCollaborationSummary(
                    candidate,
                    byConversation[candidate.target.conversationId].orEmpty(),
                )
            if (planned is Result.Failed) return planned
            changes += (planned as Result.Success).value
        }
        if (changes.isNotEmpty()) {
            val saved = projections.save(changes.toList())
            if (saved is Result.Failed) return saved
            if (!(saved as Result.Success).value) return conflict()
        }
        val completed = projections.complete(company, ids)
        if (completed is Result.Failed) return completed
        if (!(completed as Result.Success).value) return conflict()
        return Result.Success(ids.size to changes.size)
    }

    private fun invalid() =
        Result.Failed(Failure(FailureKind.UNEXPECTED, "collaboration_summary_projection_invalid"))

    private fun conflict() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_summary_projection_conflict"))

    private val targetOrder =
        compareBy<CollaborationSummaryTarget>(
            { it.companyId.toString() },
            { it.conversationId.toString() },
        )
}
