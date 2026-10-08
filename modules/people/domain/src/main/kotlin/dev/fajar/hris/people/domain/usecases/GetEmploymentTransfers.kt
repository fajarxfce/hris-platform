package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.EmploymentTransfer
import dev.fajar.hris.people.domain.repositories.EmploymentTransferRepository
import java.util.UUID

class GetEmploymentTransfers(
    private val transfers: EmploymentTransferRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, id: UUID): Result<List<EmploymentTransfer>> =
        actor.requirePermission("people.read").flatMap {
            transactions.run(actor) { transfers.forEmployee(requireNotNull(actor.companyId), id) }
        }
}
