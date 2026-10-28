package dev.fajar.hris.administration.domain

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.policies.workerIntegrationIsRecent
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WorkerIntegrationPolicyTest {
    private val at = Instant.parse("2026-10-28T12:00:00Z")
    private val observation =
        WorkerIntegrationObservation(
            UUID.randomUUID(),
            at.minusSeconds(60),
            at,
            WorkerIntegrationFlags(false, true, true, true),
        )

    @Test
    fun freshnessExpiresAtNinetySecondsAndFutureReportsNeverClaimRecent() {
        assertTrue(workerIntegrationIsRecent(observation, at))
        assertTrue(workerIntegrationIsRecent(observation, at.plusSeconds(89)))
        assertFalse(workerIntegrationIsRecent(observation, at.plusSeconds(90)))
        assertFalse(workerIntegrationIsRecent(observation, at.minusNanos(1)))
    }
}
