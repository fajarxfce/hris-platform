package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.io.IOException
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Actual provider control/key continuity. This is not a client SDK encrypted-message proof. */
class CollaborationSpaceMatrixRoomInteropTest {
    @Test
    fun retainedCanonicalUsersFromDifferentCompaniesShareOneGroupRoomAcrossRestart() {
        MatrixProviderFixture("10m").use { f ->
            f.start()
            val original = f.login()
            val raw = HttpMatrixClientDataSource(f.client, f.config, f.clock)
            val matrix =
                InternalCollaborationSpaceMatrixRepository(
                    HmacMatrixJwtDataSource(f.config),
                    raw,
                    f.clock,
                )
            val keys = InternalCollaborationMatrixKeyRepository(raw)
            val originalKeys = MatrixKeyFixture(original.userId, original.deviceId)
            fun deadline() = f.clock.instant().plusSeconds(20)
            success(
                keys.upload(
                    CollaborationMatrixKeyUpload(originalKeys.device),
                    original.accessToken,
                    deadline(),
                )
            )
            val space = UUID.randomUUID()
            val at = f.clock.instant()
            val retained =
                success(
                    matrix.login(
                        CollaborationSpaceMatrixLogin(
                            space,
                            f.request.accountId,
                            UUID.randomUUID(),
                            f.request.companyId,
                            CollaborationLegacyDevicePosition(
                                f.request.companyId,
                                f.request.deviceId,
                            ),
                            at,
                            at.plusSeconds(60),
                        )
                    )
                )
            assertEquals(original.userId, retained.userId)
            assertEquals(original.deviceId, retained.deviceId)
            val otherCompany = UUID.randomUUID()
            val peerAccount = UUID.randomUUID()
            val peerUser =
                "@${collaborationMatrixLocalpart(otherCompany, peerAccount)}:${f.config.serverName}"
            val request =
                CollaborationSpaceMatrixDirectRoom(
                    space,
                    UUID.randomUUID(),
                    f.request.accountId,
                    peerAccount,
                    retained.userId,
                    peerUser,
                )
            val rooms = InternalCollaborationSpaceMatrixRoomRepository(raw, f.config.serverName)
            assertNull(success(rooms.resolve(request, retained.accessToken, deadline())))
            val room =
                (success(rooms.create(request, retained.accessToken, deadline()))
                        as CollaborationMatrixRoomCreation.Created)
                    .roomId
            assertEquals(room, success(rooms.resolve(request, retained.accessToken, deadline())))
            assertEquals(
                CollaborationMatrixRoomCreation.AliasInUse,
                success(rooms.create(request, retained.accessToken, deadline())),
            )
            assertEquals(
                Result.Success(CollaborationMatrixRoomRepair(emptySet(), false)),
                assessCollaborationSpaceDirectRoom(
                    request,
                    success(rooms.observe(room, retained.accessToken, deadline())),
                    f.config.serverName,
                ),
            )
            // The same conversation UUID does not create a company-scoped alias.
            assertNull(
                success(
                    InternalCollaborationMatrixRoomRepository(raw, f.config.serverName)
                        .resolve(
                            CollaborationMatrixDirectRoom(
                                f.request.companyId,
                                request.conversationId,
                                f.request.accountId,
                                peerAccount,
                            ),
                            retained.accessToken,
                            deadline(),
                        )
                )
            )
            val peerAt = f.clock.instant()
            val peer =
                success(
                    f.repository.login(
                        f.request.copy(
                            companyId = otherCompany,
                            accountId = peerAccount,
                            deviceId = UUID.randomUUID(),
                            issuedAt = peerAt,
                            expiresAt = peerAt.plusSeconds(60),
                        )
                    )
                )
            assertEquals(peerUser, peer.userId)
            raw.join(room, peer.accessToken, deadline())
            val peerKeys = MatrixKeyFixture(peer.userId, peer.deviceId)
            success(
                keys.upload(
                    CollaborationMatrixKeyUpload(peerKeys.device),
                    peer.accessToken,
                    deadline(),
                )
            )
            val query =
                CollaborationMatrixKeyQuery(
                    mapOf(
                        retained.userId to listOf(retained.deviceId),
                        peer.userId to listOf(peer.deviceId),
                    )
                )
            val expected =
                mapOf(
                    retained.userId to mapOf(retained.deviceId to originalKeys.device),
                    peer.userId to mapOf(peer.deviceId to peerKeys.device),
                )
            assertEquals(
                expected,
                success(keys.deviceKeys(query, retained.accessToken, deadline())).deviceKeys,
            )
            val joined = success(rooms.observe(room, peer.accessToken, deadline()))
            assertEquals(mapOf(retained.userId to "join", peer.userId to "join"), joined.members)
            assertEquals(
                Result.Success(CollaborationMatrixRoomRepair(emptySet(), false)),
                assessCollaborationSpaceDirectRoom(request, joined, f.config.serverName),
            )
            f.restart()
            // The owned container can receive a new mapped port on restart. Reacquire its
            // current endpoint without replacing the original provider identity or keys.
            val restartedRaw = HttpMatrixClientDataSource(f.client, f.config, f.clock)
            val restartedRooms =
                InternalCollaborationSpaceMatrixRoomRepository(restartedRaw, f.config.serverName)
            assertEquals(
                room,
                success(restartedRooms.resolve(request, retained.accessToken, deadline())),
            )
            assertEquals(
                joined,
                success(restartedRooms.observe(room, retained.accessToken, deadline())),
            )
            assertEquals(
                expected,
                success(
                        InternalCollaborationMatrixKeyRepository(restartedRaw)
                            .deviceKeys(query, retained.accessToken, deadline())
                    )
                    .deviceKeys,
            )
            // Only this fixture is stopped. No logout, device deletion or key replacement.
        }
    }

    @Test
    fun lostCreationResponseRecoversTheSameGroupAliasAndAlienMembershipIsNeverAdopted() {
        MatrixProviderFixture("10m").use { f ->
            f.start()
            val raw = HttpMatrixClientDataSource(f.client, f.config, f.clock)
            val matrix =
                InternalCollaborationSpaceMatrixRepository(
                    HmacMatrixJwtDataSource(f.config),
                    raw,
                    f.clock,
                )
            val space = UUID.randomUUID()
            val creator = UUID.randomUUID()
            val peer = UUID.randomUUID()
            val at = f.clock.instant()
            val owner =
                success(
                    matrix.login(
                        CollaborationSpaceMatrixLogin(
                            space,
                            creator,
                            UUID.randomUUID(),
                            null,
                            null,
                            at,
                            at.plusSeconds(60),
                        )
                    )
                )
            val request =
                CollaborationSpaceMatrixDirectRoom(
                    space,
                    UUID.randomUUID(),
                    creator,
                    peer,
                    owner.userId,
                    "@${collaborationSpaceMatrixLocalpart(space, peer)}:${f.config.serverName}",
                )
            var created: String? = null
            val lost =
                object : MatrixRoomDataSource by raw {
                    override fun createRoom(
                        body: Map<String, Any>,
                        token: String,
                        deadline: Instant,
                    ): MatrixRoomCreateData {
                        created =
                            (raw.createRoom(body, token, deadline) as MatrixRoomCreateData.Created)
                                .roomId
                        throw IOException("fixture response lost after actual create")
                    }
                }
            fun deadline() = f.clock.instant().plusSeconds(20)
            val uncertain =
                InternalCollaborationSpaceMatrixRoomRepository(lost, f.config.serverName)
            assertInstanceOf(
                Result.Failed::class.java,
                uncertain.create(request, owner.accessToken, deadline()),
            )
            val rooms = InternalCollaborationSpaceMatrixRoomRepository(raw, f.config.serverName)
            val room = requireNotNull(created)
            assertEquals(room, success(rooms.resolve(request, owner.accessToken, deadline())))
            assertEquals(
                CollaborationMatrixRoomCreation.AliasInUse,
                success(rooms.create(request, owner.accessToken, deadline())),
            )
            assertEquals(
                Result.Success(CollaborationMatrixRoomRepair(emptySet(), false)),
                assessCollaborationSpaceDirectRoom(
                    request,
                    success(rooms.observe(room, owner.accessToken, deadline())),
                    f.config.serverName,
                ),
            )
            // A direct provider mutation models a foreign audience outside the future fixed
            // gateway.
            raw.invite(
                room,
                "@fixture_unrelated:${f.config.serverName}",
                owner.accessToken,
                deadline(),
            )
            val alien = success(rooms.observe(room, owner.accessToken, deadline()))
            assertInstanceOf(
                Result.Failed::class.java,
                assessCollaborationSpaceDirectRoom(request, alien, f.config.serverName),
            )
            f.restart()
            val restartedRooms =
                InternalCollaborationSpaceMatrixRoomRepository(
                    HttpMatrixClientDataSource(f.client, f.config, f.clock),
                    f.config.serverName,
                )
            assertEquals(
                room,
                success(restartedRooms.resolve(request, owner.accessToken, deadline())),
            )
            assertInstanceOf(
                Result.Failed::class.java,
                assessCollaborationSpaceDirectRoom(
                    request,
                    success(restartedRooms.observe(room, owner.accessToken, deadline())),
                    f.config.serverName,
                ),
            )
        }
    }

    @Test
    fun originalRoomCanBeVerifiedByItsCanonicalPeerWithoutChangingCreatorOrKeys() {
        MatrixProviderFixture("10m").use { f ->
            f.start()
            fun raw() = HttpMatrixClientDataSource(f.client, f.config, f.clock)
            fun rooms() = InternalCollaborationMatrixRoomRepository(raw(), f.config.serverName)
            fun keys() = InternalCollaborationMatrixKeyRepository(raw())
            fun deadline() = f.clock.instant().plusSeconds(20)
            val owner = f.login()
            val peerAccount = UUID.randomUUID()
            val peerDevice = UUID.randomUUID()
            val issued = f.clock.instant()
            val peer =
                success(
                    f.repository.login(
                        f.request.copy(
                            accountId = peerAccount,
                            deviceId = peerDevice,
                            issuedAt = issued,
                            expiresAt = issued.plusSeconds(60),
                        )
                    )
                )
            val ownerKeys = MatrixKeyFixture(owner.userId, owner.deviceId)
            val peerKeys = MatrixKeyFixture(peer.userId, peer.deviceId)
            success(
                keys()
                    .upload(
                        CollaborationMatrixKeyUpload(ownerKeys.device),
                        owner.accessToken,
                        deadline(),
                    )
            )
            success(
                keys()
                    .upload(
                        CollaborationMatrixKeyUpload(peerKeys.device),
                        peer.accessToken,
                        deadline(),
                    )
            )
            val originalId = UUID.randomUUID()
            val request =
                CollaborationMatrixDirectRoom(
                    f.request.companyId,
                    originalId,
                    f.request.accountId,
                    peerAccount,
                )
            val roomId =
                (success(rooms().create(request, owner.accessToken, deadline()))
                        as CollaborationMatrixRoomCreation.Created)
                    .roomId
            val space = UUID.randomUUID()
            val pair = listOf(f.request.accountId, peerAccount).sortedBy(UUID::toString)
            val at = f.clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
            val group =
                CollaborationSpaceDirectConversation(
                    UUID.randomUUID(),
                    space,
                    pair[0],
                    pair[1],
                    peerAccount,
                    at,
                )
            val original =
                CollaborationSpaceLegacyConversation(
                    space,
                    CollaborationDirectConversation(
                        originalId,
                        f.request.companyId,
                        pair[0],
                        pair[1],
                        peerAccount,
                        at,
                    ),
                    CollaborationLegacyRoomMetadata(
                        f.config.serverName,
                        roomId,
                        at,
                        f.request.accountId,
                    ),
                )
            fun member(account: UUID) =
                CollaborationSpaceMember(
                    space,
                    account,
                    "Fictional",
                    CollaborationSpacePublicIdentity(
                        f.config.serverName,
                        if (account == peerAccount) peer.userId else owner.userId,
                        at,
                    ),
                )
            val candidate =
                success(
                    prepareCollaborationSpaceLegacyRoom(
                        group,
                        member(pair[0]),
                        member(pair[1]),
                        original,
                        f.config.serverName,
                        at,
                    )
                )
            assertEquals(f.request.accountId, candidate.creatorAccountId)
            assertNotEquals(group.createdBy, candidate.creatorAccountId)
            assertEquals(
                Result.Success(Unit),
                assessCollaborationSpaceLegacyRoom(
                    candidate,
                    success(rooms().observe(roomId, owner.accessToken, deadline())),
                ),
            )
            // Only the peer's original provider user/device logs in through group credentials.
            // The peer does not impersonate the creator or replace either participant's keys.
            val retained =
                success(
                    InternalCollaborationSpaceMatrixRepository(
                            HmacMatrixJwtDataSource(f.config),
                            raw(),
                            f.clock,
                        )
                        .login(
                            CollaborationSpaceMatrixLogin(
                                space,
                                peerAccount,
                                UUID.randomUUID(),
                                f.request.companyId,
                                CollaborationLegacyDevicePosition(f.request.companyId, peerDevice),
                                at,
                                at.plusSeconds(60),
                            )
                        )
                )
            assertEquals(peer.userId, retained.userId)
            assertEquals(peer.deviceId, retained.deviceId)
            assertEquals(
                roomId,
                success(
                    InternalCollaborationMatrixRoomAccessRepository(raw())
                        .join(candidate, retained.accessToken, deadline())
                ),
            )
            val state = success(rooms().observe(roomId, retained.accessToken, deadline()))
            assertEquals(Result.Success(Unit), assessCollaborationSpaceLegacyRoom(candidate, state))
            assertEquals(mapOf(owner.userId to "join", peer.userId to "join"), state.members)
            assertInstanceOf(
                Result.Failed::class.java,
                assessCollaborationSpaceLegacyRoom(
                    candidate.copy(creatorAccountId = peerAccount),
                    state,
                ),
            )
            val groupRequest =
                CollaborationSpaceMatrixDirectRoom(
                    space,
                    group.id,
                    peerAccount,
                    f.request.accountId,
                    peer.userId,
                    owner.userId,
                )
            val groupRooms =
                InternalCollaborationSpaceMatrixRoomRepository(raw(), f.config.serverName)
            assertNull(success(groupRooms.resolve(groupRequest, retained.accessToken, deadline())))
            assertInstanceOf(
                Result.Failed::class.java,
                assessCollaborationSpaceDirectRoom(
                    groupRequest,
                    success(groupRooms.observe(roomId, retained.accessToken, deadline())),
                    f.config.serverName,
                ),
            )
            val query =
                CollaborationMatrixKeyQuery(
                    mapOf(
                        owner.userId to listOf(owner.deviceId),
                        peer.userId to listOf(peer.deviceId),
                    )
                )
            val expected =
                mapOf(
                    owner.userId to mapOf(owner.deviceId to ownerKeys.device),
                    peer.userId to mapOf(peer.deviceId to peerKeys.device),
                )
            assertEquals(
                expected,
                success(keys().deviceKeys(query, retained.accessToken, deadline())).deviceKeys,
            )
            f.restart()
            assertEquals(
                roomId,
                success(rooms().resolve(request, retained.accessToken, deadline())),
            )
            assertEquals(state, success(rooms().observe(roomId, retained.accessToken, deadline())))
            assertEquals(
                expected,
                success(keys().deviceKeys(query, retained.accessToken, deadline())).deviceKeys,
            )
        }
    }

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result) {
            if (result is Result.Failed) result.failure.code else "Unexpected result"
        }
        return (result as Result.Success).value
    }
}
