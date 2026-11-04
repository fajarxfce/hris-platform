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
            listOf("/api/v1/collaboration/spaces/current", "$base/contacts", "$base/native-device")
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
        val response = groupCall(a, "$path?limit=1")
        assertEquals(200, response.statusCode(), response.body())
        val first = json.readTree(response.body())
        assertEquals(
            companies[0].toString(),
            first["items"][0]["conversation"]["companyId"].asString(),
        )
        assertTrue(first["items"][0]["room"].isNull)
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
