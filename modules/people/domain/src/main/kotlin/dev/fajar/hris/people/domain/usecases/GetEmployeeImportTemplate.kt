package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.policies.requireEmployeeImportAccess
import dev.fajar.hris.people.domain.repositories.EmployeeImportInputRepository

class GetEmployeeImportTemplate(private val input: EmployeeImportInputRepository) {
    fun execute(actor: Actor): Result<String> {
        val access = requireEmployeeImportAccess(actor)
        if (access is Result.Failed) return access
        return input.template()
    }
}
