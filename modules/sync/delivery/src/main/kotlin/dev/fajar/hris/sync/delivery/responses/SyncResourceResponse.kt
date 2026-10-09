package dev.fajar.hris.sync.delivery.responses

import java.util.UUID

data class SyncResourceResponse(val collection: String, val id: UUID, val version: Long)
