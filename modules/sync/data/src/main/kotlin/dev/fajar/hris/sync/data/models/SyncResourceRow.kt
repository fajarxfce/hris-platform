package dev.fajar.hris.sync.data.models

import java.util.UUID

data class SyncResourceRow(val collection: String, val id: UUID, val version: Long)
