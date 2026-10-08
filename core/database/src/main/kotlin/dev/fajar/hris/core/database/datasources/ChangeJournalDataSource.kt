package dev.fajar.hris.core.database.datasources

interface ChangeJournalDataSource {
    fun append(row: JournalRow)
}
