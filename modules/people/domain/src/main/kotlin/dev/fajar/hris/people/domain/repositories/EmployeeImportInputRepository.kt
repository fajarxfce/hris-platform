package dev.fajar.hris.people.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.people.domain.entities.EmployeeImportRow

interface EmployeeImportInputRepository {
    fun template(): Result<String>

    fun decodeCsv(content: String): Result<List<EmployeeImportRow>>
}
