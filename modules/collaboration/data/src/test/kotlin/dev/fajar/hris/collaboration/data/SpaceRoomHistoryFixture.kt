package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import java.time.Instant
import java.util.UUID

/** Public protocol metadata only; no database/native or completed-device authority. */
internal class SpaceRoomHistoryFixture(private val legacy: Boolean = false) {
    val space = UUID.randomUUID()
    val pair = listOf(UUID.randomUUID(), UUID.randomUUID()).sortedBy(UUID::toString)
    val device = UUID.randomUUID()
    val company = UUID.randomUUID()
    val originalDevice = UUID.randomUUID()
    val conversation = UUID.randomUUID()
    val server = "matrix.example.test"
    val roomId = "!original-room-$conversation:$server"
    val at = Instant.parse("2026-11-04T12:00:00Z")

    fun user(account: UUID): String =
        if (legacy) "@${collaborationMatrixLocalpart(company, account)}:$server"
        else "@${collaborationSpaceMatrixLocalpart(space, account)}:$server"

    val owner =
        CollaborationSpaceHistoryOwner(
            space,
            pair[0],
            device,
            server,
            user(pair[0]),
            if (legacy) "HRIS_${compact(originalDevice)}" else "HRISG_${compact(device)}",
            if (legacy) CollaborationLegacyDevicePosition(company, originalDevice) else null,
        )
    val room =
        CollaborationSpaceBoundRoom(
            CollaborationSpaceRoomIntent(
                space,
                conversation,
                pair[0],
                pair[1],
                pair[1],
                pair[1],
                server,
                user(pair[0]),
                user(pair[1]),
                if (legacy) CollaborationSpaceRoomOrigin.LEGACY
                else CollaborationSpaceRoomOrigin.SPACE,
                if (legacy)
                    CollaborationSpaceOriginalRoom(
                        company,
                        UUID.randomUUID(),
                        roomId,
                        at.minusSeconds(120),
                    )
                else null,
                at.minusSeconds(60),
            ),
            CollaborationSpaceRoomBinding(
                space,
                conversation,
                pair[0],
                0,
                UUID.randomUUID(),
                roomId,
                at.minusSeconds(30),
                pair[1],
            ),
        )

    private fun compact(id: UUID) = id.toString().replace("-", "")
}
