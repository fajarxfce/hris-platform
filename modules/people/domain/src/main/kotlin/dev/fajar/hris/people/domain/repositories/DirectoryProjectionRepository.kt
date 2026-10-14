package dev.fajar.hris.people.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.people.domain.entities.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface DirectoryProjectionRepository {
    fun dueCompanies(now: Instant, limit: Int): Result<List<UUID>>

    fun lockDue(company: UUID, now: Instant, limit: Int): Result<List<UUID>>

    fun candidates(
        company: UUID,
        ids: List<UUID>,
        today: LocalDate,
    ): Result<List<DirectoryCandidate>>

    fun find(company: UUID, id: UUID): Result<DirectoryRecord?>

    fun save(company: UUID, record: DirectoryRecord): Result<Unit>

    fun schedule(company: UUID, id: UUID, next: Instant?): Result<Unit>
}
