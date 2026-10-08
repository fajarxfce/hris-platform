package dev.fajar.hris.core.domain

import java.util.UUID

/** Stable command outcome; replay must not emit another audit/outbox record. */
data class MutationReceipt(val id: UUID, val version: Long, val replayed: Boolean = false)
