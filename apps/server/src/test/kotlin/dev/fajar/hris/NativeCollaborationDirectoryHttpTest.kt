package dev.fajar.hris

import java.net.URI
import java.net.http.*
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["HRIS_COLLABORATION_ENABLED=true"])
class NativeCollaborationDirectoryHttpTest : NativeCollaborationApiFixture() {
    private val path = "/api/v1/collaboration/native-devices"

    private fun directory(n: Native, query: String = "", token: String = n.access) =
        n.fixture.browser.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path$query"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer $token")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun admission(n: Native) =
        n.fixture.browser.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port${n.path}/admission"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer ${n.access}")
                .header("X-HRIS-Client-Platform", "ANDROID")
                .header("X-HRIS-Client-Build", "1")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun companyAdmissionRequiresAnActiveOwnedDeviceAndNativeTransport() {
        val n = native()
        val registered = register(n)
        assertEquals(200, registered.statusCode(), registered.body())
        val device = json.readTree(registered.body())["id"].asString()
        failure(admission(n), 403, "collaboration_access_denied")
        database()
            .update(
                "update collaboration_devices set state='ACTIVE',version=version+1,updated_at=now() where id=cast(? as uuid)",
                device,
            )
        val response = admission(n)
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
        val body = json.readTree(response.body())
        assertEquals(
            setOf(
                "companyId",
                "accountId",
                "deviceId",
                "deviceVersion",
                "nativeSessionId",
                "nativeSessionVersion",
                "credentialVersion",
                "admittedAt",
                "expiresAt",
            ),
            body.propertyNames().toSet(),
        )
        assertEquals(n.fixture.company.toString(), body["companyId"].asString())
        assertEquals(n.fixture.account.toString(), body["accountId"].asString())
        assertEquals(n.session.toString(), body["nativeSessionId"].asString())
        assertEquals(device, body["deviceId"].asString())
        assertEquals(1, body["deviceVersion"].asInt())
        val admittedAt = Instant.parse(body["admittedAt"].asString())
        val expiresAt = Instant.parse(body["expiresAt"].asString())
        assertTrue(expiresAt > admittedAt && expiresAt <= admittedAt.plusSeconds(60))
        failure(get(n.fixture.browser, n.path + "/admission"), 403, "invalid_session_transport")
    }

    @Test
    fun onlyOwnParentReferencesAreExposedWithoutCompanySelectionOrCredentials() {
        val n = native()
        val empty = directory(n)
        assertEquals(200, empty.statusCode(), empty.body())
        assertTrue(json.readTree(empty.body())["devices"].isEmpty)
        val registered = register(n)
        assertEquals(200, registered.statusCode(), registered.body())
        val device = json.readTree(registered.body())["id"].asString()
        val other = native()
        val response =
            directory(n, "?accountId=${other.fixture.account}&nativeSessionId=${other.session}")
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
        val body = json.readTree(response.body())
        assertEquals(
            setOf("accountId", "nativeSessionId", "devices", "nextAfterCompanyId"),
            body.propertyNames().toSet(),
        )
        assertEquals(n.fixture.account.toString(), body["accountId"].asString())
        assertEquals(n.session.toString(), body["nativeSessionId"].asString())
        assertTrue(body["nextAfterCompanyId"].isNull)
        assertEquals(1, body["devices"].size())
        val reference = body["devices"][0]
        assertEquals(
            setOf("companyId", "deviceId", "version", "state", "platform"),
            reference.propertyNames().toSet(),
        )
        assertEquals(n.fixture.company.toString(), reference["companyId"].asString())
        assertEquals(device, reference["deviceId"].asString())
        assertEquals("PROVISIONING", reference["state"].asString())
        assertEquals("ANDROID", reference["platform"].asString())
        assertTrue(json.readTree(directory(other).body())["devices"].isEmpty)
        assertTrue(
            json
                .readTree(directory(n, "?afterCompanyId=${n.fixture.company}").body())["devices"]
                .isEmpty
        )
    }

    @Test
    fun ownRecoveryMetadataDoesNotGrantCompanyAccessAfterPermissionLoss() {
        val n = native()
        assertEquals(200, register(n).statusCode())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
                n.fixture.company,
                n.fixture.account,
            )
        val metadata = directory(n)
        assertEquals(200, metadata.statusCode(), metadata.body())
        assertEquals(1, json.readTree(metadata.body())["devices"].size())
        val protected =
            n.fixture.browser.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port${n.path}"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer ${n.access}")
                    .header("X-HRIS-Client-Platform", "ANDROID")
                    .header("X-HRIS-Client-Build", "1")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        failure(protected, 403, "access_denied")
    }

    @Test
    fun nativeTransportCurrentSessionAndBoundedQueryAreRequired() {
        val n = native()
        failure(get(n.fixture.browser, path), 403, "invalid_session_transport")
        failure(directory(n, token = "invalid"), 401, "native_session_invalid")
        for (limit in listOf(-1, 0, 33, Int.MAX_VALUE)) failure(
            directory(n, "?limit=$limit"),
            422,
            "invalid_collaboration_page",
        )
        assertEquals(400, directory(n, "?afterCompanyId=not-a-uuid").statusCode())
        database()
            .update(
                "update native_sessions set revoked_at=now(),version=version+1 where id=?",
                n.session,
            )
        failure(directory(n), 401, "native_session_invalid")
    }
}
