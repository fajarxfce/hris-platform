package dev.fajar.hris.worker.runtime

enum class JobCancellation {
    REQUESTED,
    LEASE_LOST,
    SHUTDOWN,
    TIME_LIMIT,
}
