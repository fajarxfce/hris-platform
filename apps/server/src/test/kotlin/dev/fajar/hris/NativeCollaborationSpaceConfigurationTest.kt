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
