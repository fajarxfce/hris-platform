package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.schema.tables.records.NativePushRegistrationsRecord
import java.util.UUID

interface NativePushRegistrationDataSource {
    fun find(accountId: UUID, sessionId: UUID): NativePushRegistrationsRecord?

    fun save(record: NativePushRegistrationsRecord, expectedVersion: Long?): Int
}
