package dev.fajar.hris.people.data.models

import java.util.UUID

data class SelfProfileUnitData(
    val id: UUID,
    val code: String,
    val name: String,
    val active: Boolean,
)
