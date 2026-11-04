package dev.fajar.hris.worker.collaboration

import java.time.Duration

class CollaborationSpaceRoomWorkerSettings(
    val parallelism: Int = 2,
    val pollInterval: Duration = Duration.ofMillis(250),
    val processingTimeout: Duration = Duration.ofSeconds(45),
) {
    init {
        require(parallelism in 1..8)
        require(pollInterval in Duration.ofMillis(10)..Duration.ofMinutes(1))
        require(processingTimeout in Duration.ofMillis(100)..Duration.ofSeconds(55))
    }
}
