package dev.fajar.hris.sync.domain.entities

data class SyncChange(val position: Long, val resource: SyncResource, val operation: SyncOperation)
