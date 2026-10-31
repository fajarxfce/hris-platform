package dev.fajar.hris

import java.net.URI
import java.net.http.*
import java.time.Duration
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["HRIS_COLLABORATION_ENABLED=true"])
class NativeCollaborationStatusHttpTest : NativeCollaborationApiFixture() {
    private fun status(
        n: Native,
        token: String = n.access,
        platform: String = "ANDROID",
    ): HttpResponse<String> =
        n.fixture.browser.send(
            request(n.path, token, platform).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun request(path: String, token: String, platform: String = "ANDROID") =
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("X-HRIS-Client-Platform", platform)
            .header("X-HRIS-Client-Build", "1")

    private fun retry(
        n: Native,
        device: UUID,
        operation: UUID? = UUID.randomUUID(),
        version: Long = 0,
        extra: Map<String, Any> = emptyMap(),
    ): HttpResponse<String> {
        val builder = request(n.path + "/retry", n.access)
        operation?.let { builder.header("Idempotency-Key", it.toString()) }
        val body =
            mapOf(
                "deviceId" to device.toString(),
                "expectedDeviceVersion" to 0,
                "expectedProvisioningVersion" to version,
            ) + extra
        return n.fixture.browser.send(
            builder
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    private fun enroll(n: Native): UUID {
        val response = register(n)
        assertEquals(200, response.statusCode(), response.body())
        return UUID.fromString(json.readTree(response.body())["id"].asString())
    }

    @Test
    fun nativeStatusShowsOnlyItsBoundSetupMetadataAndRequiresLiveAuthority() {
        val n = native()
        val empty = status(n)
        assertEquals(200, empty.statusCode(), empty.body())
        assertTrue(json.readTree(empty.body())["device"].isNull)
        val device = enroll(n)
        val response = status(n)
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
        val body = json.readTree(response.body())["device"]
        assertEquals(device.toString(), body["id"].asString())
        assertEquals("PROVISIONING", body["state"].asString())
        assertEquals("QUEUED", body["provisioning"]["status"].asString())
        assertEquals(
            setOf("id", "version", "state", "platform", "provisioning", "gateway"),
            body.propertyNames().toSet(),
        )
        assertEquals(
            setOf("version", "jobId", "status", "attempts", "failureCode", "retryNotBefore"),
            body["provisioning"].propertyNames().toSet(),
        )
        failure(get(n.fixture.browser, n.path), 403, "invalid_session_transport")
        failure(status(n, token = "invalid"), 401, "native_session_invalid")
        failure(status(n, platform = "IOS"), 422, "invalid_collaboration_device")
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
                n.fixture.company,
                n.fixture.account,
            )
        failure(status(n), 403, "access_denied")
    }

    @Test
    fun failedSetupCanBeRetriedOnceWithObservedVersionsAndLostResponseReplay() {
        val n = native()
        val device = enroll(n)
        failure(retry(n, device), 409, "collaboration_provisioning_changed")
        // This HTTP fixture owns the terminal job seed; real worker claiming is proved separately.
        database()
            .update(
                "update background_jobs set status='FAILED',finished_at=?,failure_code='collaboration_provider_unavailable' where id=(select job_id from collaboration_device_provisioning where device_id=?)",
                clock.instant().atOffset(ZoneOffset.UTC),
                device,
            )
        val stopped = json.readTree(status(n).body())["device"]["provisioning"]
        assertEquals("FAILED", stopped["status"].asString())
        assertFalse(stopped["retryNotBefore"].isNull)
        failure(retry(n, device), 429, "collaboration_provisioning_retry_too_soon")
        clock.set(clock.instant().plusSeconds(61))
        val operation = UUID.randomUUID()
        val first = retry(n, device, operation)
        assertEquals(200, first.statusCode(), first.body())
        assertEquals(first.body(), retry(n, device, operation).body())
        val current = json.readTree(status(n).body())["device"]
        assertEquals(device.toString(), current["id"].asString())
        assertEquals(0, current["version"].asInt())
        assertEquals(1, current["provisioning"]["version"].asInt())
        assertEquals(
            json.readTree(first.body())["id"].asString(),
            current["provisioning"]["jobId"].asString(),
        )
        failure(retry(n, device), 409, "collaboration_provisioning_changed")
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from background_jobs where company_id=? and kind='COLLABORATION_PROVISION'",
                    Int::class.java,
                    n.fixture.company,
                ),
        )
    }

    @Test
    fun retryRejectsMissingIdempotencyForgedParentForeignDeviceAndInvalidVersions() {
        val n = native()
        val device = enroll(n)
        assertEquals(400, retry(n, device, operation = null).statusCode())
        assertEquals(
            400,
            retry(n, device, extra = mapOf("nativeSessionId" to UUID.randomUUID().toString()))
                .statusCode(),
        )
        failure(retry(n, UUID.randomUUID()), 403, "collaboration_access_denied")
        failure(retry(n, device, version = -1), 422, "invalid_collaboration_provisioning_version")
        failure(
            retry(n, device, version = 999999),
            422,
            "invalid_collaboration_provisioning_version",
        )
        database()
            .update(
                "update native_sessions set revoked_at=? where id=?",
                clock.instant().atOffset(ZoneOffset.UTC),
                n.session,
            )
        failure(retry(n, device), 401, "native_session_invalid")
    }
}
