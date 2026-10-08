package dev.fajar.hris.storage.delivery.requests
data class RetryObjectCleanupRequest(val expectedVersion: Long, val reason: String)
