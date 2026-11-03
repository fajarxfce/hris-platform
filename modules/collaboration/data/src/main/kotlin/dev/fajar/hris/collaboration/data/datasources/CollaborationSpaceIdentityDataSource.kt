package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.records.CollaborationSpaceIdentitiesRecord
import dev.fajar.hris.schema.tables.records.CollaborationSpaceLegacyDevicesRecord
import java.util.UUID

data class CollaborationSpaceIdentitySelectionRow(
    val spaceId: UUID?,
    val accountId: UUID?,
    val selected: CollaborationSpaceIdentitiesRecord?,
    val preferred: CollaborationSpaceLegacyDevicesRecord?,
)

interface CollaborationSpaceIdentityDataSource {
    fun selection(
        spaceId: UUID,
        accountId: UUID,
        serverName: String,
    ): CollaborationSpaceIdentitySelectionRow?

    fun insert(record: CollaborationSpaceIdentitiesRecord)
}
