package dev.fajar.hris

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceProvisioningRepository
import dev.fajar.hris.collaboration.domain.usecases.*
import dev.fajar.hris.core.domain.*
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource

@TestPropertySource(
    properties =
        [
            "HRIS_COLLABORATION_ENABLED=true",
            "HRIS_MATRIX_ENABLED=true",
            "HRIS_COLLABORATION_SPACE_ENABLED=true",
            "HRIS_COLLABORATION_SPACE_MIN_ANDROID_BUILD=28",
            "HRIS_MATRIX_ORIGIN=http://127.0.0.1:9",
            "HRIS_MATRIX_SERVER_NAME=matrix.example.test",
            "HRIS_MATRIX_JWT_ISSUER=urn:hris:matrix:fixture",
            "HRIS_MATRIX_JWT_AUDIENCE=matrix:fixture",
        ]
)
class NativeCollaborationSpaceConfigurationTest : NativeCollaborationApiFixture() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun matrix(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_MATRIX_JWT_SECRET_FILE") {
                Path.of(
                        requireNotNull(
                                NativeCollaborationSpaceConfigurationTest::class
                                    .java
                                    .getResource("/matrix-signing.fixture")
                            )
                            .toURI()
                    )
                    .toString()
            }
        }
    }

    @Autowired private lateinit var selectSpace: SelectNativeCollaborationSpaceIdentity
    @Autowired private lateinit var registerSpace: RegisterNativeCollaborationSpaceDevice
    @Autowired private lateinit var retrySpace: RetryNativeCollaborationSpaceProvisioning
    @Autowired private lateinit var claimSpace: ClaimCollaborationSpaceProvisioning
    @Autowired private lateinit var queue: CollaborationSpaceProvisioningRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var policy: CollaborationSpacePolicy

    @Test
    fun identityReadIsPrivateMetadataAndNeverSelectsOrDisclosesPreferredDeviceOrCredentials() {
        for (legacy in listOf(false, true)) {
            val n = groupNative()
            val original = if (legacy) seedGroupLegacy(n) else null
            val base = groupBase(n)
            val before = groupCall(n, "$base/identity")
            assertEquals(200, before.statusCode(), before.body())
            assertTrue(json.readTree(before.body())["identity"].isNull)
            assertTrue(before.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from collaboration_space_identities where account_id=?",
                        Int::class.java,
                        n.fixture.account,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from collaboration_space_devices where account_id=?",
                        Int::class.java,
                        n.fixture.account,
                    ),
            )
            assertEquals(200, groupCall(n, "$base/identity", post = true).statusCode())
            val after = groupCall(n, "$base/identity")
            assertEquals(200, after.statusCode(), after.body())
            val selected = json.readTree(after.body())["identity"]
            assertEquals(
                setOf(
                    "spaceId",
                    "accountId",
                    "serverName",
                    "matrixUserId",
                    "origin",
                    "legacyCompanyId",
                    "version",
                    "createdAt",
                ),
                selected.properties().map { it.key }.toSet(),
            )
            assertEquals(n.fixture.account.toString(), selected["accountId"].asString())
            assertEquals(base.substringAfterLast('/'), selected["spaceId"].asString())
            assertEquals("matrix.example.test", selected["serverName"].asString())
            assertEquals(0L, selected["version"].asLong())
            assertEquals(if (legacy) "LEGACY" else "SPACE", selected["origin"].asString())
            val ownerPrefix =
                (if (legacy) n.fixture.company.toString() else base.substringAfterLast('/'))
                    .replace("-", "")
            val account = n.fixture.account.toString().replace("-", "")
            assertEquals(
                "@${if (legacy) "hris" else "hrisg"}_${ownerPrefix}_${account}:matrix.example.test",
                selected["matrixUserId"].asString(),
            )
            if (legacy) {
                assertEquals(n.fixture.company.toString(), selected["legacyCompanyId"].asString())
                assertFalse(after.body().contains(requireNotNull(original).toString()))
            } else assertTrue(selected["legacyCompanyId"].isNull)
            assertEquals(selected, json.readTree(groupCall(n, "$base/identity").body())["identity"])
            assertEquals(
                1,
                database()
                    .queryForObject(
                        "select count(*) from collaboration_space_identities where account_id=?",
                        Int::class.java,
                        n.fixture.account,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from collaboration_space_devices where account_id=?",
                        Int::class.java,
                        n.fixture.account,
                    ),
            )
            val peer = groupNative()
            assertTrue(json.readTree(groupCall(peer, "$base/identity").body())["identity"].isNull)
        }
    }

    @Test
    fun admittedNativeRoomSetupAndPeerStatusNeverExposeThePrivateRequest() {
        val a = groupNative()
        val b = groupNative()
        val outsider = groupNative()
        val base = groupBase(a)
        val first = activeGroupFixture(a, base)
        val second = activeGroupFixture(b, base)
        val opened =
            groupCall(
                a,
                "$base/direct-conversations",
                mapOf("peerAccountId" to b.fixture.account.toString()),
            )
        assertEquals(200, opened.statusCode(), opened.body())
        val conversation = json.readTree(opened.body())["id"].asString()
        val path = "$base/direct-conversations/$conversation/room"
        val empty = groupCall(a, path)
        assertEquals(200, empty.statusCode(), empty.body())
        assertTrue(json.readTree(empty.body())["provisioning"].isNull)
        assertTrue(json.readTree(empty.body())["room"].isNull)
        val operation = UUID.randomUUID()
        val body = mapOf("deviceId" to first.toString(), "expectedDeviceVersion" to 1)
        val prepared = groupCall(a, path, body, operation = operation)
        assertEquals(200, prepared.statusCode(), prepared.body())
        assertEquals(
            json.readTree(prepared.body()),
            json.readTree(groupCall(a, path, body, operation = operation).body()),
        )
        val observed = groupCall(b, path)
        assertEquals(200, observed.statusCode(), observed.body())
        assertEquals("no-store", observed.headers().firstValue("Cache-Control").orElse(""))
        val status = json.readTree(observed.body())["provisioning"]
        assertEquals(a.fixture.account.toString(), status["accountId"].asString())
        assertEquals("QUEUED", status["state"].asString())
        assertEquals(0L, status["revision"].asLong())
        for (field in
            listOf(
                "nativeSessionId",
                "nativeSessionVersion",
                "authenticatedAt",
                "mfaVerifiedAt",
                "credentialVersion",
                "providerGeneration",
                "deviceId",
                "leaseToken",
            )) {
            assertFalse(status.has(field), field)
        }
        assertEquals(
            200,
            groupCall(b, path, mapOf("deviceId" to second.toString(), "expectedDeviceVersion" to 1))
                .statusCode(),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_room_provisioning where conversation_id=?::uuid",
                    Int::class.java,
                    conversation,
                ),
        )
        failure(groupCall(outsider, path), 404, "collaboration_conversation_not_found")
        failure(
            groupCall(a, path, body + mapOf("expectedRequestRevision" to 0)),
            409,
            "collaboration_room_provisioning_changed",
        )
        failure(
            groupCall(
                a,
                path,
                body + mapOf("expectedRequestAccountId" to b.fixture.account.toString()),
            ),
            409,
            "collaboration_room_provisioning_changed",
        )
        failure(
            groupCall(
                a,
                path,
                body +
                    mapOf(
                        "expectedRequestRevision" to 0,
                        "expectedRequestAccountId" to b.fixture.account.toString(),
                    ),
            ),
            409,
            "collaboration_room_provisioning_changed",
        )
        database()
            .update(
                "delete from membership_permissions where account_id=? and permission='collaboration.chat'",
                b.fixture.account,
            )
        failure(groupCall(a, path), 403, "collaboration_peer_unavailable")
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_room_provisioning where conversation_id=?::uuid",
                    Int::class.java,
                    conversation,
                ),
        )
    }

    @Test
    fun actualApiGraphEnqueuesUnderCurrentNativeProofWithoutGrantingWorkerCapability() {
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS))
        assertTrue(policy.enabled)
        assertEquals(28, policy.minimumAndroidBuild)
        val n = native()
        val row =
            database()
                .queryForMap(
                    "select authenticated_at,mfa_verified_at,credential_version,version from native_sessions where id=?",
                    n.session,
                )
        val actor =
            Actor(
                n.fixture.account,
                null,
                emptySet(),
                (row["authenticated_at"] as java.sql.Timestamp).toInstant(),
                UUID.randomUUID(),
                mfaVerifiedAt = (row["mfa_verified_at"] as? java.sql.Timestamp)?.toInstant(),
                credentialVersion = (row["credential_version"] as Number).toLong(),
            )
        val version = (row["version"] as Number).toLong()
        val space =
            requireNotNull(
                database().queryForObject("select id from collaboration_spaces", UUID::class.java)
            )
        val client = ClientVersion(ClientPlatform.ANDROID, 28)
        assertInstanceOf(
            Result.Success::class.java,
            selectSpace.execute(actor, n.session, version, space, client, UUID.randomUUID()),
        )
        val operation = UUID.randomUUID()
        fun register() =
            registerSpace.execute(
                actor,
                n.session,
                version,
                space,
                client,
                operation,
                CollaborationPlatform.ANDROID,
                "Fictional API device",
                CollaborationSpaceDeviceOrigin.SPACE,
            )
        val registered = register()
        assertInstanceOf(Result.Success::class.java, registered) {
            if (registered is Result.Failed) registered.failure.code else "Unexpected result"
        }
        val receipt = (registered as Result.Success).value
        val head = transactions.run(actor) { queue.metadata(space, actor.accountId, receipt.id) }
        assertInstanceOf(Result.Success::class.java, head)
        val stored = requireNotNull((head as Result.Success).value)
        assertEquals(CollaborationSpaceProvisioningState.QUEUED, stored.state)
        assertEquals(actor.authenticatedAt, stored.request.authenticatedAt)
        assertEquals(actor.mfaVerifiedAt, stored.request.mfaVerifiedAt)
        assertEquals(receipt.copy(replayed = true), (register() as Result.Success).value)
        assertInstanceOf(Result.Failed::class.java, claimSpace.execute(UUID.randomUUID(), 1))
        assertInstanceOf(
            Result.Failed::class.java,
            retrySpace.execute(
                actor,
                n.session,
                version,
                space,
                client,
                UUID.randomUUID(),
                receipt.id,
                0,
                0,
            ),
        )
        assertEquals(
            stored,
            (transactions.run(actor) { queue.metadata(space, actor.accountId, receipt.id) }
                    as Result.Success)
                .value,
        )
    }

    @Test
    fun nativeOriginalRoomReceiptReadRecoversAcrossNewBuildAndReplacementSession() {
        val a = groupNative()
        val b = groupNative()
        val base = groupBase(a)
        val device = activeGroupFixture(a, base)
        activeGroupFixture(b, base)
        val opened =
            groupCall(
                a,
                "$base/direct-conversations",
                mapOf("peerAccountId" to b.fixture.account.toString()),
            )
        assertEquals(200, opened.statusCode(), opened.body())
        val conversation = json.readTree(opened.body())["id"].asString()
        val room = "$base/direct-conversations/$conversation/room"
        val operation = UUID.randomUUID()
        val request = mapOf("deviceId" to device.toString(), "expectedDeviceVersion" to 1)
        val recorded = groupCall(a, room, request, operation = operation)
        assertEquals(200, recorded.statusCode(), recorded.body())
        val original =
            request +
                mapOf(
                    "operationId" to operation.toString(),
                    "nativeSessionId" to a.session.toString(),
                    "nativeSessionVersion" to 0,
                    "platform" to "ANDROID",
                    "clientBuild" to 28,
                )
        val path = "$room/receipts/read"
        val before =
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where actor_id=?",
                    Int::class.java,
                    a.fixture.account,
                )
        failure(
            groupCall(a, room, request, operation = operation, build = "29"),
            409,
            "operation_payload_mismatch",
        )
        val recovered =
            groupCall(a, path, original, operation = null, build = "29", cookies = false)
        assertEquals(200, recovered.statusCode(), recovered.body())
        assertEquals("no-store", recovered.headers().firstValue("Cache-Control").orElse(""))
        val value = json.readTree(recovered.body())
        assertEquals(setOf("accountId", "original", "receipt"), value.propertyNames().toSet())
        assertEquals(a.fixture.account.toString(), value["accountId"].asString())
        assertEquals(json.readTree(recorded.body()), value["receipt"])
        assertEquals(a.session.toString(), value["original"]["nativeSessionId"].asString())
        assertEquals(28, value["original"]["clientBuild"].asInt())
        assertEquals(conversation, value["original"]["conversationId"].asString())
        val peer = groupCall(b, path, original, operation = null, cookies = false)
        assertEquals(200, peer.statusCode(), peer.body())
        assertTrue(json.readTree(peer.body())["receipt"].isNull)
        assertEquals(
            b.fixture.account.toString(),
            json.readTree(peer.body())["accountId"].asString(),
        )
        assertEquals(
            before,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where actor_id=?",
                    Int::class.java,
                    a.fixture.account,
                ),
        )

        val exchanged =
            command(
                a.fixture.browser,
                "/api/v1/auth/native/exchange",
                """{"deviceName":"Room receipt replacement fixture"}""",
                a.fixture.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, exchanged.statusCode(), exchanged.body())
        val auth = json.readTree(exchanged.body())
        val replacement =
            Native(
                a.fixture,
                UUID.fromString(auth["sessionId"].asString()),
                auth["accessToken"].asString(),
            )
        val afterExchange =
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where actor_id=?",
                    Int::class.java,
                    a.fixture.account,
                )
        database().update("update native_sessions set revoked_at=now() where id=?", a.session)
        assertEquals(
            401,
            groupCall(a, path, original, operation = null, cookies = false).statusCode(),
        )
        val after =
            groupCall(replacement, path, original, operation = null, build = "29", cookies = false)
        assertEquals(200, after.statusCode(), after.body())
        assertEquals(value, json.readTree(after.body()))
        assertEquals(
            afterExchange,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where actor_id=?",
                    Int::class.java,
                    a.fixture.account,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_room_provisioning where conversation_id=?::uuid",
                    Int::class.java,
                    conversation,
                ),
        )
        database()
            .update(
                "delete from membership_permissions where account_id=? and permission='collaboration.chat'",
                b.fixture.account,
            )
        failure(
            groupCall(
                replacement,
                path,
                original + mapOf("nativeSessionVersion" to 1),
                operation = null,
                cookies = false,
            ),
            403,
            "collaboration_peer_unavailable",
        )
    }

    @Test
    fun firstRoomWriteAfterNativeRefreshUsesAnUnexposedVersionInItsOriginalFingerprint() {
        val source = groupNative()
        val b = groupNative()
        val exchanged =
            command(
                source.fixture.browser,
                "/api/v1/auth/native/exchange",
                """{"deviceName":"Room proof capture fixture"}""",
                source.fixture.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, exchanged.statusCode(), exchanged.body())
        val credentials = json.readTree(exchanged.body())
        val a =
            Native(
                source.fixture,
                UUID.fromString(credentials["sessionId"].asString()),
                credentials["accessToken"].asString(),
            )
        assertFalse(credentials.has("version"))
        assertFalse(credentials.has("nativeSessionVersion"))
        val base = groupBase(a)
        val device = activeGroupFixture(a, base)
        activeGroupFixture(b, base)
        val opened =
            groupCall(
                a,
                "$base/direct-conversations",
                mapOf("peerAccountId" to b.fixture.account.toString()),
            )
        assertEquals(200, opened.statusCode(), opened.body())
        val conversation = json.readTree(opened.body())["id"].asString()
        val path = "$base/direct-conversations/$conversation/room"
        val operation = UUID.randomUUID()
        val input = mapOf("deviceId" to device.toString(), "expectedDeviceVersion" to 1)
        assertEquals(
            0L,
            database()
                .queryForObject(
                    "select version from native_sessions where id=?",
                    Long::class.java,
                    a.session,
                ),
        )
        // Room persistence checks PostgreSQL wall time; advancing only the app clock is invalid.
        Thread.sleep(11_000)
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS))
        val refreshed =
            groupCall(
                a,
                "/api/v1/auth/native/refresh",
                mapOf("refreshToken" to credentials["refreshToken"].asString()),
                token = null,
                cookies = false,
            )
        assertEquals(200, refreshed.statusCode(), refreshed.body())
        val response = json.readTree(refreshed.body())
        assertFalse(response.has("version"))
        assertFalse(response.has("nativeSessionVersion"))
        assertEquals(a.session.toString(), response["sessionId"].asString())
        assertEquals(
            1L,
            database()
                .queryForObject(
                    "select version from native_sessions where id=?",
                    Long::class.java,
                    a.session,
                ),
        )
        val rotated = Native(a.fixture, a.session, response["accessToken"].asString())
        val written = groupCall(rotated, path, input, operation = operation)
        assertEquals(200, written.statusCode(), written.body())
        val before =
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where actor_id=?",
                    Int::class.java,
                    a.fixture.account,
                )
        val assumedOriginal =
            input +
                mapOf(
                    "operationId" to operation.toString(),
                    "nativeSessionId" to a.session.toString(),
                    "nativeSessionVersion" to 0,
                    "platform" to "ANDROID",
                    "clientBuild" to 28,
                )
        failure(
            groupCall(rotated, "$path/receipts/read", assumedOriginal, operation = null),
            409,
            "operation_payload_mismatch",
        )
        val actual =
            groupCall(
                rotated,
                "$path/receipts/read",
                assumedOriginal + mapOf("nativeSessionVersion" to 1),
                operation = null,
            )
        assertEquals(200, actual.statusCode(), actual.body())
        assertEquals(json.readTree(written.body()), json.readTree(actual.body())["receipt"])
        assertEquals(
            before,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where actor_id=?",
                    Int::class.java,
                    a.fixture.account,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_room_provisioning where conversation_id=?::uuid",
                    Int::class.java,
                    conversation,
                ),
        )
    }

    @Test
    fun stableRoomIntentSurvivesActualRefreshUpgradeAndReplacementParentWithoutNativeProofInput() {
        val (a, refreshToken) = exchangeRoomNative(groupNative())
        val b = groupNative()
        val base = groupBase(a)
        val device = activeGroupFixture(a, base)
        activeGroupFixture(b, base)
        val opened =
            groupCall(
                a,
                "$base/direct-conversations",
                mapOf("peerAccountId" to b.fixture.account.toString()),
            )
        assertEquals(200, opened.statusCode(), opened.body())
        val conversation = json.readTree(opened.body())["id"].asString()
        val room = "$base/direct-conversations/$conversation/room"
        val write = "$room/requests"
        val read = "$write/receipts/read"
        val operation = UUID.randomUUID()
        val input = mapOf("deviceId" to device.toString(), "expectedDeviceVersion" to 1)
        val original = input + mapOf("operationId" to operation.toString())
        val (rotated, nextRefresh) = refreshRoomNative(a, refreshToken)
        assertEquals(
            1L,
            database()
                .queryForObject(
                    "select version from native_sessions where id=?",
                    Long::class.java,
                    a.session,
                ),
        )
        val absent = groupCall(rotated, read, original, operation = null, cookies = false)
        assertEquals(200, absent.statusCode(), absent.body())
        assertTrue(json.readTree(absent.body())["receipt"].isNull)
        val written = groupCall(rotated, write, input, operation = operation, cookies = false)
        assertEquals(200, written.statusCode(), written.body())
        val stoppedOperation = UUID.randomUUID()
        val stoppedOriginal = input + mapOf("operationId" to stoppedOperation.toString())
        val stopped =
            groupCall(
                rotated,
                "$write/fence",
                stoppedOriginal,
                operation = stoppedOperation,
                cookies = false,
            )
        assertEquals(200, stopped.statusCode(), stopped.body())
        assertEquals("FENCED", json.readTree(stopped.body())["state"].asString())
        failure(
            groupCall(rotated, write, input, operation = stoppedOperation, cookies = false),
            409,
            "collaboration_room_request_fenced",
        )

        val upgraded =
            groupCall(rotated, write, input, operation = operation, build = "29", cookies = false)
        assertEquals(200, upgraded.statusCode(), upgraded.body())
        assertEquals(json.readTree(written.body()), json.readTree(upgraded.body()))
        val (again, _) = refreshRoomNative(rotated, nextRefresh)
        val replay =
            groupCall(again, write, input, operation = operation, build = "29", cookies = false)
        assertEquals(200, replay.statusCode(), replay.body())
        assertEquals(json.readTree(written.body()), json.readTree(replay.body()))
        val observed =
            groupCall(again, read, original, operation = null, build = "29", cookies = false)
        assertEquals(200, observed.statusCode(), observed.body())
        assertEquals("no-store", observed.headers().firstValue("Cache-Control").orElse(""))
        val receipt = json.readTree(observed.body())
        assertEquals(setOf("accountId", "original", "receipt"), receipt.propertyNames().toSet())
        assertEquals(a.fixture.account.toString(), receipt["accountId"].asString())
        assertEquals(json.readTree(written.body()), receipt["receipt"])
        assertEquals(
            setOf(
                "operationId",
                "spaceId",
                "conversationId",
                "deviceId",
                "expectedDeviceVersion",
                "expectedRequestRevision",
                "expectedRequestAccountId",
            ),
            receipt["original"].propertyNames().toSet(),
        )
        val legacy =
            groupCall(
                again,
                "$room/receipts/read",
                original +
                    mapOf(
                        "nativeSessionId" to a.session.toString(),
                        "nativeSessionVersion" to 1,
                        "platform" to "ANDROID",
                        "clientBuild" to 28,
                    ),
                operation = null,
            )
        assertEquals(200, legacy.statusCode(), legacy.body())
        assertTrue(json.readTree(legacy.body())["receipt"].isNull)
        val privateRead = groupCall(b, read, original, operation = null)
        assertEquals(200, privateRead.statusCode(), privateRead.body())
        assertTrue(json.readTree(privateRead.body())["receipt"].isNull)
        failure(
            groupCall(
                again,
                read,
                original + mapOf("expectedDeviceVersion" to 2),
                operation = null,
            ),
            409,
            "operation_payload_mismatch",
        )
        val (replacement, _) = exchangeRoomNative(again)
        database().update("update native_sessions set revoked_at=now() where id=?", a.session)
        val recovered =
            groupCall(replacement, read, original, operation = null, build = "29", cookies = false)
        assertEquals(200, recovered.statusCode(), recovered.body())
        assertEquals(receipt, json.readTree(recovered.body()))
        val resolved =
            groupCall(
                replacement,
                "$write/fence",
                original,
                operation = operation,
                build = "29",
                cookies = false,
            )
        assertEquals(200, resolved.statusCode(), resolved.body())
        assertEquals("no-store", resolved.headers().firstValue("Cache-Control").orElse(""))
        val resolution = json.readTree(resolved.body())
        assertEquals(
            setOf("accountId", "original", "state", "receipt", "fence"),
            resolution.propertyNames().toSet(),
        )
        assertEquals("RECORDED", resolution["state"].asString())
        assertEquals(receipt["accountId"], resolution["accountId"])
        assertEquals(receipt["original"], resolution["original"])
        assertEquals(receipt["receipt"], resolution["receipt"])
        assertTrue(resolution["fence"].isNull)
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_room_request_fences where account_id=? and operation_id=?",
                    Int::class.java,
                    a.fixture.account,
                    operation,
                ),
        )

        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_room_provisioning where conversation_id=?::uuid",
                    Int::class.java,
                    conversation,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where actor_id=? and operation in ('collaboration.space_room_setup','collaboration.space_room_setup.v2')",
                    Int::class.java,
                    a.fixture.account,
                ),
        )
        database()
            .update(
                "delete from membership_permissions where account_id=? and permission='collaboration.chat'",
                b.fixture.account,
            )
        failure(
            groupCall(
                replacement,
                read,
                original + mapOf("expectedDeviceVersion" to 2),
                operation = null,
            ),
            403,
            "collaboration_peer_unavailable",
        )
    }

    @Test
    fun nativeRoomReceiptAbsenceIsReadOnlyAndRequiresCompleteBoundedOriginalInput() {
        val a = groupNative()
        val b = groupNative()
        val outsider = groupNative()
        val base = groupBase(a)
        val opened =
            groupCall(
                a,
                "$base/direct-conversations",
                mapOf("peerAccountId" to b.fixture.account.toString()),
            )
        assertEquals(200, opened.statusCode(), opened.body())
        val conversation = json.readTree(opened.body())["id"].asString()
        val path = "$base/direct-conversations/$conversation/room/receipts/read"
        val original =
            mapOf(
                "operationId" to UUID.randomUUID().toString(),
                "nativeSessionId" to a.session.toString(),
                "nativeSessionVersion" to 0,
                "deviceId" to UUID.randomUUID().toString(),
                "expectedDeviceVersion" to 1,
                "platform" to "ANDROID",
                "clientBuild" to 28,
            )
        val before =
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where actor_id=?",
                    Int::class.java,
                    a.fixture.account,
                )
        val missing = groupCall(a, path, original, operation = null, cookies = false)
        assertEquals(200, missing.statusCode(), missing.body())
        assertTrue(json.readTree(missing.body())["receipt"].isNull)
        assertEquals(
            before,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where actor_id=?",
                    Int::class.java,
                    a.fixture.account,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_room_provisioning where conversation_id=?::uuid",
                    Int::class.java,
                    conversation,
                ),
        )
        assertEquals(
            400,
            groupCall(a, path, original - "nativeSessionVersion", operation = null).statusCode(),
        )
        assertEquals(
            400,
            groupCall(a, path, original + mapOf("deviceId" to "invalid"), operation = null)
                .statusCode(),
        )
        for (invalid in
            listOf(
                original + mapOf("nativeSessionVersion" to -1),
                original + mapOf("expectedDeviceVersion" to 1_000_000),
                original + mapOf("platform" to "WEB"),
                original + mapOf("expectedRequestRevision" to 0),
                original + mapOf("expectedRequestAccountId" to b.fixture.account.toString()),
            )) failure(
            groupCall(a, path, invalid, operation = null),
            422,
            "invalid_collaboration_room_operation",
        )
        failure(
            groupCall(a, path, original, operation = null, build = "27"),
            403,
            "client_update_required",
        )
        failure(
            groupCall(outsider, path, original, operation = null),
            404,
            "collaboration_conversation_not_found",
        )
        assertEquals(
            403,
            groupCall(a, path, original, operation = null, token = null, cookies = false)
                .statusCode(),
        )
        assertEquals(
            413,
            groupCall(
                    a,
                    path,
                    original + mapOf("unused" to "x".repeat(1_048_577)),
                    operation = null,
                )
                .statusCode(),
        )
    }

    @Test
    fun groupDiscoveryAndContactsCrossCompaniesWithoutPrivateFields() {
        val n = groupNative()
        val admission = groupCall(n, "/api/v1/collaboration/spaces/current")
        assertEquals(200, admission.statusCode(), admission.body())
        assertEquals("no-store", admission.headers().firstValue("Cache-Control").orElse(""))
        val discovered = json.readTree(admission.body())
        assertEquals(
            setOf(
                "spaceId",
                "accountId",
                "nativeSessionId",
                "nativeSessionVersion",
                "credentialVersion",
                "admittedAt",
                "expiresAt",
            ),
            discovered.propertyNames().toSet(),
        )
        assertEquals(n.fixture.account.toString(), discovered["accountId"].asString())
        assertEquals(n.session.toString(), discovered["nativeSessionId"].asString())
        val space = discovered["spaceId"].asString()
        val other = groupNative()
        val peer = UUID.randomUUID()
        val name = "Group peer $peer"
        database()
            .update(
                "insert into accounts(id,email,display_name) values(?,?,?)",
                peer,
                "$peer@example.test",
                name,
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                other.fixture.company,
                peer,
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                other.fixture.company,
                peer,
            )
        val path = "/api/v1/collaboration/spaces/$space/contacts?query=Group%20peer%20$peer&limit=1"
        val page = groupCall(n, path)
        assertEquals(200, page.statusCode(), page.body())
        val result = json.readTree(page.body())
        assertEquals(1, result["items"].size())
        assertTrue(result["nextCursor"].isNull)
        val item = result["items"][0]
        assertEquals(
            setOf("accountId", "displayName", "companies", "hasMoreCompanies"),
            item.propertyNames().toSet(),
        )
        assertEquals(peer.toString(), item["accountId"].asString())
        assertEquals(name, item["displayName"].asString())
        assertEquals(other.fixture.company.toString(), item["companies"][0]["id"].asString())
        assertFalse(page.body().contains("example.test"))
        failure(
            groupCall(n, "/api/v1/collaboration/spaces/$space/contacts?limit=51"),
            422,
            "invalid_collaboration_contact_query",
        )
        assertEquals(
            400,
            groupCall(n, "/api/v1/collaboration/spaces/$space/contacts?after=invalid").statusCode(),
        )
        failure(
            groupCall(n, "/api/v1/collaboration/spaces/${UUID.randomUUID()}/contacts"),
            403,
            "collaboration_access_denied",
        )
        database()
            .update(
                "delete from membership_permissions where account_id=? and permission='collaboration.chat'",
                peer,
            )
        assertTrue(json.readTree(groupCall(n, path).body())["items"].isEmpty)
        // Directory labels never imply permission to enter the peer's HR company.
        val hr = groupCall(n, "${other.fixture.root}/employees?asOf=2026-11-04")
        assertEquals(403, hr.statusCode(), hr.body())
    }

    @Test
    fun groupSelectionRegistrationAndStatusKeepOriginalWorkOnReplay() {
        val n = groupNative()
        val base = groupBase(n)
        assertTrue(json.readTree(groupCall(n, "$base/native-device").body())["device"].isNull)
        val selection = UUID.randomUUID()
        val selected = groupCall(n, "$base/identity", post = true, operation = selection)
        assertEquals(200, selected.statusCode(), selected.body())
        val selectedAgain = groupCall(n, "$base/identity", post = true, operation = selection)
        assertEquals(200, selectedAgain.statusCode(), selectedAgain.body())
        assertEquals(json.readTree(selected.body()), json.readTree(selectedAgain.body()))
        val operation = UUID.randomUUID()
        val registered =
            groupCall(n, "$base/native-device", groupRegistration(), operation = operation)
        assertEquals(200, registered.statusCode(), registered.body())
        val id = json.readTree(registered.body())["id"].asString()
        val replay = groupCall(n, "$base/native-device", groupRegistration(), operation = operation)
        assertEquals(200, replay.statusCode(), replay.body())
        assertEquals(id, json.readTree(replay.body())["id"].asString())
        assertEquals(json.readTree(registered.body()), json.readTree(replay.body()))
        val status = groupCall(n, "$base/native-device")
        assertEquals(200, status.statusCode(), status.body())
        val device = json.readTree(status.body())["device"]
        assertEquals(
            setOf(
                "id",
                "version",
                "state",
                "platform",
                "origin",
                "label",
                "provisioning",
                "observedAt",
                "gateway",
            ),
            device.propertyNames().toSet(),
        )
        assertEquals(id, device["id"].asString())
        assertEquals("PROVISIONING", device["state"].asString())
        assertEquals(
            setOf("revision", "state", "attempts", "expiresAt", "lastOutcome", "retryNotBefore"),
            device["provisioning"].propertyNames().toSet(),
        )
        assertEquals("QUEUED", device["provisioning"]["state"].asString())
        assertEquals(0, device["provisioning"]["attempts"].asInt())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_provisioning where device_id=?",
                    Int::class.java,
                    UUID.fromString(id),
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_provider_sessions where device_id=?",
                    Int::class.java,
                    UUID.fromString(id),
                ),
        )
        failure(
            groupCall(
                n,
                "$base/native-device/retry",
                mapOf(
                    "deviceId" to id,
                    "expectedDeviceVersion" to 0,
                    "expectedProvisioningRevision" to 0,
                ),
            ),
            409,
            "collaboration_provisioning_changed",
        )
        assertEquals(device, json.readTree(groupCall(n, "$base/native-device").body())["device"])
    }

    @Test
    fun groupTransportBuildAndPathAdmissionFailClosed() {
        val n = groupNative()
        val base = groupBase(n)
        val reads =
            listOf(
                "/api/v1/collaboration/spaces/current",
                "$base/contacts",
                "$base/native-device",
                "$base/identity",
            )
        for (path in reads) {
            assertEquals(401, groupCall(n, path, token = null, cookies = false).statusCode())
            failure(get(n.fixture.browser, path), 403, "invalid_session_transport")
            failure(groupCall(n, path, build = "27"), 403, "client_update_required")
            failure(groupCall(n, path, platform = "WEB"), 422, "invalid_client_version")
        }
        failure(
            groupCall(n, "$base/identity", post = true, build = "27"),
            403,
            "client_update_required",
        )
        failure(
            groupCall(n, "$base/native-device", groupRegistration(), build = "27"),
            403,
            "client_update_required",
        )
        assertEquals(
            400,
            groupCall(n, "/api/v1/collaboration/spaces/invalid/native-device").statusCode(),
        )
        val denied = groupNative(grant = false)
        failure(
            groupCall(denied, "/api/v1/collaboration/spaces/current"),
            403,
            "collaboration_access_denied",
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_devices where account_id=?",
                    Int::class.java,
                    n.fixture.account,
                ),
        )
    }

    @Test
    fun legacyPublicationPreflightRequiresCurrentNativeTargetAndLeavesRuntimeUntouchedOnOutage() {
        val n = groupNative()
        val original = seedGroupLegacy(n)
        val base = groupBase(n)
        assertEquals(200, groupCall(n, "$base/identity", post = true).statusCode())
        val registration =
            groupCall(
                n,
                "$base/native-device",
                groupRegistration() +
                    mapOf(
                        "origin" to "LEGACY",
                        "legacy" to mapOf("companyId" to n.fixture.company, "deviceId" to original),
                        "legacyKeyFingerprint" to "a".repeat(64),
                    ),
            )
        assertEquals(200, registration.statusCode(), registration.body())
        val target = json.readTree(registration.body())["id"].asString()
        val path = "$base/native-device/legacy-runtime/publication"
        val input =
            mapOf(
                "deviceId" to target,
                "expectedDeviceVersion" to 0,
                "fingerprint" to "a".repeat(64),
            )
        for (missing in input.keys) {
            assertEquals(400, groupCall(n, path, input - missing, operation = null).statusCode())
        }
        failure(groupCall(n, path, input, build = "27"), 403, "client_update_required")
        failure(groupCall(n, path, input, platform = "WEB"), 422, "invalid_client_version")
        assertEquals(403, groupCall(n, path, input, token = null, cookies = false).statusCode())
        failure(groupCall(n, path, input, token = null), 403, "access_denied")
        failure(
            groupCall(n, path, input + ("deviceId" to UUID.randomUUID())),
            503,
            "collaboration_admission_changed",
        )
        val status = json.readTree(groupCall(n, "$base/native-device").body())
        repeat(2) {
            // Provider connection is unavailable. A read needs no operation receipt and never
            // freezes.
            failure(
                groupCall(n, path, input, operation = null),
                503,
                "collaboration_provider_unavailable",
            )
        }
        assertEquals(status, json.readTree(groupCall(n, "$base/native-device").body()))
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_legacy_runtime where account_id=?",
                    Int::class.java,
                    n.fixture.account,
                ),
        )
        database()
            .update(
                "delete from membership_permissions where account_id=? and permission='collaboration.chat'",
                n.fixture.account,
            )
        failure(groupCall(n, path, input, operation = null), 403, "collaboration_access_denied")
    }

    @Test
    fun groupRetainedFreezeAndDrainExposeExactOwnedTarget() {
        val n = groupNative()
        val original = seedGroupLegacy(n)
        val base = groupBase(n)
        assertEquals(200, groupCall(n, "$base/identity", post = true).statusCode())
        val body =
            groupRegistration() +
                mapOf(
                    "origin" to "LEGACY",
                    "legacy" to mapOf("companyId" to n.fixture.company, "deviceId" to original),
                    "legacyKeyFingerprint" to "a".repeat(64),
                )
        val registered = groupCall(n, "$base/native-device", body)
        assertEquals(200, registered.statusCode(), registered.body())
        val target = json.readTree(registered.body())["id"].asString()
        val command =
            mapOf(
                "deviceId" to target,
                "expectedDeviceVersion" to 0,
                "fingerprint" to "a".repeat(64),
            )
        val operation = UUID.randomUUID()
        val frozen =
            groupCall(
                n,
                "$base/native-device/legacy-runtime/freeze",
                command,
                operation = operation,
            )
        assertEquals(200, frozen.statusCode(), frozen.body())
        val receipt = json.readTree(frozen.body())
        assertEquals(original.toString(), receipt["id"].asString())
        assertEquals(1, receipt["version"].asLong())
        val replay =
            groupCall(
                n,
                "$base/native-device/legacy-runtime/freeze",
                command,
                operation = operation,
            )
        assertEquals(200, replay.statusCode(), replay.body())
        assertEquals(receipt, json.readTree(replay.body()))
        val path =
            "$base/native-device/legacy-runtime/drain?deviceId=$target&expectedDeviceVersion=0&fingerprint=${"a".repeat(64)}"
        val observed = groupCall(n, path)
        assertEquals(200, observed.statusCode(), observed.body())
        val drain = json.readTree(observed.body())
        assertEquals(
            setOf(
                "target",
                "runtimeVersion",
                "activeRequests",
                "latestRequestExpiresAt",
                "observedAt",
            ),
            drain.propertyNames().toSet(),
        )
        assertEquals(target, drain["target"]["deviceId"].asString())
        assertEquals(1, drain["runtimeVersion"].asLong())
        assertEquals(0, drain["activeRequests"].asInt())
        assertTrue(drain["latestRequestExpiresAt"].isNull)
        failure(
            groupCall(
                n,
                "$base/native-device/legacy-runtime/freeze",
                command + ("fingerprint" to "b".repeat(64)),
            ),
            409,
            "collaboration_device_changed",
        )
        assertEquals(
            "ACTIVE",
            database()
                .queryForObject(
                    "select state from collaboration_devices where company_id=? and id=?",
                    String::class.java,
                    n.fixture.company,
                    original,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_legacy_runtime_completions where account_id=?",
                    Int::class.java,
                    n.fixture.account,
                ),
        )
    }

    @Test
    fun groupMissingHeadersAndInvalidRequestVersionsCannotMutate() {
        val n = groupNative()
        val base = groupBase(n)
        assertEquals(
            400,
            groupCall(n, "$base/identity", post = true, operation = null).statusCode(),
        )
        assertEquals(200, groupCall(n, "$base/identity", post = true).statusCode())
        assertEquals(
            400,
            groupCall(n, "$base/native-device", groupRegistration(), operation = null).statusCode(),
        )
        failure(
            groupCall(n, "$base/native-device", groupRegistration() + ("platform" to "IOS")),
            422,
            "invalid_collaboration_device",
        )
        failure(
            groupCall(n, "$base/native-device", groupRegistration() + ("origin" to "LEGACY")),
            422,
            "invalid_collaboration_device",
        )
        failure(
            groupCall(
                n,
                "$base/native-device/retry",
                mapOf(
                    "deviceId" to UUID.randomUUID(),
                    "expectedDeviceVersion" to -1,
                    "expectedProvisioningRevision" to 0,
                ),
            ),
            422,
            "invalid_collaboration_provisioning_version",
        )
        assertEquals(
            400,
            groupCall(n, "$base/native-device/retry", mapOf("deviceId" to UUID.randomUUID()))
                .statusCode(),
        )
        assertEquals(400, groupCall(n, "$base/native-device/legacy-runtime/drain").statusCode())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_devices where account_id=?",
                    Int::class.java,
                    n.fixture.account,
                ),
        )
    }

    @Test
    fun groupParentRevocationAndOffboardingWithholdPreviouslyObservedStatus() {
        val n = groupNative()
        val base = groupBase(n)
        assertEquals(200, groupCall(n, "$base/identity", post = true).statusCode())
        val operation = UUID.randomUUID()
        assertEquals(
            200,
            groupCall(n, "$base/native-device", groupRegistration(), operation = operation)
                .statusCode(),
        )
        database()
            .update(
                "delete from membership_permissions where account_id=? and permission='collaboration.chat'",
                n.fixture.account,
            )
        for (path in
            listOf(
                "/api/v1/collaboration/spaces/current",
                "$base/contacts",
                "$base/native-device",
            )) failure(groupCall(n, path), 403, "collaboration_access_denied")
        failure(
            groupCall(n, "$base/native-device", groupRegistration(), operation = operation),
            403,
            "collaboration_access_denied",
        )
        database()
            .update(
                "update native_sessions set revoked_at=now(),version=version+1 where id=?",
                n.session,
            )
        assertEquals(401, groupCall(n, "$base/native-device").statusCode())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_devices where account_id=?",
                    Int::class.java,
                    n.fixture.account,
                ),
        )
    }

    @Test
    fun explicitBootstrapReplacementReturnsItsOwnPairAndClosesTheOriginalWithoutForgingAReceipt() {
        val n = groupNative()
        val base = groupBase(n)
        val device = activeGroupFixture(n, base)
        val original = UUID.randomUUID()
        val operation = UUID.randomUUID()
        val descriptor =
            mapOf(
                "operationId" to original,
                "spaceId" to base.substringAfterLast('/'),
                "accountId" to n.fixture.account,
                "nativeSessionId" to n.session,
                "deviceId" to device,
                "expectedDeviceVersion" to 1,
                "platform" to "ANDROID",
                "clientBuild" to 28,
            )
        val input =
            mapOf("deviceId" to device, "expectedDeviceVersion" to 1, "supersedes" to descriptor)
        val path = "$base/native-device/bootstrap/replacements"
        failure(
            groupCall(n, path, input, operation = operation, build = "27"),
            403,
            "client_update_required",
        )
        failure(
            groupCall(
                n,
                path,
                input + ("supersedes" to (descriptor + ("nativeSessionId" to UUID.randomUUID()))),
                operation = operation,
                build = "29",
            ),
            409,
            "collaboration_space_bootstrap_replacement_changed",
        )
        failure(
            groupCall(n, path, input, operation = original, build = "29"),
            409,
            "collaboration_space_bootstrap_replacement_changed",
        )
        val response = groupCall(n, path, input, operation = operation, build = "29")
        assertEquals(200, response.statusCode(), response.body())
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("private"))
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        val value = json.readTree(response.body())
        assertEquals(
            setOf("superseded", "replacementOperationId", "gateway"),
            value.propertyNames().toSet(),
        )
        val proof = value["superseded"]
        assertEquals(
            setOf(
                "operationId",
                "spaceId",
                "accountId",
                "nativeSessionId",
                "deviceId",
                "expectedDeviceVersion",
                "expectedGatewayVersion",
                "platform",
                "clientBuild",
            ),
            proof.propertyNames().toSet(),
        )
        assertEquals(original.toString(), proof["operationId"].asString())
        assertEquals(base.substringAfterLast('/'), proof["spaceId"].asString())
        assertEquals(n.fixture.account.toString(), proof["accountId"].asString())
        assertEquals(n.session.toString(), proof["nativeSessionId"].asString())
        assertEquals(device.toString(), proof["deviceId"].asString())
        assertEquals(1L, proof["expectedDeviceVersion"].asLong())
        assertTrue(proof["expectedGatewayVersion"].isNull)
        assertEquals("ANDROID", proof["platform"].asString())
        assertEquals(28, proof["clientBuild"].asInt())
        assertEquals(operation.toString(), value["replacementOperationId"].asString())
        val gateway = value["gateway"]
        assertEquals(device.toString(), gateway["deviceId"].asString())
        assertEquals(n.session.toString(), gateway["nativeSessionId"].asString())
        assertEquals(0L, gateway["version"].asLong())
        assertTrue(gateway["accessToken"].asString().startsWith("hris_mxg_a_"))
        assertTrue(gateway["refreshToken"].asString().startsWith("hris_mxg_r_"))
        assertEquals(
            value,
            json.readTree(groupCall(n, path, input, operation = operation, build = "29").body()),
        )
        assertEquals(
            29,
            database()
                .queryForObject(
                    "select client_build from collaboration_space_gateway_sessions where device_id=?",
                    Int::class.java,
                    device,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where operation_id=?",
                    Int::class.java,
                    original,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where operation_id=?",
                    Int::class.java,
                    operation,
                ),
        )
        failure(
            groupCall(
                n,
                "$base/native-device/bootstrap",
                mapOf("deviceId" to device, "expectedDeviceVersion" to 1),
                operation = original,
            ),
            409,
            "collaboration_space_gateway_changed",
        )
        failure(
            groupCall(
                n,
                path,
                input + ("supersedes" to (descriptor + ("operationId" to UUID.randomUUID()))),
                operation = operation,
                build = "29",
            ),
            409,
            "operation_payload_mismatch",
        )
        for (body in
            listOf(
                input - "supersedes",
                input - "expectedDeviceVersion",
                input + ("supersedes" to (descriptor - "clientBuild")),
            )) {
            assertEquals(400, groupCall(n, path, body, build = "29").statusCode())
        }
        assertEquals(400, groupCall(n, path, input, operation = null, build = "29").statusCode())
    }

    @Test
    fun groupGatewayHttpRecoversLostBootstrapUsingOwnedStatusThenRefreshesTheExactPair() {
        val n = groupNative()
        val base = groupBase(n)
        val device = activeGroupFixture(n, base)
        val input = mapOf("deviceId" to device, "expectedDeviceVersion" to 1)
        val operation = UUID.randomUUID()
        val first = groupCall(n, "$base/native-device/bootstrap", input, operation = operation)
        assertEquals(200, first.statusCode(), first.body())
        assertTrue(first.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        assertTrue(first.headers().firstValue("Cache-Control").orElse("").contains("private"))
        val data = json.readTree(first.body())
        assertEquals(
            setOf(
                "spaceId",
                "accountId",
                "deviceId",
                "nativeSessionId",
                "matrixUserId",
                "matrixDeviceId",
                "sessionId",
                "version",
                "accessToken",
                "refreshToken",
                "accessExpiresAt",
                "expiresAt",
            ),
            data.propertyNames().toSet(),
        )
        assertEquals(base.substringAfterLast('/'), data["spaceId"].asString())
        assertEquals(n.fixture.account.toString(), data["accountId"].asString())
        assertEquals(device.toString(), data["deviceId"].asString())
        assertEquals(n.session.toString(), data["nativeSessionId"].asString())
        val original =
            database()
                .queryForMap(
                    "select matrix_user_id,matrix_device_id from collaboration_space_devices where id=?",
                    device,
                )
        assertEquals(original["matrix_user_id"], data["matrixUserId"].asString())
        assertEquals(original["matrix_device_id"], data["matrixDeviceId"].asString())
        assertTrue(data["accessToken"].asString().startsWith("hris_mxg_a_"))
        assertTrue(data["refreshToken"].asString().startsWith("hris_mxg_r_"))
        assertEquals(
            data,
            json.readTree(
                groupCall(n, "$base/native-device/bootstrap", input, operation = operation).body()
            ),
        )
        advanceGroupClock(31)
        failure(
            groupCall(n, "$base/native-device/bootstrap", input, operation = operation),
            401,
            "collaboration_space_gateway_authentication_required",
        )
        val status = groupCall(n, "$base/native-device")
        assertEquals(200, status.statusCode(), status.body())
        val gateway = json.readTree(status.body())["device"]["gateway"]
        assertEquals(
            setOf("id", "version", "clientBuild", "accessExpiresAt", "expiresAt", "revokedAt"),
            gateway.propertyNames().toSet(),
        )
        assertEquals(data["sessionId"].asString(), gateway["id"].asString())
        assertEquals(0L, gateway["version"].asLong())
        val rotated =
            groupCall(
                n,
                "$base/native-device/bootstrap",
                input + ("expectedGatewayVersion" to gateway["version"].asLong()),
            )
        assertEquals(200, rotated.statusCode(), rotated.body())
        val next = json.readTree(rotated.body())
        assertEquals(1L, next["version"].asLong())
        assertEquals(data["expiresAt"], next["expiresAt"])
        assertEquals(data["matrixDeviceId"], next["matrixDeviceId"])
        assertNotEquals(data["refreshToken"], next["refreshToken"])
        assertEquals(429, groupRefresh(next["refreshToken"].asString()).statusCode())
        advanceGroupClock(11)
        val refresh = groupRefresh(next["refreshToken"].asString())
        assertEquals(200, refresh.statusCode(), refresh.body())
        val refreshed = json.readTree(refresh.body())
        assertEquals(
            setOf("access_token", "refresh_token", "expires_in_ms"),
            refreshed.propertyNames().toSet(),
        )
        assertEquals(300000L, refreshed["expires_in_ms"].asLong())
        assertTrue(refresh.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        assertEquals(refreshed, json.readTree(groupRefresh(next["refreshToken"].asString()).body()))
        assertEquals(
            2L,
            json
                .readTree(groupCall(n, "$base/native-device").body())["device"]["gateway"][
                    "version"]
                .asLong(),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_gateway_sessions where account_id=?",
                    Int::class.java,
                    n.fixture.account,
                ),
        )
    }

    @Test
    fun groupGatewayHttpRejectsForeignParentsInvalidVersionsAndMissingNativeTransport() {
        val n = groupNative()
        val base = groupBase(n)
        val device = activeGroupFixture(n, base)
        val input = mapOf("deviceId" to device, "expectedDeviceVersion" to 1)
        val path = "$base/native-device/bootstrap"
        assertEquals(400, groupCall(n, path, input, operation = null).statusCode())
        assertEquals(400, groupCall(n, path, mapOf("deviceId" to device)).statusCode())
        assertEquals(403, groupCall(n, path, input, token = null, cookies = false).statusCode())
        assertEquals(403, groupCall(n, path, input, token = null).statusCode())
        for (bad in listOf(-1L, 1000000L)) failure(
            groupCall(n, path, input + ("expectedDeviceVersion" to bad)),
            409,
            "collaboration_space_gateway_changed",
        )
        for (bad in listOf(-1L, 999999L)) failure(
            groupCall(n, path, input + ("expectedGatewayVersion" to bad)),
            409,
            "collaboration_space_gateway_changed",
        )
        val peer = groupNative()
        failure(groupCall(peer, path, input), 403, "collaboration_access_denied")
        failure(
            groupCall(
                n,
                path.replace(base.substringAfterLast('/'), UUID.randomUUID().toString()),
                input,
            ),
            403,
            "collaboration_access_denied",
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_gateway_sessions where account_id=?",
                    Int::class.java,
                    n.fixture.account,
                ),
        )
    }

    @Test
    fun groupGatewayHttpRechecksOffboardingForBootstrapReplayStatusAndRefresh() {
        val n = groupNative()
        val base = groupBase(n)
        val device = activeGroupFixture(n, base)
        val input = mapOf("deviceId" to device, "expectedDeviceVersion" to 1)
        val operation = UUID.randomUUID()
        val issued = groupCall(n, "$base/native-device/bootstrap", input, operation = operation)
        assertEquals(200, issued.statusCode(), issued.body())
        val first = json.readTree(issued.body())
        advanceGroupClock(11)
        val rotation = groupRefresh(first["refreshToken"].asString())
        assertEquals(200, rotation.statusCode(), rotation.body())
        val next = json.readTree(rotation.body())
        database()
            .update(
                "delete from membership_permissions where account_id=? and permission='collaboration.chat'",
                n.fixture.account,
            )
        failure(
            groupCall(n, "$base/native-device/bootstrap", input, operation = operation),
            403,
            "collaboration_access_denied",
        )
        failure(groupCall(n, "$base/native-device"), 403, "collaboration_access_denied")
        for (token in listOf(first["refreshToken"].asString(), next["refresh_token"].asString())) {
            val response = groupRefresh(token)
            assertEquals(403, response.statusCode(), response.body())
            assertEquals("M_FORBIDDEN", json.readTree(response.body())["errcode"].asString())
            assertFalse(response.body().contains(token))
        }
        assertEquals(
            1L,
            database()
                .queryForObject(
                    "select version from collaboration_space_gateway_sessions where account_id=?",
                    Long::class.java,
                    n.fixture.account,
                ),
        )
    }

    @Test
    fun groupRefreshStrictParsingDoesNotAcceptAmbiguousOrOversizedCredentials() {
        val token = "hris_mxg_r_" + "a".repeat(43)
        val valid = json.writeValueAsString(mapOf("refresh_token" to token))
        for (body in
            listOf(
                "[]",
                "{}",
                "{\"refresh_token\":null}",
                valid + "{}",
                "{\"refresh_token\":\"$token\",\"refresh_token\":\"$token\"}",
                "{\"refresh_token\":\"$token\",\"extra\":true}",
            )) {
            val response = groupRefreshBody(body)
            assertEquals(400, response.statusCode(), response.body())
            assertEquals("M_BAD_JSON", json.readTree(response.body())["errcode"].asString())
        }
        assertEquals(413, groupRefreshBody(" ".repeat(2049)).statusCode())
        assertEquals(415, groupRefreshBody(valid, "text/plain").statusCode())
        for (unknown in
            listOf(
                token,
                "hris_mx_r_" + "a".repeat(43),
                "hris_mxg_a_" + "a".repeat(43),
                "unknown",
            )) {
            val response = groupRefresh(unknown)
            assertEquals(401, response.statusCode(), response.body())
            assertEquals("M_UNKNOWN_TOKEN", json.readTree(response.body())["errcode"].asString())
            assertFalse(response.body().contains(unknown))
        }
    }

    @Test
    fun nativeProviderRenewalHttpRecoversSafeReceiptAndRechecksOffboarding() {
        val n = groupNative()
        val base = groupBase(n)
        val device = activeGroupFixture(n, base)
        val path = "$base/native-device/provider/renew"
        val input = mapOf("deviceId" to device, "expectedDeviceVersion" to 1)
        val operation = UUID.randomUUID()
        val response = groupCall(n, path, input, operation = operation)
        assertEquals(200, response.statusCode(), response.body())
        assertTrue(
            response.headers().firstValue("Cache-Control").orElse("").contains("private, no-store")
        )
        val body = json.readTree(response.body())
        assertEquals(setOf("deviceId", "providerGeneration"), body.propertyNames().toSet())
        assertEquals(device.toString(), body["deviceId"].asString())
        assertEquals(0L, body["providerGeneration"].asLong())
        assertEquals(body, json.readTree(groupCall(n, path, input, operation = operation).body()))
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_request_leases where account_id=?",
                    Int::class.java,
                    n.fixture.account,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where actor_id=? and operation='collaboration.space_provider_renewal'",
                    Int::class.java,
                    n.fixture.account,
                ),
        )
        database()
            .update(
                "update company_memberships set active=false,version=version+1 where account_id=?",
                n.fixture.account,
            )
        failure(
            groupCall(n, path, input, operation = operation),
            403,
            "collaboration_access_denied",
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where actor_id=? and operation='collaboration.space_provider_renewal'",
                    Int::class.java,
                    n.fixture.account,
                ),
        )
    }

    @Test
    fun nativeProviderRenewalHttpRequiresOwnedDeviceCurrentBuildVersionsAndNativeTransport() {
        val n = groupNative()
        val base = groupBase(n)
        val device = activeGroupFixture(n, base)
        val path = "$base/native-device/provider/renew"
        val input = mapOf("deviceId" to device, "expectedDeviceVersion" to 1)
        assertEquals(400, groupCall(n, path, input, operation = null).statusCode())
        assertEquals(400, groupCall(n, path, mapOf("deviceId" to device)).statusCode())
        assertEquals(400, groupCall(n, path, mapOf("expectedDeviceVersion" to 1)).statusCode())
        assertEquals(403, groupCall(n, path, input, token = null, cookies = false).statusCode())
        assertEquals(403, groupCall(n, path, input, token = null).statusCode())
        for (bad in listOf(-1L, 0L, 1000000L)) failure(
            groupCall(n, path, input + ("expectedDeviceVersion" to bad)),
            409,
            "collaboration_space_provider_changed",
        )
        failure(groupCall(n, path, input, build = "27"), 403, "client_update_required")
        failure(groupCall(groupNative(), path, input), 403, "collaboration_access_denied")
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where actor_id=? and operation='collaboration.space_provider_renewal'",
                    Int::class.java,
                    n.fixture.account,
                ),
        )
    }

    private fun advanceGroupClock(seconds: Long) {
        require(seconds in 1..31)
        val target = clock.instant().plusSeconds(seconds)
        // Publication SQL also observes real time. Exercise replay/cooldown expiry instead
        // of creating a future gateway row or an already-expired registration request.
        val remaining = java.time.Duration.between(Instant.now(), target).toNanos()
        if (remaining > 0) java.util.concurrent.TimeUnit.NANOSECONDS.sleep(remaining + 20_000_000)
        clock.set(target)
    }

    /** Stored provider metadata only: API tests must not make external provider calls. */
    private fun activeGroupFixture(n: Native, base: String): UUID {
        assertEquals(200, groupCall(n, "$base/identity", post = true).statusCode())
        val registered = groupCall(n, "$base/native-device", groupRegistration())
        assertEquals(200, registered.statusCode(), registered.body())
        val id = UUID.fromString(json.readTree(registered.body())["id"].asString())
        val db = database()
        val at = clock.instant().toString()
        db.update(
            "update collaboration_space_provisioning set state='RUNNING', attempts=1, lease_owner=?,lease_token=?,lease_until=least(expires_at,clock_timestamp()+interval '30 seconds') where device_id=?",
            UUID.randomUUID(),
            UUID.randomUUID(),
            id,
        )
        db.update(
            "insert into collaboration_space_provider_sessions(space_id,device_id,account_id,native_session_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) select space_id,id,account_id,origin_native_session_id,matrix_user_id,matrix_device_id,0,'fixture-not-a-provider-token',?::timestamptz,?::timestamptz,?::timestamptz+interval '5 minutes' from collaboration_space_devices where id=?",
            at,
            at,
            at,
            id,
        )
        db.update(
            "update collaboration_space_devices set state='ACTIVE',version=version+1,updated_at=?::timestamptz where id=?",
            at,
            id,
        )
        db.update(
            "update collaboration_space_provisioning set state='SUCCEEDED',lease_owner=null,lease_token=null,lease_until=null,completed_at=?::timestamptz,last_outcome_revision=revision,last_outcome_state='SUCCEEDED',last_outcome_at=?::timestamptz where device_id=?",
            at,
            at,
            id,
        )
        return id
    }

    private fun groupRefresh(token: String) =
        groupRefreshBody(json.writeValueAsString(mapOf("refresh_token" to token)))

    private fun groupRefreshBody(
        body: String,
        contentType: String = "application/json",
    ): java.net.http.HttpResponse<String> =
        java.net.http.HttpClient.newHttpClient().use {
            it.send(
                java.net.http.HttpRequest.newBuilder(
                        java.net.URI("http://127.0.0.1:$port/_matrix/client/v3/refresh")
                    )
                    .timeout(java.time.Duration.ofSeconds(15))
                    .header("Content-Type", contentType)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString(),
            )
        }

    @Test
    fun oppositeCompanyEmployeesOpenOneOwnedGroupConversationWithoutSelectingPeerKeys() {
        val a = groupNative()
        val b = groupNative()
        val unrelated = groupNative()
        val base = groupBase(a)
        val path = "$base/direct-conversations"
        val operation = UUID.randomUUID()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        val ready = java.util.concurrent.CountDownLatch(2)
        val start = java.util.concurrent.CountDownLatch(1)
        val outcomes =
            try {
                val requests =
                    listOf(a to b, b to a).map { (caller, peer) ->
                        pool.submit<java.net.http.HttpResponse<String>> {
                            ready.countDown()
                            assertTrue(start.await(10, java.util.concurrent.TimeUnit.SECONDS))
                            groupCall(
                                caller,
                                path,
                                mapOf("peerAccountId" to peer.fixture.account.toString()),
                                operation = operation,
                            )
                        }
                    }
                assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS))
                start.countDown()
                requests.map { it.get(20, java.util.concurrent.TimeUnit.SECONDS) }
            } finally {
                pool.shutdownNow()
                assertTrue(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS))
            }
        outcomes.forEach { assertEquals(200, it.statusCode(), it.body()) }
        assertEquals(json.readTree(outcomes[0].body()), json.readTree(outcomes[1].body()))
        val receipt = json.readTree(outcomes[0].body())
        assertEquals(0L, receipt["version"].asLong())
        val id = receipt["id"].asString()
        val replay =
            groupCall(
                a,
                path,
                mapOf("peerAccountId" to b.fixture.account.toString()),
                operation = operation,
            )
        assertEquals(200, replay.statusCode(), replay.body())
        assertEquals(receipt, json.readTree(replay.body()))
        val detail = groupCall(a, "$path/$id")
        assertEquals(200, detail.statusCode(), detail.body())
        assertEquals("no-store", detail.headers().firstValue("Cache-Control").orElse(""))
        val value = json.readTree(detail.body())
        assertEquals(
            setOf(
                "id",
                "spaceId",
                "firstAccountId",
                "secondAccountId",
                "createdBy",
                "createdAt",
                "version",
            ),
            value.propertyNames().toSet(),
        )
        assertEquals(
            setOf(a.fixture.account.toString(), b.fixture.account.toString()),
            setOf(value["firstAccountId"].asString(), value["secondAccountId"].asString()),
        )
        assertEquals(value, json.readTree(groupCall(b, "$path/$id").body()))
        val page = json.readTree(groupCall(a, "$path?limit=1").body())
        assertEquals(value, page["items"][0])
        assertTrue(page["nextCursor"].isNull)
        val end = json.readTree(groupCall(a, "$path?after=$id&limit=1").body())
        assertTrue(end["items"].isEmpty)
        assertTrue(end["nextCursor"].isNull)
        failure(groupCall(unrelated, "$path/$id"), 404, "collaboration_conversation_not_found")
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_identities where account_id in (?,?)",
                    Int::class.java,
                    a.fixture.account,
                    b.fixture.account,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_direct_conversations where id=?",
                    Int::class.java,
                    UUID.fromString(id),
                ),
        )
    }

    @Test
    fun groupOpenRechecksPeerAndClientWhileOwnedReadRetainsUnavailableConversation() {
        val a = groupNative()
        val b = groupNative()
        val path = "${groupBase(a)}/direct-conversations"
        val input = mapOf("peerAccountId" to b.fixture.account.toString())
        failure(
            groupCall(a, path, mapOf("peerAccountId" to a.fixture.account.toString())),
            422,
            "invalid_collaboration_peer",
        )
        failure(groupCall(a, path, input, build = "27"), 403, "client_update_required")
        assertEquals(400, groupCall(a, path, input, operation = null).statusCode())
        failure(groupCall(a, "$path?limit=51"), 422, "invalid_collaboration_conversation_query")
        assertEquals(400, groupCall(a, "$path?after=invalid").statusCode())
        val operation = UUID.randomUUID()
        val opened = groupCall(a, path, input, operation = operation)
        assertEquals(200, opened.statusCode(), opened.body())
        val id = json.readTree(opened.body())["id"].asString()
        failure(
            groupCall(
                a,
                path,
                mapOf("peerAccountId" to UUID.randomUUID().toString()),
                operation = operation,
            ),
            409,
            "operation_payload_mismatch",
        )
        database()
            .update(
                "delete from membership_permissions where account_id=? and permission='collaboration.chat'",
                b.fixture.account,
            )
        failure(
            groupCall(a, path, input, operation = operation),
            403,
            "collaboration_peer_unavailable",
        )
        assertEquals(200, groupCall(a, "$path/$id").statusCode())
        failure(groupCall(b, "$path/$id"), 403, "collaboration_access_denied")
        assertEquals(403, groupCall(a, path, input, token = null).statusCode())
        assertEquals(401, groupCall(a, path, token = null, cookies = false).statusCode())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_direct_conversations where id=?",
                    Int::class.java,
                    UUID.fromString(id),
                ),
        )
    }

    @Test
    fun originalConversationInventoryRequiresBothCursorCoordinatesAndCurrentNativeOwnership() {
        val a = groupNative()
        val b = groupNative()
        val unrelated = groupNative()
        val base = groupBase(a)
        val path = "$base/legacy-conversations"
        val pair = listOf(a.fixture.account, b.fixture.account).sortedBy(UUID::toString)
        val companies = listOf(a.fixture.company, b.fixture.company).sortedBy(UUID::toString)
        val originalId = UUID.randomUUID()
        for (company in companies) database()
            .update(
                "insert into collaboration_direct_conversations(company_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,?::timestamptz)",
                company,
                originalId,
                pair[0],
                pair[1],
                a.fixture.account,
                clock.instant().minusSeconds(1).toString(),
            )
        seedGroupLegacyRoom(b, originalId)
        val response = groupCall(a, "$path?limit=1")
        assertEquals(200, response.statusCode(), response.body())
        val first = json.readTree(response.body())
        assertEquals(
            companies[0].toString(),
            first["items"][0]["conversation"]["companyId"].asString(),
        )
        fun assertOriginalRoom(item: tools.jackson.databind.JsonNode) {
            assertEquals(a.fixture.account.toString(), item["conversation"]["createdBy"].asString())
            if (item["conversation"]["companyId"].asString() == b.fixture.company.toString()) {
                assertEquals(b.fixture.account.toString(), item["room"]["createdBy"].asString())
                assertEquals("!original:matrix.example.test", item["room"]["roomId"].asString())
                assertEquals(
                    setOf("serverName", "roomId", "boundAt", "createdBy"),
                    item["room"].propertyNames().toSet(),
                )
            } else assertTrue(item["room"].isNull)
        }
        assertOriginalRoom(first["items"][0])
        assertEquals(companies[0].toString(), first["next"]["companyId"].asString())
        assertEquals(originalId.toString(), first["next"]["conversationId"].asString())
        val last =
            json.readTree(
                groupCall(
                        a,
                        "$path?afterCompanyId=${companies[0]}&afterConversationId=$originalId&limit=1",
                    )
                    .body()
            )
        assertEquals(
            companies[1].toString(),
            last["items"][0]["conversation"]["companyId"].asString(),
        )
        assertTrue(last["next"].isNull)
        assertOriginalRoom(last["items"][0])
        val peerDetail = groupCall(b, "$path/${b.fixture.company}/$originalId")
        assertEquals(200, peerDetail.statusCode(), peerDetail.body())
        assertOriginalRoom(json.readTree(peerDetail.body()))
        val detail = groupCall(a, "$path/${companies[1]}/$originalId")
        assertEquals(200, detail.statusCode(), detail.body())
        assertEquals(last["items"][0], json.readTree(detail.body()))
        failure(
            groupCall(unrelated, "$path/${companies[1]}/$originalId"),
            404,
            "collaboration_conversation_not_found",
        )
        failure(
            groupCall(a, "$path?afterCompanyId=${companies[0]}"),
            422,
            "invalid_collaboration_conversation_query",
        )
        failure(
            groupCall(a, "$path?afterConversationId=$originalId"),
            422,
            "invalid_collaboration_conversation_query",
        )
        failure(groupCall(a, "$path?limit=0"), 422, "invalid_collaboration_conversation_query")
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_direct_conversations where created_by=?",
                    Int::class.java,
                    a.fixture.account,
                ),
        )
    }

    private fun exchangeRoomNative(n: Native): Pair<Native, String> {
        val response =
            command(
                n.fixture.browser,
                "/api/v1/auth/native/exchange",
                """{"deviceName":"Stable room intent fixture"}""",
                n.fixture.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, response.statusCode(), response.body())
        val body = json.readTree(response.body())
        return Native(
            n.fixture,
            UUID.fromString(body["sessionId"].asString()),
            body["accessToken"].asString(),
        ) to body["refreshToken"].asString()
    }

    private fun refreshRoomNative(n: Native, token: String): Pair<Native, String> {
        Thread.sleep(11_000)
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS))
        val response =
            groupCall(
                n,
                "/api/v1/auth/native/refresh",
                mapOf("refreshToken" to token),
                token = null,
                cookies = false,
            )
        assertEquals(200, response.statusCode(), response.body())
        val body = json.readTree(response.body())
        assertEquals(n.session.toString(), body["sessionId"].asString())
        assertFalse(body.has("nativeSessionVersion"))
        return Native(n.fixture, n.session, body["accessToken"].asString()) to
            body["refreshToken"].asString()
    }

    @Test
    fun stableReceiptReadBoundsInputAndLeavesUncertainAbsenceWithoutAnyOperation() {
        val a = groupNative()
        val b = groupNative()
        val base = groupBase(a)
        val opened =
            groupCall(
                a,
                "$base/direct-conversations",
                mapOf("peerAccountId" to b.fixture.account.toString()),
            )
        assertEquals(200, opened.statusCode(), opened.body())
        val conversation = json.readTree(opened.body())["id"].asString()
        val room = "$base/direct-conversations/$conversation/room/requests"
        val path = "$room/receipts/read"
        val input =
            mapOf(
                "operationId" to UUID.randomUUID().toString(),
                "deviceId" to UUID.randomUUID().toString(),
                "expectedDeviceVersion" to 0,
            )
        val absent = groupCall(a, path, input, operation = null, cookies = false)
        assertEquals(200, absent.statusCode(), absent.body())
        assertTrue(json.readTree(absent.body())["receipt"].isNull)
        assertEquals(
            400,
            groupCall(a, path, input - "expectedDeviceVersion", operation = null).statusCode(),
        )
        assertEquals(
            400,
            groupCall(a, path, input + mapOf("operationId" to "bad"), operation = null).statusCode(),
        )
        for (invalid in
            listOf(
                input + mapOf("deviceId" to UUID(0, 0).toString()),
                input + mapOf("expectedDeviceVersion" to -1),
                input + mapOf("expectedDeviceVersion" to 1_000_000),
                input + mapOf("expectedRequestRevision" to 0),
                input + mapOf("expectedRequestAccountId" to b.fixture.account.toString()),
            )) {
            failure(
                groupCall(a, path, invalid, operation = null),
                422,
                "invalid_collaboration_room_operation",
            )
        }
        assertEquals(400, groupCall(a, room, input - "operationId", operation = null).statusCode())
        assertEquals(
            403,
            groupCall(a, path, input, operation = null, token = null, cookies = false).statusCode(),
        )
        failure(
            groupCall(a, path, input, operation = null, build = "27"),
            403,
            "client_update_required",
        )
        assertEquals(
            413,
            groupCall(a, path, input + mapOf("unused" to "x".repeat(1_048_577)), operation = null)
                .statusCode(),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where actor_id=? and operation='collaboration.space_room_setup.v2'",
                    Int::class.java,
                    a.fixture.account,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_room_provisioning where conversation_id=?::uuid",
                    Int::class.java,
                    conversation,
                ),
        )
    }

    @Test
    fun nativeFenceIsExactPrivateEvidenceAcrossRefreshAndOriginalParentPurge() {
        val (a, refreshToken) = exchangeRoomNative(groupNative())
        val b = groupNative()
        val outsider = groupNative()
        val base = groupBase(a)
        val opened =
            groupCall(
                a,
                "$base/direct-conversations",
                mapOf("peerAccountId" to b.fixture.account.toString()),
            )
        assertEquals(200, opened.statusCode(), opened.body())
        val conversation = json.readTree(opened.body())["id"].asString()
        val room = "$base/direct-conversations/$conversation/room/requests"
        val path = "$room/fence"
        val operation = UUID.randomUUID()
        val original =
            mapOf(
                "operationId" to operation.toString(),
                "deviceId" to UUID.randomUUID().toString(),
                "expectedDeviceVersion" to 0,
                "expectedRequestAccountId" to b.fixture.account.toString(),
                "expectedRequestRevision" to 0,
            )
        val first = groupCall(a, path, original, operation = operation, cookies = false)
        assertEquals(200, first.statusCode(), first.body())
        assertEquals("no-store", first.headers().firstValue("Cache-Control").orElse(""))
        assertTrue(first.body().toByteArray().size < 4096)
        val evidence = json.readTree(first.body())
        assertEquals(
            setOf("accountId", "original", "state", "receipt", "fence"),
            evidence.propertyNames().toSet(),
        )
        assertEquals(a.fixture.account.toString(), evidence["accountId"].asString())
        assertEquals("FENCED", evidence["state"].asString())
        assertTrue(evidence["receipt"].isNull)
        assertEquals(setOf("id", "createdAt"), evidence["fence"].propertyNames().toSet())
        assertNotEquals(UUID(0, 0), UUID.fromString(evidence["fence"]["id"].asString()))
        assertEquals(0, Instant.parse(evidence["fence"]["createdAt"].asString()).nano % 1000)
        assertEquals(
            json.readTree(
                json.writeValueAsString(
                    original +
                        mapOf(
                            "spaceId" to base.substringAfterLast('/'),
                            "conversationId" to conversation,
                        )
                )
            ),
            evidence["original"],
        )
        val replay = groupCall(a, path, original, operation = operation, cookies = false)
        assertEquals(200, replay.statusCode(), replay.body())
        assertEquals(evidence, json.readTree(replay.body()))
        // Neither participant selected a provider identity; fencing grants no room authority.
        failure(
            groupCall(a, room, original - "operationId", operation = operation),
            403,
            "collaboration_peer_unavailable",
        )
        failure(
            groupCall(outsider, path, original, operation = operation),
            404,
            "collaboration_conversation_not_found",
        )
        val (rotated, _) = refreshRoomNative(a, refreshToken)
        val refreshed =
            groupCall(rotated, path, original, operation = operation, build = "29", cookies = false)
        assertEquals(200, refreshed.statusCode(), refreshed.body())
        assertEquals(evidence, json.readTree(refreshed.body()))
        val (replacement, _) = exchangeRoomNative(rotated)
        database().update("update native_sessions set revoked_at=now() where id=?", a.session)
        assertEquals(1, database().update("delete from native_sessions where id=?", a.session))
        val recovered =
            groupCall(
                replacement,
                path,
                original,
                operation = operation,
                build = "29",
                cookies = false,
            )
        assertEquals(200, recovered.statusCode(), recovered.body())
        assertEquals(evidence, json.readTree(recovered.body()))
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_devices where account_id=?",
                    Int::class.java,
                    a.fixture.account,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_room_request_fences where account_id=?",
                    Int::class.java,
                    a.fixture.account,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_room_request_fences where account_id=?",
                    Int::class.java,
                    outsider.fixture.account,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where actor_id=? and operation='collaboration.space_room_setup.v2'",
                    Int::class.java,
                    a.fixture.account,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_room_provisioning where conversation_id=?::uuid",
                    Int::class.java,
                    conversation,
                ),
        )
        val changed = original + mapOf("expectedDeviceVersion" to 1)
        failure(
            groupCall(replacement, path, changed, operation = operation),
            409,
            "operation_payload_mismatch",
        )
        database()
            .update(
                "delete from membership_permissions where account_id=? and permission='collaboration.chat'",
                b.fixture.account,
            )
        failure(
            groupCall(replacement, path, changed, operation = operation),
            403,
            "collaboration_peer_unavailable",
        )
        failure(
            groupCall(replacement, path, original, operation = operation),
            403,
            "collaboration_peer_unavailable",
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_room_request_fences where account_id=?",
                    Int::class.java,
                    a.fixture.account,
                ),
        )
    }

    @Test
    fun nativeFenceRequiresMatchingOriginalHeaderCompleteBoundsAndCurrentNativeTransport() {
        val a = groupNative()
        val b = groupNative()
        val base = groupBase(a)
        val opened =
            groupCall(
                a,
                "$base/direct-conversations",
                mapOf("peerAccountId" to b.fixture.account.toString()),
            )
        assertEquals(200, opened.statusCode(), opened.body())
        val conversation = json.readTree(opened.body())["id"].asString()
        val path = "$base/direct-conversations/$conversation/room/requests/fence"
        val operation = UUID.randomUUID()
        val original =
            mapOf(
                "operationId" to operation.toString(),
                "deviceId" to UUID.randomUUID().toString(),
                "expectedDeviceVersion" to 0,
            )
        assertEquals(400, groupCall(a, path, original, operation = null).statusCode())
        for (missing in listOf("operationId", "deviceId", "expectedDeviceVersion")) {
            assertEquals(
                400,
                groupCall(a, path, original - missing, operation = operation).statusCode(),
            )
        }
        assertEquals(
            400,
            groupCall(a, path, original + mapOf("operationId" to "bad"), operation = operation)
                .statusCode(),
        )
        failure(
            groupCall(a, path, original, operation = UUID.randomUUID()),
            422,
            "invalid_collaboration_room_operation",
        )
        for (invalid in
            listOf(
                original + mapOf("deviceId" to UUID(0, 0).toString()),
                original + mapOf("expectedDeviceVersion" to -1),
                original + mapOf("expectedDeviceVersion" to 1_000_000),
                original + mapOf("expectedRequestRevision" to 0),
                original + mapOf("expectedRequestAccountId" to b.fixture.account.toString()),
                original +
                    mapOf(
                        "expectedRequestAccountId" to b.fixture.account.toString(),
                        "expectedRequestRevision" to 1_000_000,
                    ),
                original +
                    mapOf(
                        "expectedRequestAccountId" to UUID.randomUUID().toString(),
                        "expectedRequestRevision" to 0,
                    ),
            )) {
            failure(
                groupCall(a, path, invalid, operation = operation),
                422,
                "invalid_collaboration_room_operation",
            )
        }
        assertEquals(
            403,
            groupCall(a, path, original, operation = operation, token = null, cookies = false)
                .statusCode(),
        )
        assertEquals(
            403,
            groupCall(a, path, original, operation = operation, token = null).statusCode(),
        )
        failure(
            groupCall(a, path, original, operation = operation, build = "27"),
            403,
            "client_update_required",
        )
        assertEquals(
            413,
            groupCall(
                    a,
                    path,
                    original + mapOf("unused" to "x".repeat(1_048_577)),
                    operation = operation,
                )
                .statusCode(),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_room_request_fences where account_id=?",
                    Int::class.java,
                    a.fixture.account,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where actor_id=? and operation='collaboration.space_room_setup.v2'",
                    Int::class.java,
                    a.fixture.account,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_room_provisioning where conversation_id=?::uuid",
                    Int::class.java,
                    conversation,
                ),
        )
    }

    private fun groupNative(grant: Boolean = true): Native {
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS))
        val admin = fixture()
        val account = UUID.randomUUID()
        val email = "$account@example.test"
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Group HTTP employee',password_hash from accounts where email='admin@example.test'",
                account,
                email,
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                admin.company,
                account,
            )
        if (grant)
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                    admin.company,
                    account,
                )
        val browser = client()
        val csrf = login(browser, email)
        val exchanged =
            command(
                browser,
                "/api/v1/auth/native/exchange",
                """{"deviceName":"Group HTTP fixture"}""",
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, exchanged.statusCode(), exchanged.body())
        val data = json.readTree(exchanged.body())
        val actor =
            Actor(
                account,
                admin.company,
                if (grant) setOf("collaboration.chat") else emptySet(),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        return Native(
            Fixture(browser, csrf, admin.company, account, actor),
            UUID.fromString(data["sessionId"].asString()),
            data["accessToken"].asString(),
        )
    }

    private fun groupBase(n: Native): String {
        val response = groupCall(n, "/api/v1/collaboration/spaces/current")
        assertEquals(200, response.statusCode(), response.body())
        return "/api/v1/collaboration/spaces/${json.readTree(response.body())["spaceId"].asString()}"
    }

    private fun groupRegistration(): Map<String, Any> =
        mapOf("platform" to "ANDROID", "label" to "Fictional group device", "origin" to "SPACE")

    private fun groupCall(
        n: Native,
        path: String,
        body: Map<String, Any>? = null,
        post: Boolean = body != null,
        operation: UUID? = UUID.randomUUID(),
        build: String = "28",
        platform: String = "ANDROID",
        token: String? = n.access,
        cookies: Boolean = true,
    ): java.net.http.HttpResponse<String> {
        val request =
            java.net.http.HttpRequest.newBuilder(java.net.URI("http://127.0.0.1:$port$path"))
                .timeout(java.time.Duration.ofSeconds(15))
                .header("X-HRIS-Client-Platform", platform)
                .header("X-HRIS-Client-Build", build)
        token?.let { request.header("Authorization", "Bearer $it") }
        if (post) {
            operation?.let { request.header("Idempotency-Key", it.toString()) }
            request
                .header("Content-Type", "application/json")
                .POST(
                    java.net.http.HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(body ?: emptyMap<String, Any>())
                    )
                )
        } else request.GET()
        return if (cookies)
            n.fixture.browser.send(
                request.build(),
                java.net.http.HttpResponse.BodyHandlers.ofString(),
            )
        else
            java.net.http.HttpClient.newHttpClient().use {
                it.send(request.build(), java.net.http.HttpResponse.BodyHandlers.ofString())
            }
    }

    private fun seedGroupLegacyRoom(n: Native, conversation: UUID) {
        val device = seedGroupLegacy(n)
        val job = UUID.randomUUID()
        val db = database()
        val at = clock.instant().minusSeconds(1).toString()
        db.update(
            """insert into background_jobs(id,company_id,actor_id,kind,operation_id,request,authenticated_at,credential_version,correlation_id,created_at,total_items)
            values(?,?,?,'COLLABORATION_DIRECT_PROVISION',?,jsonb_build_object('conversationId',?::text,'deviceId',?::text,'nativeSessionId',?::text,'platform','ANDROID','clientBuild','28'),?::timestamptz,0,?,?::timestamptz,1)""",
            job,
            n.fixture.company,
            n.fixture.account,
            UUID.randomUUID(),
            conversation,
            device,
            n.session,
            at,
            UUID.randomUUID(),
            at,
        )
        db.update(
            "insert into collaboration_direct_room_provisioning(company_id,conversation_id,account_id,device_id,job_id,version,created_at,updated_at) values(?,?,?,?,?,0,?::timestamptz,?::timestamptz)",
            n.fixture.company,
            conversation,
            n.fixture.account,
            device,
            job,
            at,
            at,
        )
        db.update(
            "update background_jobs set status='RUNNING',attempts=1,lease_owner=gen_random_uuid(),lease_token=gen_random_uuid(),lease_until=now()+interval '1 minute' where id=?",
            job,
        )
        db.update(
            "insert into collaboration_direct_room_bindings(company_id,conversation_id,account_id,job_id,provisioning_version,matrix_server_name,matrix_room_id,created_at) values(?,?,?,?,0,'matrix.example.test','!original:matrix.example.test',?::timestamptz)",
            n.fixture.company,
            conversation,
            n.fixture.account,
            job,
            at,
        )
    }

    private fun seedGroupLegacy(n: Native): UUID {
        val device = UUID.randomUUID()
        val at = clock.instant().minusSeconds(1).toString()
        val db = database()
        db.update(
            "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional retained HTTP device','PROVISIONING',0,?::timestamptz,?::timestamptz)",
            n.fixture.company,
            device,
            n.fixture.account,
            at,
            at,
        )
        db.update(
            "insert into collaboration_device_sessions(company_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?::timestamptz)",
            n.fixture.company,
            device,
            n.fixture.account,
            n.session,
            at,
        )
        db.update(
            "insert into collaboration_provider_sessions(company_id,device_id,account_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) values(?,?,?,?,?,0,'fixture-envelope-not-a-token',?::timestamptz,?::timestamptz,?::timestamptz+interval '5 minutes')",
            n.fixture.company,
            device,
            n.fixture.account,
            "@hris_${n.fixture.company.toString().replace("-", "")}_${n.fixture.account.toString().replace("-", "")}:matrix.example.test",
            "HRIS_${device.toString().replace("-", "")}",
            at,
            at,
            at,
        )
        db.update(
            "update collaboration_devices set state='ACTIVE',version=1 where company_id=? and id=?",
            n.fixture.company,
            device,
        )
        db.update(
            "insert into collaboration_device_key_publications(company_id,device_id,account_id,fingerprint,version,reserved_at) values(?,?,?, ?,0,?::timestamptz)",
            n.fixture.company,
            device,
            n.fixture.account,
            "a".repeat(64),
            at,
        )
        return device
    }
}
