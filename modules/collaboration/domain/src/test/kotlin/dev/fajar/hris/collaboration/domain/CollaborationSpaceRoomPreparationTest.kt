package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceRoomPreparationTest {
    private val at = Instant.parse("2026-11-04T10:00:00Z")
    private val space = UUID.fromString("b3992b3b-1e4f-47db-a7ec-08ddc1b289a3")
    private val company = UUID.fromString("fe119b31-32d2-4582-aa80-0e51b046c8dd")
    private val first = UUID.fromString("71c05f36-4d90-4714-b329-f92270f0588e")
    private val second = UUID.fromString("ddce83cf-c59a-476f-af59-c11ad4a73172")
    private val conversation =
        CollaborationSpaceDirectConversation(
            UUID.randomUUID(),
            space,
            first,
            second,
            second,
            at.minusSeconds(3),
        )
    private val server = "matrix.example.test"

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun member(account: UUID, original: UUID? = company) =
        CollaborationSpaceMember(
            space,
            account,
            "Fictional",
            CollaborationSpacePublicIdentity(
                server,
                if (original == null) "@hrisg_${compact(space)}_${compact(account)}:$server"
                else "@hris_${compact(original)}_${compact(account)}:$server",
                at.minusSeconds(4),
            ),
        )

    private fun prepare(
        a: CollaborationSpaceMember? = member(first),
        b: CollaborationSpaceMember? = member(second),
        c: CollaborationSpaceDirectConversation = conversation,
    ) = prepareCollaborationSpaceRoomParticipants(c, a, b, server, at)

    private fun original(bound: Boolean = true) =
        CollaborationSpaceLegacyConversation(
            space,
            CollaborationDirectConversation(
                UUID.randomUUID(),
                company,
                first,
                second,
                first,
                at.minusSeconds(10),
            ),
            if (bound)
                CollaborationLegacyRoomMetadata(
                    server,
                    "!retained:$server",
                    at.minusSeconds(5),
                    second,
                )
            else null,
        )

    private fun <T> success(value: Result<T>) = (value as Result.Success).value

    @Test
    fun onlyTheSameOriginalCanonicalCompanySharesAnOriginalPairRoute() {
        assertEquals(company, success(prepare()).originalCompanyId)
        for ((a, b) in
            listOf(
                member(first, null) to member(second, null),
                member(first) to member(second, UUID.randomUUID()),
                member(first) to member(second, null),
            )) {
            val pair = success(prepare(a, b))
            assertNull(pair.originalCompanyId)
            val intent = success(prepareCollaborationSpaceRoomIntent(pair, second, null, at))
            assertEquals(CollaborationSpaceRoomOrigin.SPACE, intent.origin)
            assertEquals(second, intent.matrixCreatorAccountId)
            assertEquals(a.identity!!.userId, intent.firstMatrixUserId)
            assertEquals(b.identity!!.userId, intent.secondMatrixUserId)
        }
    }

    @Test
    fun historicalCreatorAndCoordinatesSurviveANewRequestByTheOtherParticipant() {
        val previous = original()
        val pair = success(prepare())
        val intent = success(prepareCollaborationSpaceRoomIntent(pair, first, previous, at))
        val retained = requireNotNull(intent.original)
        val oldRoom = requireNotNull(previous.room)
        assertEquals(first, intent.requestedBy)
        assertEquals(second, intent.matrixCreatorAccountId)
        assertEquals(CollaborationSpaceRoomOrigin.LEGACY, intent.origin)
        assertEquals(previous.conversation.companyId, retained.companyId)
        assertEquals(previous.conversation.id, retained.conversationId)
        assertEquals(oldRoom.roomId, retained.roomId)
        assertEquals(oldRoom.boundAt, retained.boundAt)
        assertEquals(conversation.id, intent.conversationId)
        assertEquals(conversation.spaceId, intent.spaceId)
    }

    @Test
    fun unboundOrForeignOriginalHistoryNeverAuthorizesAReplacementIntent() {
        val pair = success(prepare())
        val pending = prepareCollaborationSpaceRoomIntent(pair, first, original(bound = false), at)
        assertEquals(
            "collaboration_original_room_recovery_required",
            (pending as Result.Failed).failure.code,
        )
        val previous = original()
        val oldRoom = requireNotNull(previous.room)
        for (bad in
            listOf(
                previous.copy(spaceId = UUID.randomUUID()),
                previous.copy(
                    conversation = previous.conversation.copy(companyId = UUID.randomUUID())
                ),
                previous.copy(
                    conversation = previous.conversation.copy(secondAccountId = UUID.randomUUID())
                ),
                previous.copy(room = oldRoom.copy(createdBy = UUID.randomUUID())),
                previous.copy(room = oldRoom.copy(boundAt = at.plusSeconds(1))),
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                prepareCollaborationSpaceRoomIntent(pair, first, bad, at),
            )
        }
        assertInstanceOf(
            Result.Failed::class.java,
            prepareCollaborationSpaceRoomIntent(
                pair.copy(originalCompanyId = UUID.randomUUID()),
                first,
                null,
                at,
            ),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            prepareCollaborationSpaceRoomIntent(pair, UUID.randomUUID(), null, at),
        )
    }

    @Test
    fun missingSelectionWrongCanonicalAccountAndInvalidTimeCannotChooseHistory() {
        assertInstanceOf(Result.Failed::class.java, prepare(a = null))
        assertInstanceOf(
            Result.Failed::class.java,
            prepare(b = member(second).copy(identity = null)),
        )
        val a = member(first)
        val selected = requireNotNull(a.identity)
        for (bad in
            listOf(
                a.copy(spaceId = UUID.randomUUID()),
                a.copy(accountId = second),
                a.copy(identity = selected.copy(userId = member(second).identity!!.userId)),
                a.copy(identity = selected.copy(serverName = "elsewhere.test")),
                a.copy(identity = selected.copy(selectedAt = at.plusSeconds(1))),
                a.copy(identity = selected.copy(selectedAt = at.minusSeconds(1).plusNanos(1))),
            )) {
            assertInstanceOf(Result.Failed::class.java, prepare(a = bad))
        }
        assertInstanceOf(Result.Failed::class.java, prepare(c = conversation.copy(version = 1)))
        assertInstanceOf(
            Result.Failed::class.java,
            prepare(c = conversation.copy(firstAccountId = second, secondAccountId = first)),
        )
        assertInstanceOf(Result.Failed::class.java, prepare(c = conversation.copy(id = UUID(0, 0))))
    }
}
