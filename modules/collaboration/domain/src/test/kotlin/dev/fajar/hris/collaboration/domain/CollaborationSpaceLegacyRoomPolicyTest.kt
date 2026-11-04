package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceLegacyRoomPolicyTest {
    private val space = UUID.randomUUID()
    private val company = UUID.randomUUID()
    private val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val second = UUID.fromString("80000000-0000-0000-0000-000000000001")
    private val at = Instant.parse("2026-11-04T00:00:00Z")
    private val server = "matrix.example.test"
    private val group =
        CollaborationSpaceDirectConversation(UUID.randomUUID(), space, first, second, second, at)

    private fun member(account: UUID) =
        CollaborationSpaceMember(
            space,
            account,
            "Fictional",
            CollaborationSpacePublicIdentity(
                server,
                "@${collaborationMatrixLocalpart(company, account)}:$server",
                at,
            ),
        )

    private val original =
        CollaborationSpaceLegacyConversation(
            space,
            CollaborationDirectConversation(UUID.randomUUID(), company, first, second, second, at),
            CollaborationLegacyRoomMetadata(server, "!original:$server", at, first),
        )

    private fun candidate() =
        (prepareCollaborationSpaceLegacyRoom(
                group,
                member(first),
                member(second),
                original,
                server,
                at,
            )
                as Result.Success)
            .value

    private fun observed() =
        CollaborationMatrixRoomObservation(
            creator = member(first).identity!!.userId,
            roomVersion = "12",
            federated = false,
            marker =
                CollaborationMatrixRoomMarker(
                    company.toString(),
                    original.conversation.id.toString(),
                    "DIRECT",
                ),
            additionalCreators = emptyList(),
            tombstoned = false,
            encryptionAlgorithm = "m.megolm.v1.aes-sha2",
            joinRule = "invite",
            historyVisibility = "invited",
            guestAccess = "forbidden",
            power = collaborationDirectRoomPower(),
            members =
                mapOf(
                    member(first).identity!!.userId to "join",
                    member(second).identity!!.userId to "invite",
                ),
        )

    @Test
    fun originalRoomUsesItsBindingCreatorAndRequiresHealthyOriginalState() {
        val room = candidate()
        assertEquals(first, room.creatorAccountId)
        assertEquals(second, original.conversation.createdBy)
        assertEquals(original.conversation.id, room.conversationId)
        assertNotEquals(group.id, room.conversationId)
        assertEquals(company, room.companyId)
        assertEquals(Result.Success(Unit), assessCollaborationSpaceLegacyRoom(room, observed()))
        assertEquals(
            Result.Success(Unit),
            assessCollaborationSpaceLegacyRoom(
                room,
                observed()
                    .copy(
                        members = observed().members + (member(second).identity!!.userId to "join")
                    ),
            ),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            assessCollaborationSpaceLegacyRoom(room.copy(creatorAccountId = second), observed()),
        )
    }

    @Test
    fun missingOrForeignCanonicalMetadataCannotAdoptOriginalHistory() {
        val cases: List<() -> Result<*>> =
            listOf(
                {
                    prepareCollaborationSpaceLegacyRoom(
                        group,
                        member(first).copy(identity = null),
                        member(second),
                        original,
                        server,
                        at,
                    )
                },
                {
                    prepareCollaborationSpaceLegacyRoom(
                        group,
                        member(second),
                        member(first),
                        original,
                        server,
                        at,
                    )
                },
                {
                    prepareCollaborationSpaceLegacyRoom(
                        group,
                        member(first),
                        member(second).copy(spaceId = UUID.randomUUID()),
                        original,
                        server,
                        at,
                    )
                },
                {
                    prepareCollaborationSpaceLegacyRoom(
                        group,
                        member(first),
                        member(second),
                        original.copy(spaceId = UUID.randomUUID()),
                        server,
                        at,
                    )
                },
                {
                    prepareCollaborationSpaceLegacyRoom(
                        group,
                        member(first),
                        member(second),
                        original.copy(room = null),
                        server,
                        at,
                    )
                },
                {
                    prepareCollaborationSpaceLegacyRoom(
                        group,
                        member(first),
                        member(second),
                        original.copy(room = original.room!!.copy(createdBy = UUID.randomUUID())),
                        server,
                        at,
                    )
                },
                {
                    prepareCollaborationSpaceLegacyRoom(
                        group,
                        member(first),
                        member(second),
                        original.copy(
                            conversation = original.conversation.copy(companyId = UUID.randomUUID())
                        ),
                        server,
                        at,
                    )
                },
                {
                    prepareCollaborationSpaceLegacyRoom(
                        group,
                        member(first),
                        member(second),
                        original.copy(
                            conversation =
                                original.conversation.copy(secondAccountId = UUID.randomUUID())
                        ),
                        server,
                        at,
                    )
                },
                {
                    prepareCollaborationSpaceLegacyRoom(
                        group,
                        member(first),
                        member(second),
                        original,
                        "foreign.example.test",
                        at,
                    )
                },
                {
                    prepareCollaborationSpaceLegacyRoom(
                        group.copy(version = 1),
                        member(first),
                        member(second),
                        original,
                        server,
                        at,
                    )
                },
                {
                    prepareCollaborationSpaceLegacyRoom(
                        group,
                        member(first),
                        member(second),
                        original.copy(room = original.room!!.copy(boundAt = at.minusSeconds(1))),
                        server,
                        at,
                    )
                },
                {
                    prepareCollaborationSpaceLegacyRoom(
                        group,
                        member(first),
                        member(second),
                        original,
                        server,
                        at.minusSeconds(1),
                    )
                },
            )
        cases.forEachIndexed { index, call ->
            assertInstanceOf(Result.Failed::class.java, call(), "case $index")
        }
        for (user in
            listOf(
                "@hrisg_${space.toString().replace("-", "")}_${second.toString().replace("-", "")}:$server",
                "@${collaborationMatrixLocalpart(UUID.randomUUID(), second)}:$server",
                member(first).identity!!.userId,
            )) {
            val peer = member(second).copy(identity = member(second).identity!!.copy(userId = user))
            assertInstanceOf(
                Result.Failed::class.java,
                prepareCollaborationSpaceLegacyRoom(
                    group,
                    member(first),
                    peer,
                    original,
                    server,
                    at,
                ),
            )
        }
    }

    @Test
    fun historicalMissingSettingsOrInvitesDoNotAuthorizeRepair() {
        val healthy = observed()
        val invalid =
            listOf(
                healthy.copy(
                    marker =
                        CollaborationMatrixRoomMarker(
                            space.toString(),
                            group.id.toString(),
                            "DIRECT",
                        )
                ),
                healthy.copy(encryptionAlgorithm = null),
                healthy.copy(joinRule = null),
                healthy.copy(historyVisibility = null),
                healthy.copy(guestAccess = null),
                healthy.copy(power = null),
                healthy.copy(members = mapOf(member(first).identity!!.userId to "join")),
                healthy.copy(members = healthy.members + ("@foreign:$server" to "invite")),
                healthy.copy(
                    members = healthy.members + (member(second).identity!!.userId to "leave")
                ),
                healthy.copy(federated = true),
                healthy.copy(additionalCreators = listOf(member(second).identity!!.userId)),
                healthy.copy(tombstoned = true),
                healthy.copy(encryptionAlgorithm = "unknown"),
            )
        invalid.forEachIndexed { index, state ->
            assertInstanceOf(
                Result.Failed::class.java,
                assessCollaborationSpaceLegacyRoom(candidate(), state),
                "case $index",
            )
        }
    }
}
