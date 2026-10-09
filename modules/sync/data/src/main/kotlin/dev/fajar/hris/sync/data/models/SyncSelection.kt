package dev.fajar.hris.sync.data.models

import java.util.UUID

data class SyncSelection(
    val companyId: UUID,
    val accountId: UUID,
    val employmentIds: Set<UUID>,
    val collections: Set<String>,
)
