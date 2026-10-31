package dev.fajar.hris.collaboration.data.models

import dev.fajar.hris.schema.tables.records.CollaborationDevicesRecord
import java.util.UUID

data class BoundCollaborationDeviceData(
    val device: CollaborationDevicesRecord,
    val nativeSessionId: UUID,
)
