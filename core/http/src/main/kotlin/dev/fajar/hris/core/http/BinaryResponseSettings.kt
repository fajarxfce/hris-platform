package dev.fajar.hris.core.http

import java.time.Duration

data class BinaryResponseSettings(
    val concurrency: Int = 4,
    val timeout: Duration = Duration.ofSeconds(60),
    val shutdownTimeout: Duration = Duration.ofSeconds(25),
) {
    init {
        require(concurrency in 1..4)
        require(timeout.toMillis() in 1..120000)
        require(shutdownTimeout.toMillis() in 1..30000)
    }
}
