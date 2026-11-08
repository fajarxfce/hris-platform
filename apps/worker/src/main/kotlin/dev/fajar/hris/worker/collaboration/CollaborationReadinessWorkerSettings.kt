package dev.fajar.hris.worker.collaboration

import java.time.Duration

class CollaborationReadinessWorkerSettings(
    val parallelism: Int = 2,
    val pollInterval: Duration = Duration.ofMillis(500),
    val fanOutTimeout: Duration = Duration.ofSeconds(20),
) {
    init {
        require(parallelism in 1..4)
        require(pollInterval in Duration.ofMillis(10)..Duration.ofMinutes(1))
        require(fanOutTimeout in Duration.ofMillis(100)..Duration.ofSeconds(20))
    }
}
