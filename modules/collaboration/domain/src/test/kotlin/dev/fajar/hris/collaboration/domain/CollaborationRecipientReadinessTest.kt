package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.CollaborationRecipientDevice
import dev.fajar.hris.collaboration.domain.policies.collaborationRecipientReady
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationRecipientReadinessTest {
    private val company = UUID.randomUUID()
    private val peer = UUID.randomUUID()
    private val now = Instant.parse("2026-11-08T00:00:00Z")
    private val device =
        CollaborationRecipientDevice(
            company,
            peer,
            UUID.randomUUID(),
            "a".repeat(64),
            now.plusSeconds(60),
            true,
        )

    @Test
    fun readinessRequiresOneConfirmedEligibleDevice() {
        assertEquals(Result.Success(false), read(emptyList()))
        assertEquals(Result.Success(false), read(listOf(device.copy(publicationConfirmed = false))))
        assertEquals(Result.Success(true), read(listOf(device)))
        assertEquals(
            Result.Success(true),
            read(
                listOf(
                    device,
                    device.copy(deviceId = UUID.randomUUID(), publicationConfirmed = false),
                )
            ),
        )
    }

    @Test
    fun malformedOrExpiredProjectionNeverClaimsReadiness() {
        for (values in
            listOf(
                listOf(device.copy(companyId = UUID.randomUUID())),
                listOf(device.copy(accountId = UUID.randomUUID())),
                listOf(device.copy(receiveUntil = now)),
                listOf(device.copy(receiveUntil = now.minusSeconds(1))),
                listOf(device, device),
                List(6) { device.copy(deviceId = UUID.randomUUID()) },
            )) assertEquals(
            "collaboration_recipients_changed",
            (read(values) as Result.Failed).failure.code,
        )
    }

    private fun read(values: List<CollaborationRecipientDevice>) =
        collaborationRecipientReady(company, peer, now, values)
}
