package dev.fajar.hris.communications.domain.entities

import java.time.Duration

class InboxPushPolicy(val enabled: Boolean = false) {
    val maximumAttempts = 8
    val maximumTargets = 500
    val leaseSeconds = 120
    val deliveryLifetime: Duration = Duration.ofHours(24)
    val retention: Duration = Duration.ofDays(7)
}
