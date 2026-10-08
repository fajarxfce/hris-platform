package dev.fajar.hris.documents.data.datasources

import dev.fajar.hris.documents.data.models.*
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID

interface DocumentInventoryDataSource {
    fun capacity(companyId: UUID): DocumentInventoryCapacityData

    fun find(companyId: UUID, id: UUID): DocumentInventoryViewsRecord?

    fun list(companyId: UUID, after: UUID?, limit: Int): List<DocumentInventoryViewsRecord>

    fun insert(row: DocumentInventoryRunsRecord)

    fun insertAttempt(row: DocumentInventoryAttemptsRecord)

    fun resume(companyId: UUID, id: UUID, version: Long, jobId: UUID, attempt: Int): Boolean

    fun references(companyId: UUID, keys: Set<String>): List<DocumentInventoryReferenceData>

    fun insertRecovery(row: DocumentInventoryRecoveriesRecord)

    fun checkpoint(
        companyId: UUID,
        id: UUID,
        version: Long,
        row: DocumentInventoryPagesRecord,
        status: String,
    ): Boolean

    fun attempts(companyId: UUID, id: UUID): List<DocumentInventoryAttemptViewsRecord>

    fun pages(companyId: UUID, id: UUID, after: Int, limit: Int): List<DocumentInventoryPagesRecord>
}
