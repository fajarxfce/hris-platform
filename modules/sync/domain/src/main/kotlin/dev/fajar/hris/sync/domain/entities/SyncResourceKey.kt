package dev.fajar.hris.sync.domain.entities

import java.util.UUID

data class SyncResourceKey(val collection: SyncCollection, val id: UUID)
