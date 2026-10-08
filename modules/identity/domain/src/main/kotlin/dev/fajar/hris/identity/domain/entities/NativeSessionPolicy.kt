package dev.fajar.hris.identity.domain.entities

import java.time.Duration

data class NativeSessionPolicy(
    val accessLifetime: Duration = Duration.ofMinutes(10),
    val sessionLifetime: Duration = Duration.ofDays(30),
    val replayLifetime: Duration = Duration.ofMinutes(2),
    val minimumRotationInterval: Duration = Duration.ofSeconds(10),
    val maximumSessions: Int = 10,
    val maximumIssuedSessions: Int = 100,
    val maximumRotations: Long = 5_000,
) {
    init {
        require(accessLifetime in Duration.ofMinutes(1)..Duration.ofMinutes(30))
        require(sessionLifetime in Duration.ofHours(1)..Duration.ofDays(90))
        require(replayLifetime in Duration.ofSeconds(10)..Duration.ofMinutes(5))
        require(minimumRotationInterval in Duration.ofSeconds(1)..Duration.ofMinutes(1))
        require(maximumSessions in 1..50 && maximumRotations in 1..10_000)
        require(maximumIssuedSessions in maximumSessions..500)
    }
}
