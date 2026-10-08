package dev.fajar.hris.worker.runtime

import java.time.Duration

data class WorkerSettings(
    val concurrency: Int = 2,
    val leaseSeconds: Int = 60,
    val pollInterval: Duration = Duration.ofSeconds(2),
    val heartbeatInterval: Duration = Duration.ofSeconds(15),
    val maximumRunTime: Duration = Duration.ofMinutes(30),
    val shutdownTimeout: Duration = Duration.ofSeconds(40),
) {
    init {
        require(concurrency in 1..4 && leaseSeconds in 30..300)
        require(pollInterval.toMillis() in 1..30000)
        require(
            heartbeatInterval.toMillis() >= 1 &&
                heartbeatInterval < Duration.ofSeconds(leaseSeconds.toLong() / 2)
        )
        require(maximumRunTime > Duration.ZERO && maximumRunTime <= Duration.ofHours(1))
        require(shutdownTimeout > Duration.ZERO && shutdownTimeout <= Duration.ofSeconds(45))
    }
}
