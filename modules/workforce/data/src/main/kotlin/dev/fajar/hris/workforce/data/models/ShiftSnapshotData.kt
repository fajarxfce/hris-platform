package dev.fajar.hris.workforce.data.models

import java.util.UUID

data class ShiftSnapshotData(val id: UUID, val revision: Long, val details: ShiftDetailsData)
