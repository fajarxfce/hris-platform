package dev.fajar.hris.worker.push

import java.time.Duration

class InboxPushWorkerSettings(
    val parallelism: Int = 4,
    val pollInterval: Duration = Duration.ofMillis(250),
    val deliveryTimeout: Duration = Duration.ofSeconds(60),
) {
    init {
        require(parallelism in 1..8)
        require(pollInterval in Duration.ofMillis(10)..Duration.ofMinutes(1))
        require(deliveryTimeout in Duration.ofMillis(100)..Duration.ofSeconds(60))
    }
}
