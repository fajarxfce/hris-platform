package dev.fajar.hris.organization.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.organization.domain.entities.Company
import java.util.UUID

interface CompanyRepository {
    fun find(id: UUID): Result<Company?>

    fun create(actor: Actor, operationId: UUID, company: Company): Result<MutationReceipt>

    fun update(actor: Actor, operationId: UUID, company: Company): Result<MutationReceipt>
}
