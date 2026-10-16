package dev.fajar.hris.identity.data.models

data class NativeSessionCleanupData(
    val deletedSessions: Int,
    val clearedExchangeReplays: Int,
    val clearedRefreshReplays: Int,
)
