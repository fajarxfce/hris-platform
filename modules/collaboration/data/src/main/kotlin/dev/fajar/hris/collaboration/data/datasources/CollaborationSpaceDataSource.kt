package dev.fajar.hris.collaboration.data.datasources

import java.util.UUID

data class CollaborationSpaceContactRow(
    val spaceId: UUID?,
    val accountId: UUID?,
    val displayName: String?,
    val companyId: UUID?,
    val companyLabel: String?,
)

interface CollaborationSpaceDataSource {
    fun current(): UUID?

    fun contacts(
        spaceId: UUID,
        excludedAccountId: UUID,
        query: String,
        after: UUID?,
        accountLimit: Int,
        companyLimit: Int,
    ): List<CollaborationSpaceContactRow>
}
