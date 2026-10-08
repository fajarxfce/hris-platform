package dev.fajar.hris.storage.domain.entities

import java.util.UUID

data class ObjectCleanupLease(val entry: ObjectCleanupEntry, val token: UUID)
