package dev.fajar.hris.core.http

import dev.fajar.hris.core.domain.MutationReceipt
import java.util.UUID

data class MutationResponse(val id: UUID, val version: Long)

fun MutationReceipt.toResponse(): MutationResponse = MutationResponse(id, version)
