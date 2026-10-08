package dev.fajar.hris.people.data.repositories

import dev.fajar.hris.people.data.datasources.EmployeeCsvDataSource
import dev.fajar.hris.people.data.errors.safeCsvCall
import dev.fajar.hris.people.data.mappers.toImportRows
import dev.fajar.hris.people.domain.repositories.EmployeeImportInputRepository

class CsvEmployeeImportInputRepository(private val source: EmployeeCsvDataSource) :
    EmployeeImportInputRepository {
    override fun template() = safeCsvCall { source.template() }

    override fun decodeCsv(content: String) = safeCsvCall { source.read(content).toImportRows() }
}
