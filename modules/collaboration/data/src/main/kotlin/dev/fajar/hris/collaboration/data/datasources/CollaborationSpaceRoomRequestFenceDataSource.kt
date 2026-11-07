package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationSpaceRoomRequestFencesRecord
import java.util.UUID

interface CollaborationSpaceRoomRequestFenceDataSource {
    fun find(accountId: UUID, operationId: UUID): CollaborationSpaceRoomRequestFencesRecord?

    fun insert(value: CollaborationSpaceRoomRequestFencesRecord)
}
