package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSpaceBoundRoomRepositoryTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun exactBoundRoomIsSharedOnlyByItsOriginalParticipantsUnderRootScope() {
        val f = SpaceKeyExchangeFixture(postgres)
        val repository =
            StoredCollaborationSpaceRoomRepository(
                PostgresCollaborationSpaceRoomDataSource(f.base.sql)
            )
        fun read(actor: Actor = f.s.actor, room: String = f.boundRoom, space: UUID = f.s.space) =
            success(
                f.s.tx(actor) {
                    repository.boundRoom(space, actor.accountId, "matrix.example.test", room)
                }
            )
        assertNull(read())
        f.bind()
        val bound = requireNotNull(read())
        assertEquals(f.s.space, bound.intent.spaceId)
        assertEquals(f.conversation, bound.binding.conversationId)
        assertEquals(f.boundRoom, bound.binding.roomId)
        assertEquals(
            setOf(f.s.account, f.peer),
            setOf(bound.intent.firstAccountId, bound.intent.secondAccountId),
        )
        assertEquals(
            setOf(f.ownKeys.userId, f.peerKeys.userId),
            setOf(bound.intent.firstMatrixUserId, bound.intent.secondMatrixUserId),
        )
        assertEquals(bound, read(f.peerActor))
        assertNull(read(f.s.actor.copy(accountId = UUID.randomUUID())))
        assertNull(read(f.s.actor.copy(companyId = f.s.company)))
        assertNull(read(room = "!unrelated:matrix.example.test"))
        assertNull(read(space = UUID.randomUUID()))
    }

    @Test
    fun ownedRoomMetadataSurvivesPeerOffboardingWithoutGrantingNewReceivingAccess() {
        val f = SpaceKeyExchangeFixture(postgres)
        f.reserve()
        f.reserve(f.peer)
        f.bind()
        val repository =
            StoredCollaborationSpaceRoomRepository(
                PostgresCollaborationSpaceRoomDataSource(f.base.sql)
            )
        fun read() =
            success(
                f.s.tx {
                    repository.boundRoom(f.s.space, f.s.account, "matrix.example.test", f.boundRoom)
                }
            )
        val original = requireNotNull(read())
        f.revokePeer()
        assertEquals(original, read())
        assertTrue(
            success(f.s.tx { f.recipients.devices(f.s.space, setOf(f.peer), f.now()) }).isEmpty()
        )
    }

    @Test
    fun duplicateOrMalformedExactRowsFailClosedAndAcquisitionCancellationPropagates() {
        val f = SpaceKeyExchangeFixture(postgres)
        f.bind()
        val raw = PostgresCollaborationSpaceRoomDataSource(f.base.sql)
        val rows =
            success(
                f.s.tx {
                    Result.Success(
                        raw.boundRoom(f.s.space, f.s.account, "matrix.example.test", f.boundRoom)
                    )
                }
            )
        assertEquals(1, rows.size)
        var answer = rows + rows
        var cancel = false
        val source =
            object : CollaborationSpaceRoomDataSource by raw {
                override fun boundRoom(
                    spaceId: UUID,
                    accountId: UUID,
                    serverName: String,
                    roomId: String,
                ): List<CollaborationSpaceRoomBindingData> {
                    if (cancel) throw CancellationException("fictional bound room cancellation")
                    return answer
                }
            }
        val repository = StoredCollaborationSpaceRoomRepository(source)
        fun read() =
            f.s.tx {
                repository.boundRoom(f.s.space, f.s.account, "matrix.example.test", f.boundRoom)
            }
        assertInstanceOf(Result.Failed::class.java, read())
        answer = rows
        rows.single().intent.matrixServerName = "other.example.test"
        assertInstanceOf(Result.Failed::class.java, read())
        cancel = true
        assertThrows(CancellationException::class.java) { read() }
    }

    private fun <T> success(value: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, value)
        return (value as Result.Success).value
    }
}
