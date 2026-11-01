package dev.fajar.hris.push.data.transport

import java.net.http.HttpTimeoutException
import java.time.Duration
import java.time.Instant

/** Technical operation budget only; this never authorizes a target or extends message TTL. */
fun remainingPushDispatch(deadline: Instant, now: Instant): Duration {
    val remaining = Duration.between(now, deadline)
    if (remaining < Duration.ofMillis(1))
        throw HttpTimeoutException("Push dispatch deadline expired")
    require(remaining <= Duration.ofMinutes(5))
    return remaining
}
