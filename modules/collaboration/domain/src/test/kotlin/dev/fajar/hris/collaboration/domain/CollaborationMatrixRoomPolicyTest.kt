package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationMatrixRoomPolicyTest {
    private val request =
        CollaborationMatrixDirectRoom(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
        )
    private val server = "matrix.example.test"
    private val creator =
        "@${collaborationMatrixLocalpart(request.companyId, request.creatorAccountId)}:$server"
    private val peer =
        "@${collaborationMatrixLocalpart(request.companyId, request.peerAccountId)}:$server"
    private val complete =
        CollaborationMatrixRoomObservation(
            creator,
            "12",
            false,
            CollaborationMatrixRoomMarker(
                request.companyId.toString(),
                request.conversationId.toString(),
                "DIRECT",
            ),
            emptyList(),
            false,
            "m.megolm.v1.aes-sha2",
            "invite",
            "invited",
            "forbidden",
            collaborationDirectRoomPower(),
            mapOf(creator to "join", peer to "invite"),
        )

    @Test
    fun matchingCompleteAndPartialRoomsHaveFiniteExplicitRepair() {
        assertEquals(
            Result.Success(CollaborationMatrixRoomRepair(emptySet(), false)),
            assessCollaborationDirectRoom(request, complete, server),
        )
        assertEquals(
            Result.Success(CollaborationMatrixRoomRepair(emptySet(), false)),
            assessCollaborationDirectRoom(
                request,
                complete.copy(members = mapOf(creator to "join", peer to "join")),
                server,
            ),
        )
        val partial =
            complete.copy(
                encryptionAlgorithm = null,
                joinRule = null,
                historyVisibility = null,
                guestAccess = null,
                power = null,
                members = mapOf(creator to "join"),
            )
        assertEquals(
            Result.Success(
                CollaborationMatrixRoomRepair(CollaborationMatrixRoomSetting.entries.toSet(), true)
            ),
            assessCollaborationDirectRoom(request, partial, server),
        )
    }

    @Test
    fun alienCreatorContextVersionOrAudienceCannotBeAdopted() {
        val marker = requireNotNull(complete.marker)
        val variants =
            listOf(
                complete.copy(creator = peer),
                complete.copy(roomVersion = "11"),
                complete.copy(federated = true),
                complete.copy(federated = null),
                complete.copy(marker = null),
                complete.copy(marker = marker.copy(companyId = UUID.randomUUID().toString())),
                complete.copy(marker = marker.copy(conversationId = UUID.randomUUID().toString())),
                complete.copy(marker = marker.copy(kind = "CLAIM")),
                complete.copy(additionalCreators = listOf(peer)),
                complete.copy(tombstoned = true),
                complete.copy(members = complete.members + ("@outsider:$server" to "join")),
                complete.copy(members = complete.members + (peer to "leave")),
                complete.copy(members = complete.members + (creator to "leave")),
            )
        for (observed in variants) assertEquals(
            "collaboration_room_mismatch",
            (assessCollaborationDirectRoom(request, observed, server) as Result.Failed).failure.code,
        )
    }

    @Test
    fun existingIncompatibleEncryptionHistoryOrPowerIsNeverRepairedByDowngrade() {
        val power = requireNotNull(complete.power)
        val variants =
            listOf(
                complete.copy(encryptionAlgorithm = "unknown"),
                complete.copy(joinRule = "public"),
                complete.copy(historyVisibility = "shared"),
                complete.copy(guestAccess = "can_join"),
                complete.copy(power = power.copy(users = mapOf(peer to 100))),
                complete.copy(power = power.copy(invite = 0)),
                complete.copy(
                    power = power.copy(events = power.events + ("m.room.encryption" to 0))
                ),
            )
        for (observed in variants) assertInstanceOf(
            Result.Failed::class.java,
            assessCollaborationDirectRoom(request, observed, server),
        )
    }
}
