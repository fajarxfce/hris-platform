package dev.fajar.hris.communications.domain

import dev.fajar.hris.communications.domain.policies.inboxPushRetryAt
import dev.fajar.hris.core.domain.*
import java.time.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InboxPushPolicyTest {
    @Test
    fun finiteRetryBudgetHonorsProviderDelayAndDoesNotOutliveTheInboxHint() {
        val now = Instant.parse("2026-10-01T00:00:00Z")
        val transient = Failure(FailureKind.UNAVAILABLE, "push_delivery_unavailable")
        assertEquals(
            now.plusSeconds(5),
            inboxPushRetryAt(transient, 1, now, now.plusSeconds(1000), 8),
        )
        assertEquals(
            now.plusSeconds(300),
            inboxPushRetryAt(transient, 7, now, now.plusSeconds(1000), 8),
        )
        assertNull(inboxPushRetryAt(transient, 8, now, now.plusSeconds(1000), 8))
        assertNull(inboxPushRetryAt(transient, 1, now, now.plusSeconds(5), 8))
        assertNull(
            inboxPushRetryAt(
                Failure(FailureKind.UNEXPECTED, "push_credentials_rejected"),
                1,
                now,
                now.plusSeconds(1000),
                8,
            )
        )
        assertEquals(
            now.plusSeconds(600),
            inboxPushRetryAt(
                Failure(
                    FailureKind.RATE_LIMITED,
                    "push_rate_limited",
                    parameters = mapOf("retryAfterSeconds" to "600"),
                ),
                1,
                now,
                now.plusSeconds(1000),
                8,
            ),
        )
        assertNull(
            inboxPushRetryAt(
                Failure(
                    FailureKind.RATE_LIMITED,
                    "push_rate_limited",
                    parameters = mapOf("retryAfterSeconds" to "86400"),
                ),
                1,
                now,
                now.plusSeconds(1000),
                8,
            )
        )
    }
}
