package dev.fajar.hris.communications.domain.entities

import java.time.Duration

class InboxEmailPolicy(
    val enabled: Boolean = false,
    val publicOrigin: String = "",
    val appEnvironment: String = "prod",
) {
    val maximumAttempts = 8
    val leaseSeconds = 120
    val deliveryLifetime: Duration = Duration.ofHours(24)
    val retention: Duration = Duration.ofDays(7)

    init {
        require(appEnvironment in setOf("prod", "dev", "staging"))
        require(!enabled || publicOrigin.isNotBlank())
    }
}
