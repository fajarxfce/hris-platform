package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.push.domain.entities.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpacePushDeliveryPolicyTest {
    private val at = Instant.parse("2026-11-04T20:00:00Z")
    private val binding = run {
        val space = UUID.randomUUID()
        val account = UUID.randomUUID()
        val device = UUID.randomUUID()
        CollaborationSpacePushBinding(
            space,
            account,
            device,
            UUID.randomUUID(),
            "@${collaborationSpaceMatrixLocalpart(space, account)}:matrix.example.test",
            "HRISG_" + device.toString().replace("-", ""),
            0,
            at.minusSeconds(30),
            null,
        )
    }
    private val device =
        CollaborationSpaceDevice(
            binding.deviceId,
            binding.spaceId,
            binding.accountId,
            binding.matrixUserId,
            binding.matrixDeviceId,
            CollaborationSpaceDeviceOrigin.SPACE,
            null,
            null,
            binding.nativeSessionId,
            CollaborationPlatform.ANDROID,
            "Fictional",
            CollaborationDeviceState.ACTIVE,
            1,
            at.minusSeconds(100),
            at.minusSeconds(90),
            null,
        )
    private val target =
        CollaborationSpacePushTarget.Ready(
            binding,
            CollaborationSpaceIdentity(
                binding.spaceId,
                binding.accountId,
                "matrix.example.test",
                binding.matrixUserId,
                CollaborationSpaceIdentityOrigin.SPACE,
                null,
                at.minusSeconds(100),
            ),
            device,
            CollaborationSpaceProviderState(
                binding.spaceId,
                binding.accountId,
                binding.deviceId,
                binding.nativeSessionId,
                binding.matrixUserId,
                binding.matrixDeviceId,
                1,
                at.minusSeconds(90),
                at.minusSeconds(80),
                at.plusSeconds(100),
            ),
            1,
            0,
            CollaborationSpaceGatewaySession(
                UUID.randomUUID(),
                binding.spaceId,
                binding.accountId,
                binding.deviceId,
                binding.nativeSessionId,
                CollaborationPlatform.ANDROID,
                28,
                1,
                at.minusSeconds(60),
                at.minusSeconds(50),
                at.plusSeconds(100),
                at.plusSeconds(90),
            ),
            NativePushRegistration(
                binding.nativeSessionId,
                binding.accountId,
                PushPlatform.ANDROID,
                true,
                1,
                at.minusSeconds(90),
                at.minusSeconds(60),
                at.plusSeconds(100),
            ),
            at,
            at.plusSeconds(30),
        )
    private val lease =
        CollaborationSpacePushLease(
            binding.spaceId,
            binding.accountId,
            binding.deviceId,
            binding.nativeSessionId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            999999999999,
            at.plusSeconds(60),
        )
    private val event = CollaborationPushEvent("!" + "a".repeat(254), "$" + "b".repeat(254))
    private val head =
        CollaborationSpacePushHead(
            binding.spaceId,
            binding.accountId,
            binding.deviceId,
            binding.nativeSessionId,
            lease.revision,
            0,
            at.minusSeconds(60),
            at,
            at.plusSeconds(86400),
            lease.revision,
            event,
            at,
            1,
            at,
            lease.revision,
            1,
            lease,
        )

    private fun message(
        head: CollaborationSpacePushHead = this.head,
        lease: CollaborationSpacePushLease = this.lease,
        target: CollaborationSpacePushTarget.Ready = this.target,
        now: Instant = at,
    ) = collaborationSpacePushMessage(head, lease, target, "fixture-token", now, at.plusSeconds(20))

    @Test
    fun maximumEnvelopeRemainsOpaqueSmallAndKeepsGroupDistinctFromCompany() {
        val sent = (message() as Result.Success).value
        assertEquals(12, sent.data.size)
        assertTrue(
            sent.data.entries.sumOf { it.key.toByteArray().size + it.value.toByteArray().size } <=
                1024
        )
        assertEquals(binding.spaceId.toString(), sent.data["spaceId"])
        assertFalse(sent.data.containsKey("companyId"))
        assertEquals("collaboration.space_chat_available", sent.data["eventCode"])
        assertEquals(at.plusSeconds(20), sent.dispatchDeadline)
        assertEquals(at.plusSeconds(86400), sent.expiresAt)
        assertEquals(PushRetention.RECOVERABLE, sent.retention)
        assertEquals(PushDelivery.Visible("HRIS", "New chat activity", "hris_chat"), sent.delivery)
    }

    @Test
    fun shorterHeadAndTargetDeadlinesBoundAnOtherwiseValidRetainedLease() {
        assertEquals(
            at.plusSeconds(5),
            (message(head.copy(expiresAt = at.plusSeconds(5))) as Result.Success)
                .value
                .dispatchDeadline,
        )
        assertEquals(
            at.plusSeconds(2),
            (message(target = target.copy(expiresAt = at.plusSeconds(2))) as Result.Success)
                .value
                .dispatchDeadline,
        )
        for (result in
            listOf(
                message(now = at.minusSeconds(1)),
                message(now = at.plusSeconds(20)),
                message(head.copy(expiresAt = at)),
                message(target = target.copy(expiresAt = at)),
                message(lease = lease.copy(token = UUID.randomUUID())),
                message(head.copy(accountId = UUID.randomUUID())),
            )) assertInstanceOf(Result.Failed::class.java, result)
    }

    @Test
    fun newerVisibleRevisionCannotBeAttributedToAnOlderLease() {
        val old = lease.copy(revision = lease.revision - 1)
        val pending = head.copy(lease = old, attemptRevision = old.revision)
        val sent = (message(pending, old) as Result.Success).value
        assertInstanceOf(PushDelivery.Background::class.java, sent.delivery)
        assertFalse(sent.data.containsKey("matrixEventId"))
        assertFalse(sent.data.containsKey("matrixRoomId"))
        assertNotEquals((message() as Result.Success).value.data["eventId"], sent.data["eventId"])
    }
}
