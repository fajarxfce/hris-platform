package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceMatrixRoomPolicyTest {
    private val server = "matrix.example.test"
    private val space = UUID.randomUUID()
    private val creator = UUID.randomUUID()
    private val peer = UUID.randomUUID()
    private val request =
        CollaborationSpaceMatrixDirectRoom(
            space,
            UUID.randomUUID(),
            creator,
            peer,
            "@hris_${compact(UUID.randomUUID())}_${compact(creator)}:$server",
            "@hris_${compact(UUID.randomUUID())}_${compact(peer)}:$server",
        )

    @Test
    fun canonicalUsersFromDifferentLegacyCompaniesKeepTheirIdentityInOneGroupRoom() {
        assertTrue(validCollaborationSpaceRoomRequest(request, server))
        assertEquals(
            "hrisg_dm_${compact(space)}_${compact(request.conversationId)}",
            collaborationSpaceMatrixRoomAliasLocalpart(request),
        )
        assertEquals(
            Result.Success(CollaborationMatrixRoomRepair(emptySet(), false)),
            assessCollaborationSpaceDirectRoom(request, observed(), server),
        )
        val fresh =
            request.copy(
                creatorUserId = "@hrisg_${compact(space)}_${compact(creator)}:$server",
                peerUserId = "@hrisg_${compact(space)}_${compact(peer)}:$server",
            )
        assertTrue(validCollaborationSpaceRoomRequest(fresh, server))
        assertEquals(
            Result.Success(CollaborationMatrixRoomRepair(emptySet(), false)),
            assessCollaborationSpaceDirectRoom(
                fresh,
                observed()
                    .copy(
                        creator = fresh.creatorUserId,
                        members = mapOf(fresh.creatorUserId to "join", fresh.peerUserId to "join"),
                    ),
                server,
            ),
        )
        assertNotEquals(
            collaborationMatrixRoomAliasLocalpart(
                CollaborationMatrixDirectRoom(space, request.conversationId, creator, peer)
            ),
            collaborationSpaceMatrixRoomAliasLocalpart(request),
        )
    }

    @Test
    fun onlyMissingInitialSettingsAndInviteCanBeProposedForRepair() {
        val partial =
            observed()
                .copy(
                    encryptionAlgorithm = null,
                    guestAccess = null,
                    members = mapOf(request.creatorUserId to "join"),
                )
        assertEquals(
            Result.Success(
                CollaborationMatrixRoomRepair(
                    setOf(
                        CollaborationMatrixRoomSetting.ENCRYPTION,
                        CollaborationMatrixRoomSetting.GUEST_ACCESS,
                    ),
                    true,
                )
            ),
            assessCollaborationSpaceDirectRoom(request, partial, server),
        )
        val invalid =
            listOf(
                partial.copy(marker = null),
                partial.copy(creator = request.peerUserId),
                partial.copy(
                    marker = partial.marker!!.copy(spaceId = UUID.randomUUID().toString())
                ),
                partial.copy(encryptionAlgorithm = "unsupported"),
                partial.copy(historyVisibility = "world_readable"),
                partial.copy(joinRule = "public"),
                partial.copy(guestAccess = "can_join"),
                partial.copy(federated = true),
                partial.copy(roomVersion = "11"),
                partial.copy(tombstoned = true),
                partial.copy(additionalCreators = listOf(request.peerUserId)),
                partial.copy(power = collaborationDirectRoomPower().copy(invite = 0)),
                partial.copy(members = partial.members + ("@foreign:$server" to "invite")),
                partial.copy(members = partial.members + (request.peerUserId to "ban")),
                partial.copy(members = mapOf(request.creatorUserId to "leave")),
            )
        invalid.forEach {
            assertInstanceOf(
                Result.Failed::class.java,
                assessCollaborationSpaceDirectRoom(request, it, server),
            )
        }
    }

    @Test
    fun foreignAccountSpaceAndServerCoordinatesNeverBecomeCanonicalByShapeAlone() {
        val invalid =
            listOf(
                request.copy(spaceId = UUID(0, 0)),
                request.copy(conversationId = UUID(0, 0)),
                request.copy(creatorAccountId = UUID(0, 0)),
                request.copy(peerAccountId = creator),
                request.copy(peerUserId = request.creatorUserId),
                request.copy(
                    peerUserId = "@hrisg_${compact(UUID.randomUUID())}_${compact(peer)}:$server"
                ),
                request.copy(
                    peerUserId = "@hrisg_${compact(space)}_${compact(UUID.randomUUID())}:$server"
                ),
                request.copy(peerUserId = request.peerUserId.replace(server, "other.example.test")),
                request.copy(peerUserId = "@unknown:$server"),
            )
        invalid.forEach {
            assertFalse(validCollaborationSpaceRoomRequest(it, server))
            assertInstanceOf(
                Result.Failed::class.java,
                assessCollaborationSpaceDirectRoom(it, observed(), server),
            )
        }
        assertFalse(validCollaborationSpaceRoomRequest(request, "invalid/server"))
    }

    @Test
    fun recoveringPeerRetainsActualCreatorAndRequiresItsOwnJoinedMembership() {
        val recovering =
            request.copy(
                creatorAccountId = request.peerAccountId,
                peerAccountId = request.creatorAccountId,
                creatorUserId = request.peerUserId,
                peerUserId = request.creatorUserId,
            )
        val joined =
            observed()
                .copy(
                    members = mapOf(request.creatorUserId to "join", request.peerUserId to "join")
                )
        assertEquals(
            Result.Success(
                CollaborationSpaceRoomAssessment(
                    creator,
                    CollaborationMatrixRoomRepair(emptySet(), false),
                )
            ),
            assessCollaborationSpaceRecoveredRoom(recovering, joined, server),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            assessCollaborationSpaceRecoveredRoom(recovering, observed(), server),
        )
        for (invalid in
            listOf(
                joined.copy(creator = "@foreign:$server"),
                joined.copy(marker = null),
                joined.copy(members = joined.members + ("@foreign:$server" to "join")),
                joined.copy(power = collaborationDirectRoomPower().copy(invite = 0)),
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                assessCollaborationSpaceRecoveredRoom(recovering, invalid, server),
            )
        }
    }

    @Test
    fun missingInitialStateCannotBeRepairedByImpersonatingTheOtherCreator() {
        val partial =
            observed()
                .copy(
                    encryptionAlgorithm = null,
                    members = mapOf(request.creatorUserId to "join", request.peerUserId to "join"),
                )
        assertEquals(
            Result.Success(
                CollaborationSpaceRoomAssessment(
                    creator,
                    CollaborationMatrixRoomRepair(
                        setOf(CollaborationMatrixRoomSetting.ENCRYPTION),
                        false,
                    ),
                )
            ),
            assessCollaborationSpaceRecoveredRoom(request, partial, server),
        )
        val recovering =
            request.copy(
                creatorAccountId = request.peerAccountId,
                peerAccountId = request.creatorAccountId,
                creatorUserId = request.peerUserId,
                peerUserId = request.creatorUserId,
            )
        val rejected = assessCollaborationSpaceRecoveredRoom(recovering, partial, server)
        assertInstanceOf(Result.Failed::class.java, rejected)
        assertEquals(
            "collaboration_room_recovery_requires_creator",
            (rejected as Result.Failed).failure.code,
        )
    }

    private fun observed() =
        CollaborationSpaceMatrixRoomObservation(
            request.creatorUserId,
            "12",
            false,
            CollaborationSpaceMatrixRoomMarker(
                space.toString(),
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
            mapOf(request.creatorUserId to "join", request.peerUserId to "invite"),
        )

    private fun compact(value: UUID) = value.toString().replace("-", "")
}
