package dev.fajar.hris.sync.domain.entities

import java.util.UUID

data class SyncScope(
    val accountId: UUID,
    val companyId: UUID,
    val credentialVersion: Long,
    val membershipVersion: Long,
    val companyVersion: Long,
    val permissions: Set<String>,
    val employmentIds: Set<UUID>,
    val collections: Set<SyncCollection>,
)
