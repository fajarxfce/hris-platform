package dev.fajar.hris.identity.domain.entities

data class NativeSessionCleanupResult(
    val deletedSessions: Int,
    val clearedExchangeReplays: Int,
    val clearedRefreshReplays: Int,
)
