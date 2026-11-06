package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.schema.tables.CollaborationSpaceRoomBindings.COLLABORATION_SPACE_ROOM_BINDINGS as B
import dev.fajar.hris.schema.tables.CollaborationSpaceRoomIntents.COLLABORATION_SPACE_ROOM_INTENTS as I
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSpaceBoundRoomRepositoryTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

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
        fun scope(
            actor: Actor = f.s.actor,
            space: UUID = f.s.space,
            server: String = "matrix.example.test",
        ) =
            success(
                f.s.tx(actor) { repository.ownedBoundRooms(space, actor.accountId, server, 10001) }
            )
        assertNull(read())
        assertTrue(scope().isEmpty())
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
        assertEquals(listOf(bound), scope())
        assertEquals(listOf(bound), scope(f.peerActor))
        assertTrue(scope(f.s.actor.copy(accountId = UUID.randomUUID())).isEmpty())
        assertTrue(scope(f.s.actor.copy(companyId = f.s.company)).isEmpty())
        assertTrue(scope(space = UUID.randomUUID()).isEmpty())
        assertTrue(scope(server = "other.example.test").isEmpty())
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
        fun scope() =
            success(
                f.s.tx {
                    repository.ownedBoundRooms(f.s.space, f.s.account, "matrix.example.test", 10001)
                }
            )
        assertEquals(listOf(original), scope())
        f.revokePeer()
        assertEquals(original, read())
        assertEquals(listOf(original), scope())
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
        var interrupted = false
        var scopeCalls = 0
        val source =
            object : CollaborationSpaceRoomDataSource by raw {
                override fun ownedBoundRooms(
                    spaceId: UUID,
                    accountId: UUID,
                    serverName: String,
                    limit: Int,
                ): List<CollaborationSpaceRoomBindingData> {
                    scopeCalls++
                    if (interrupted) throw InterruptedException("fictional scope interruption")
                    if (cancel) throw CancellationException("fictional scope cancellation")
                    return answer
                }

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
        fun scope(limit: Int = 10001) =
            f.s.tx {
                repository.ownedBoundRooms(f.s.space, f.s.account, "matrix.example.test", limit)
            }
        for (limit in listOf(0, -1, 10002, Int.MAX_VALUE)) assertInstanceOf(
            Result.Failed::class.java,
            scope(limit),
        )
        assertEquals(0, scopeCalls)
        assertInstanceOf(Result.Failed::class.java, read())
        assertInstanceOf(Result.Failed::class.java, scope())
        val original = rows.single()
        fun copy(id: UUID) =
            CollaborationSpaceRoomBindingData(
                original.intent.into(I).also { it.conversationId = id },
                original.binding.into(B).also {
                    it.conversationId = id
                    it.matrixRoomId = "!scope-$id:matrix.example.test"
                },
            )
        answer = listOf(copy(UUID(0, 1)), copy(UUID(0, 2)))
        assertEquals(2, success(scope()).size)
        assertInstanceOf(Result.Failed::class.java, scope(1))
        answer = answer.reversed()
        assertInstanceOf(Result.Failed::class.java, scope())
        answer = rows
        rows.single().intent.matrixServerName = "other.example.test"
        assertInstanceOf(Result.Failed::class.java, read())
        assertInstanceOf(Result.Failed::class.java, scope())
        cancel = true
        assertThrows(CancellationException::class.java) { read() }
        assertThrows(CancellationException::class.java) { scope() }
        cancel = false
        rows.single().intent.matrixServerName = "matrix.example.test"
        interrupted = true
        val before = scopeCalls
        assertThrows(InterruptedException::class.java) { scope() }
        assertEquals(before + 1, scopeCalls)
    }

    private fun <T> success(value: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, value)
        return (value as Result.Success).value
    }
}
