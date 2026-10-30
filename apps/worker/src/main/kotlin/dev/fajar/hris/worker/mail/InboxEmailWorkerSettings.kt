package dev.fajar.hris.worker.mail

import java.time.Duration

class InboxEmailWorkerSettings(
    val parallelism: Int = 2,
    val pollInterval: Duration = Duration.ofMillis(250),
    val deliveryTimeout: Duration = Duration.ofSeconds(60),
) {
    init {
        require(parallelism in 1..4)
        require(pollInterval in Duration.ofMillis(10)..Duration.ofMinutes(1))
        require(deliveryTimeout in Duration.ofMillis(100)..Duration.ofSeconds(60))
    }
}
