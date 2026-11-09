package dev.fajar.hris

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.validateCollaborationDeviceKeys
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.*
import dev.fajar.hris.core.domain.Result
import java.net.URI
import java.net.http.*
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode

/** Fictional original publication, actual native commands and explicitly owned worker ports. */
abstract class NativeSpaceOriginalFixture : NativeGatewaySdkFixture() {
    @Autowired private lateinit var matrix: CollaborationMatrixRepository
    @Autowired private lateinit var rooms: CollaborationMatrixRoomRepository
    @Autowired private lateinit var keys: CollaborationMatrixKeyRepository
    @Autowired private lateinit var claim: ClaimCollaborationSpaceProvisioning
    @Autowired private lateinit var advance: AdvanceCollaborationSpaceProvisioning
    @Autowired private lateinit var claimRooms: ClaimCollaborationSpaceRooms
    @Autowired private lateinit var advanceRoom: AdvanceCollaborationSpaceRoom

    protected data class GroupOriginal(
        val spaceId: UUID,
        val deviceId: UUID,
        val bootstrapOperationId: UUID,
        val registrationId: UUID,
        val handoverOperationId: UUID,
        val createdAt: String,
    )

    protected fun originalRoom(sender: SdkOwner, recipient: SdkOwner, conversation: UUID): String {
        val at = clock.instant()
        val credential =
            success(
                matrix.login(
                    CollaborationMatrixLogin(
                        sender.native.fixture.company,
                        sender.native.fixture.account,
                        sender.device,
                        at,
                        at.plusSeconds(60),
                    )
                )
            )
        val room =
            success(
                rooms.create(
                    CollaborationMatrixDirectRoom(
                        sender.native.fixture.company,
                        conversation,
                        sender.native.fixture.account,
                        recipient.native.fixture.account,
                    ),
                    credential.accessToken,
                    clock.instant().plusSeconds(20),
                )
            )
                as CollaborationMatrixRoomCreation.Created
        return publishSdkRoom(
            sender.native,
            sender.device,
            recipient.native.fixture.account,
            room.roomId,
            conversation,
        )
    }

    protected fun completeOriginal(owner: SdkOwner, fingerprint: String): GroupOriginal {
        val prepared = prepareOriginal(owner, fingerprint)
        completeOriginal(owner, fingerprint, prepared)
        return prepared
    }

    /** Freeze the actual original, leaving key publication to the separately controlled worker. */
    protected fun prepareOriginal(owner: SdkOwner, fingerprint: String): GroupOriginal {
        val native = owner.native
        val originalKeys = originalPublicKeys(owner)
        assertEquals(
            fingerprint,
            success(
                validateCollaborationDeviceKeys(
                    originalKeys,
                    owner.session.getValue("userId"),
                    owner.session.getValue("deviceId"),
                )
            ),
        )
        val space =
            UUID.fromString(
                call(native, "/api/v1/collaboration/spaces/current")["spaceId"].asString()
            )
        val base = "/api/v1/collaboration/spaces/$space"
        call(native, "$base/identity", emptyMap())
        val registration = UUID.randomUUID()
        val createdAt = clock.instant().toString()
        val registered =
            call(
                native,
                "$base/native-device",
                mapOf(
                    "platform" to "ANDROID",
                    "label" to "Fictional original sender",
                    "origin" to "LEGACY",
                    "legacy" to
                        mapOf("companyId" to native.fixture.company, "deviceId" to owner.device),
                    "legacyKeyFingerprint" to fingerprint,
                ),
                registration,
            )
        val device = UUID.fromString(registered["id"].asString())
        val handover = UUID.randomUUID()
        call(
            native,
            "$base/native-device/legacy-runtime/freeze",
            mapOf("deviceId" to device, "expectedDeviceVersion" to 0, "fingerprint" to fingerprint),
            handover,
        )
        val drained =
            call(
                native,
                "$base/native-device/legacy-runtime/drain?deviceId=$device&expectedDeviceVersion=0&fingerprint=$fingerprint",
            )
        assertEquals(0, drained["activeRequests"].asInt())
        return GroupOriginal(space, device, UUID.randomUUID(), registration, handover, createdAt)
    }

    /** The actual worker publishes keys only after an existing conversation can need its wake. */
    protected fun completeOriginal(owner: SdkOwner, fingerprint: String, group: GroupOriginal) {
        val native = owner.native
        val device = group.deviceId
        val base = "/api/v1/collaboration/spaces/${group.spaceId}"
        withWorker {
            val leases = success(claim.execute(UUID.randomUUID(), 8))
            assertEquals(listOf(device), leases.map { it.request.deviceId })
            success(advance.execute(leases.single()))
        }
        assertEquals(
            device,
            database()
                .queryForObject(
                    "select device_id from collaboration_legacy_runtime_completions where legacy_company_id=? and legacy_device_id=?",
                    UUID::class.java,
                    native.fixture.company,
                    owner.device,
                ),
        )
        // Worker completion alone must establish receiving readiness before any group SDK or
        // gateway bootstrap exists. This is an assertion, never fixture publication SQL.
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_key_publications k join collaboration_legacy_runtime_completions c using(space_id,device_id,account_id,native_session_id,matrix_user_id,matrix_device_id) where k.device_id=? and k.fingerprint=? and k.version=1 and k.reserved_at=c.completed_at and k.published_at=c.completed_at",
                    Int::class.java,
                    device,
                    fingerprint,
                ),
        )
        call(
            native,
            "$base/native-device/bootstrap",
            mapOf("deviceId" to device, "expectedDeviceVersion" to 1),
            group.bootstrapOperationId,
        )
    }

    /** Public identity produced by the actual stopped SDK; this fixture never generates keys. */
    private fun originalPublicKeys(owner: SdkOwner): CollaborationMatrixDeviceKeys {
        val at = clock.instant()
        val credential =
            success(
                matrix.login(
                    CollaborationMatrixLogin(
                        owner.native.fixture.company,
                        owner.native.fixture.account,
                        owner.device,
                        at,
                        at.plusSeconds(60),
                    )
                )
            )
        return requireNotNull(
            success(
                keys.ownDeviceKeys(
                    credential.userId,
                    credential.deviceId,
                    credential.accessToken,
                    clock.instant().plusSeconds(20),
                )
            )
        )
    }

    protected fun adoptRoom(
        sender: SdkOwner,
        recipient: SdkOwner,
        group: GroupOriginal,
        originalRoom: String,
        recipientReady: Boolean = true,
    ): UUID {
        val base = "/api/v1/collaboration/spaces/${group.spaceId}/direct-conversations"
        val conversation =
            UUID.fromString(
                call(
                        sender.native,
                        base,
                        mapOf("peerAccountId" to recipient.native.fixture.account),
                    )["id"]
                    .asString()
            )
        call(
            sender.native,
            "$base/$conversation/room/requests",
            mapOf("deviceId" to group.deviceId, "expectedDeviceVersion" to 1),
        )
        withWorker {
            val leases = success(claimRooms.execute(UUID.randomUUID(), 8))
            assertEquals(1, leases.size)
            success(advanceRoom.execute(leases.single()))
        }
        val transport = call(sender.native, "$base/$conversation/transport")
        assertEquals(originalRoom, transport["room"]["roomId"].asString())
        assertEquals(sender.session.getValue("userId"), transport["matrixUserId"].asString())
        assertEquals(sender.session.getValue("deviceId"), transport["matrixDeviceId"].asString())
        assertEquals(recipientReady, transport["recipientReady"].asBoolean())
        assertEquals("LEGACY", transport["room"]["origin"].asString())
        assertEquals(
            originalRoom,
            database()
                .queryForObject(
                    "select matrix_room_id from collaboration_space_room_bindings where space_id=? and conversation_id=?",
                    String::class.java,
                    group.spaceId,
                    conversation,
                ),
        )
        return conversation
    }

    protected fun assertPublished(owner: SdkOwner, identity: JsonNode) {
        assertEquals(owner.session.getValue("userId"), identity["matrixUserId"].asString())
        assertEquals(owner.session.getValue("deviceId"), identity["matrixDeviceId"].asString())
        val fingerprint = identity["fingerprint"].asString()
        assertTrue(fingerprint.matches(Regex("[a-f0-9]{64}")))
        val until = System.nanoTime() + Duration.ofSeconds(20).toNanos()
        while (
            database()
                .queryForObject(
                    "select count(*) from collaboration_device_key_publications where company_id=? and device_id=? and version=1",
                    Int::class.java,
                    owner.native.fixture.company,
                    owner.device,
                ) != 1
        ) {
            assertTrue(System.nanoTime() < until, "Original SDK did not publish its keys")
            Thread.sleep(100)
        }
        assertEquals(
            fingerprint,
            database()
                .queryForObject(
                    "select fingerprint from collaboration_device_key_publications where company_id=? and device_id=? and version=1",
                    String::class.java,
                    owner.native.fixture.company,
                    owner.device,
                ),
        )
    }

    protected fun withWorker(action: () -> Unit) {
        database().execute("grant hris_worker_capability to hris_test_runtime")
        try {
            action()
        } finally {
            database().execute("revoke hris_worker_capability from hris_test_runtime")
        }
    }

    private fun call(
        n: Native,
        path: String,
        body: Map<String, Any>? = null,
        operation: UUID = UUID.randomUUID(),
    ): JsonNode {
        val request =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer ${n.access}")
                .header("X-HRIS-Client-Platform", "ANDROID")
                .header("X-HRIS-Client-Build", "1")
        if (body == null) request.GET()
        else
            request
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", operation.toString())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
        val response = n.fixture.browser.send(request.build(), HttpResponse.BodyHandlers.ofString())
        assertTrue(response.body().length <= 16384)
        val value = json.readTree(response.body())
        assertEquals(200, response.statusCode()) {
            "Original native HTTP failed: ${value.path("code").asString()}"
        }
        return value
    }

    protected fun <T> success(result: Result<T>): T {
        if (result is Result.Failed)
            fail<Nothing>("Original fixture failed: ${result.failure.kind}/${result.failure.code}")
        return (result as Result.Success).value
    }
}
