package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.NativePushRegistration
import dev.fajar.hris.identity.domain.entities.PushPlatform
import dev.fajar.hris.push.domain.entities.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationPushDeliveryPolicyTest {
    private val now = Instant.parse("2026-11-01T00:00:00Z")
    private val binding =
        CollaborationPushBinding(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            now.minusSeconds(60),
            now.minusSeconds(30),
        )
    private val registration =
        NativePushRegistration(
            binding.nativeSessionId,
            binding.accountId,
            PushPlatform.ANDROID,
            true,
            1,
            now.minusSeconds(60),
            now.minusSeconds(30),
            now.plusSeconds(3600),
        )
    private val target =
        CollaborationPushTarget.Ready(binding, 1, 1, 1, 1, registration, now, now.plusSeconds(30))
    private val lease =
        CollaborationPushLease(
            binding.companyId,
            binding.accountId,
            binding.deviceId,
            binding.nativeSessionId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Long.MAX_VALUE - 1,
            now.plusSeconds(60),
        )
    private val event = CollaborationPushEvent("!" + "r".repeat(254), "$" + "e".repeat(254))
    private val head =
        CollaborationPushHead(
            binding.companyId,
            binding.accountId,
            binding.deviceId,
            binding.nativeSessionId,
            lease.revision,
            0,
            now.minusSeconds(60),
            now,
            now.plusSeconds(86400),
            lease.revision,
            event,
            now,
            1,
            now,
            lease.revision,
            1,
            lease,
        )

    private fun message(
        value: CollaborationPushHead = head,
        at: Instant = now,
        owned: CollaborationPushLease = lease,
    ): PushMessage =
        (collaborationPushMessage(
                value,
                owned,
                target,
                "fixture-token-only",
                at,
                now.plusSeconds(20),
            )
                as Result.Success)
            .value

    @Test
    fun largestOpaqueEnvelopeFitsTransportAndSeparatesRetentionFromOperationBudget() {
        val value = message()
        assertEquals(PushDelivery.Visible("HRIS", "New chat activity", "hris_chat"), value.delivery)
        assertEquals(PushRetention.RECOVERABLE, value.retention)
        assertEquals(now.plusSeconds(86400), value.expiresAt)
        assertEquals(now.plusSeconds(20), value.dispatchDeadline)
        assertEquals(12, value.data.size)
        assertTrue(
            value.data.entries.sumOf { it.key.toByteArray().size + it.value.toByteArray().size } <=
                1024
        )
        assertEquals(event.roomId, value.data["matrixRoomId"])
        assertEquals(event.eventId, value.data["matrixEventId"])
        assertEquals(
            setOf(
                "schemaVersion",
                "eventCode",
                "eventId",
                "accountId",
                "companyId",
                "nativeSessionId",
                "deviceId",
                "revision",
                "issuedAt",
                "expiresAt",
                "matrixRoomId",
                "matrixEventId",
            ),
            value.data.keys,
        )
        assertFalse(value.data.values.contains(value.token))
    }

    @Test
    fun retryKeepsWakeIdentityWhileANewerRevisionGetsAnotherIdentity() {
        val retry = lease.copy(token = UUID.randomUUID(), ownerId = UUID.randomUUID())
        assertEquals(
            message().data["eventId"],
            message(head.copy(lease = retry), owned = retry).data["eventId"],
        )
        val next = lease.copy(revision = lease.revision + 1)
        assertNotEquals(
            message().data["eventId"],
            message(head.copy(revision = next.revision, lease = next), owned = next).data["eventId"],
        )
    }

    @Test
    fun newerVisibleCallbackCannotBeSmuggledIntoAnOlderLeasedWake() {
        val later = head.copy(revision = lease.revision + 1, visibleRevision = lease.revision + 1)
        val value = message(later)
        assertEquals(PushDelivery.Background("hris_chat"), value.delivery)
        assertFalse(value.data.containsKey("matrixRoomId"))
        assertFalse(value.data.containsKey("matrixEventId"))
        val next = lease.copy(revision = later.revision)
        assertEquals(event, collaborationPushVisibleEvent(later.copy(lease = next), next, now))
    }

    @Test
    fun countOnlyAlreadyAcknowledgedExpiredAndFutureEventsStayQuiet() {
        for (value in
            listOf(
                head.copy(event = null, visibleAt = null, visibleRevision = 0),
                head.copy(
                    deliveredRevision = lease.revision - 1,
                    visibleRevision = lease.revision - 1,
                ),
                head.copy(visibleAt = now.minusSeconds(86400)),
                head.copy(visibleAt = now.plusSeconds(1)),
            )) assertEquals(PushDelivery.Background("hris_chat"), message(value).delivery)
    }

    @Test
    fun foreignReplacedExpiredAndAcknowledgedLeasesCannotPrepareDelivery() {
        for (value in
            listOf(
                head.copy(companyId = UUID.randomUUID()),
                head.copy(accountId = UUID.randomUUID()),
                head.copy(deviceId = UUID.randomUUID()),
                head.copy(nativeSessionId = UUID.randomUUID()),
                head.copy(lease = null),
                head.copy(lease = lease.copy(token = UUID.randomUUID())),
                head.copy(deliveredRevision = lease.revision),
                head.copy(revision = lease.revision - 1),
                head.copy(expiresAt = now),
                head.copy(expiresAt = now.plusMillis(999)),
            )) assertInstanceOf(
            Result.Failed::class.java,
            collaborationPushMessage(
                value,
                lease,
                target,
                "fixture-token-only",
                now,
                now.plusSeconds(20),
            ),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            collaborationPushMessage(
                head,
                lease,
                target,
                "fixture-token-only",
                now.minusSeconds(1),
                now.plusSeconds(20),
            ),
        )
    }

    @Test
    fun onlyTransientOutcomesHaveFiniteBackoffWithinRetention() {
        val unavailable = Failure(FailureKind.UNAVAILABLE, "fixture_unavailable")
        assertEquals(
            listOf(5L, 10L, 20L, 40L, 80L, 160L, 300L),
            (1..7).map {
                collaborationPushRetryAt(unavailable, it, now, now.plusSeconds(86400))!!
                    .epochSecond - now.epochSecond
            },
        )
        for (attempt in listOf(0, 8, 9)) assertNull(
            collaborationPushRetryAt(unavailable, attempt, now, head.expiresAt)
        )
        assertNull(collaborationPushRetryAt(unavailable, 1, now, now.plusSeconds(5)))
        assertNull(
            collaborationPushRetryAt(
                Failure(FailureKind.VALIDATION, "fixture_permanent"),
                1,
                now,
                head.expiresAt,
            )
        )
        for (retry in listOf("999999999999", "300")) assertEquals(
            now.plusSeconds(300),
            collaborationPushRetryAt(
                Failure(
                    FailureKind.RATE_LIMITED,
                    "fixture_limit",
                    parameters = mapOf("retryAfterSeconds" to retry),
                ),
                1,
                now,
                head.expiresAt,
            ),
        )
    }
}
