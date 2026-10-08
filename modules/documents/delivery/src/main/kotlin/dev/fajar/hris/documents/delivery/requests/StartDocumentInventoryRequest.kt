package dev.fajar.hris.documents.delivery.requests

import java.util.UUID

data class StartDocumentInventoryRequest(val runId: UUID, val reason: String)
