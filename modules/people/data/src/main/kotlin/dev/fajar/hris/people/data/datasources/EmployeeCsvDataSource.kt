package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.people.data.models.EmployeeCsvDocument

interface EmployeeCsvDataSource {
    fun template(): String

    fun read(content: String): EmployeeCsvDocument
}
