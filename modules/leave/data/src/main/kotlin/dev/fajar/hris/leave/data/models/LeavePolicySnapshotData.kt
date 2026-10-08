package dev.fajar.hris.leave.data.models

import java.util.UUID

data class LeavePolicySnapshotData(
    val typeId: UUID,
    val code: String,
    val revision: Long,
    val policy: LeavePolicyData,
)
