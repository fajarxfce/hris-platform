package dev.fajar.hris.communications.domain.entities

import java.util.UUID

data class SaveAudienceGroupCommand(
    val id: UUID,
    val expectedVersion: Long?,
    val name: String,
    val active: Boolean,
    val employmentIds: List<UUID>,
    val reason: String,
)
