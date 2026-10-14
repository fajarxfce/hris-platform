package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.policies.projectDirectoryEmployee
import dev.fajar.hris.people.domain.repositories.DirectoryProjectionRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Worker-only projection policy. Each pass visits at most four companies and 200 rows. */
class RefreshCompanyDirectory(
    private val projections: DirectoryProjectionRepository,
    private val companies: CompanyRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(): Result<Int> {
        val worker = Actor(UUID(0, 0), null, emptySet(), clock.instant(), UUID.randomUUID())
        val due = transactions.run(worker) { projections.dueCompanies(clock.instant(), 4) }
        if (due is Result.Failed) return due
        val selected = (due as Result.Success).value
        if (selected.isEmpty()) return Result.Success(0)
        val perCompany = 200 / selected.size
        var count = 0
        var failure: Result.Failed? = null
        for (company in selected) {
            val refreshed =
                transactions.run(worker.copy(companyId = company)) { refresh(company, perCompany) }
            when (refreshed) {
                is Result.Failed -> if (failure == null) failure = refreshed
                is Result.Success -> count += refreshed.value
            }
        }
        return failure ?: Result.Success(count)
    }

    private fun refresh(company: UUID, limit: Int): Result<Int> {
        // Never acquire source guards after queue rows: source writers capture into
        // this queue. MVCC reads plus the locked queue preserve later invalidations.
        val companyGuard = companies.lock(company, shared = true)
        if (companyGuard is Result.Failed) return companyGuard
        val found = companies.find(company)
        if (found is Result.Failed) return found
        val companyData =
            (found as Result.Success).value
                ?: return Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
        val zone = ZoneId.of(companyData.timezone)
        val now = clock.instant()
        val today = LocalDate.ofInstant(now, zone)
        val locked = projections.lockDue(company, now, limit)
        if (locked is Result.Failed) return locked
        val ids = (locked as Result.Success).value
        if (ids.isEmpty()) return Result.Success(0)
        val loaded = projections.candidates(company, ids, today)
        if (loaded is Result.Failed) return loaded
        val candidates = (loaded as Result.Success).value
        if (candidates.map { it.id }.toSet() != ids.toSet() || candidates.size != ids.size)
            return Result.Failed(Failure(FailureKind.UNEXPECTED, "directory_projection_invalid"))
        for (candidate in candidates) {
            val current = projections.find(company, candidate.id)
            if (current is Result.Failed) return current
            val planned =
                projectDirectoryEmployee(candidate, (current as Result.Success).value, today, zone)
            if (planned is Result.Failed) return planned
            val projection = (planned as Result.Success).value
            projection.changed?.let {
                val saved = projections.save(company, it)
                if (saved is Result.Failed) return saved
            }
            val scheduled = projections.schedule(company, candidate.id, projection.nextRefresh)
            if (scheduled is Result.Failed) return scheduled
        }
        return Result.Success(ids.size)
    }
}
