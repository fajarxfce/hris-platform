package dev.fajar.hris.core.domain

interface ChangeJournalRepository {
    fun record(actor: Actor, change: ChangeRecord): Result<Unit>
}
