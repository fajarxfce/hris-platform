package dev.fajar.hris.worker.mail

import java.time.Duration

class IdentityMailWorkerSettings(
    val pollInterval: Duration = Duration.ofSeconds(2),
    val deliveryTimeout: Duration = Duration.ofSeconds(30),
) {
    init {
        require(
            !pollInterval.isNegative &&
                !pollInterval.isZero &&
                pollInterval <= Duration.ofMinutes(1)
        )
        require(
            !deliveryTimeout.isNegative &&
                !deliveryTimeout.isZero &&
                deliveryTimeout <= Duration.ofSeconds(60)
        )
    }
}
