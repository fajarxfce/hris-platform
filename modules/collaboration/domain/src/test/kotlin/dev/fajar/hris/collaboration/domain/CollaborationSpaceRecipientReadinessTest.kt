package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRecipientDevice
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceRecipientReadinessTest {
    private val space = UUID.randomUUID()
    private val account = UUID.randomUUID()
    private val deviceId = UUID.randomUUID()
    private val at = Instant.parse("2026-11-09T12:00:00Z")
    private val server = "matrix.example.test"
    private val user = "@${collaborationSpaceMatrixLocalpart(space, account)}:$server"
    private val device =
        CollaborationSpaceRecipientDevice(
            space,
            account,
            deviceId,
            server,
            user,
            "HRISG_${deviceId.toString().replace("-", "")}",
            "a".repeat(64),
            at.plusSeconds(600),
            true,
        )

    @Test
    fun oneConfirmedEligibleDeviceIsRequired() {
        assertEquals(Result.Success(false), read(emptyList()))
        assertEquals(Result.Success(false), read(listOf(device.copy(publicationConfirmed = false))))
        assertEquals(Result.Success(true), read(listOf(device)))
        val other = UUID.randomUUID()
        assertEquals(
            Result.Success(true),
            read(
                listOf(
                    device,
                    device.copy(
                        deviceId = other,
                        matrixDeviceId = "HRISG_${other.toString().replace("-", "")}",
                        publicationConfirmed = false,
                    ),
                )
            ),
        )
    }

    @Test
    fun originalLegacyIdentityCanReceiveUnderItsCurrentLogicalGroupDevice() {
        val company = UUID.randomUUID()
        val physicalDevice = UUID.randomUUID()
        val legacyUser = "@${collaborationMatrixLocalpart(company, account)}:$server"
        val original =
            device.copy(
                matrixUserId = legacyUser,
                matrixDeviceId = "HRIS_${physicalDevice.toString().replace("-", "")}",
            )
        assertEquals(Result.Success(true), read(listOf(original), legacyUser))
        assertInstanceOf(Result.Failed::class.java, read(listOf(original)))
    }

    @Test
    fun malformedForeignExpiredOrDuplicatedCandidatesCannotClaimReadiness() {
        for (candidate in
            listOf(
                device.copy(spaceId = UUID.randomUUID()),
                device.copy(accountId = UUID.randomUUID()),
                device.copy(deviceId = UUID(0, 0)),
                device.copy(serverName = "other.example.test"),
                device.copy(matrixUserId = "@unknown:$server"),
                device.copy(matrixDeviceId = "unknown-device"),
                device.copy(fingerprint = "unknown-key"),
                device.copy(receiveUntil = at),
                device.copy(receiveUntil = at.minusSeconds(1)),
                device.copy(receiveUntil = at.plusNanos(1)),
            )) {
            assertEquals(
                "collaboration_recipients_changed",
                (read(listOf(candidate)) as Result.Failed).failure.code,
            )
        }
        assertInstanceOf(Result.Failed::class.java, read(listOf(device, device)))
        val six =
            List(6) {
                val id = UUID.randomUUID()
                device.copy(
                    deviceId = id,
                    matrixDeviceId = "HRISG_${id.toString().replace("-", "")}",
                )
            }
        assertInstanceOf(Result.Failed::class.java, read(six))
    }

    private fun read(values: List<CollaborationSpaceRecipientDevice>, identity: String = user) =
        collaborationSpaceRecipientReady(space, account, server, identity, at, values)
}
