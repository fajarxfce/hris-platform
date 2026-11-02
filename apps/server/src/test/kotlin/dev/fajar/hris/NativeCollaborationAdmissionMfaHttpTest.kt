package dev.fajar.hris

import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.net.URI
import java.net.http.*
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["HRIS_COLLABORATION_ENABLED=true"])
class NativeCollaborationAdmissionMfaHttpTest : MfaApiFixture() {
    @Autowired private lateinit var security: IdentitySecurityPolicy

    @Test
    fun expiredMfaStopsCompanyAdmissionButRetainsOwnDeviceDiscovery() {
        assertTrue(security.enforceMfa)
        val f = enroll(fixture()).fixture
        val created =
            command(
                f.client,
                "/api/v1/companies",
                """{"code":"M${UUID.randomUUID().toString().take(8)}","name":"MFA chat fixture","timezone":"UTC"}""",
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, created.statusCode(), created.body())
        val company = UUID.fromString(json.readTree(created.body())["id"].asString())
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat') on conflict do nothing",
                company,
                f.account,
            )
        val exchanged =
            command(
                f.client,
                "/api/v1/auth/native/exchange",
                """{"deviceName":"MFA chat fixture"}""",
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, exchanged.statusCode(), exchanged.body())
        val identity = json.readTree(exchanged.body())
        val bearer = identity["accessToken"].asString()
        val parent = UUID.fromString(identity["sessionId"].asString())
        val root = "/api/v1/companies/$company/collaboration/native-device"
        fun request(path: String, register: Boolean = false): HttpResponse<String> {
            val builder =
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer $bearer")
                    .header("X-HRIS-Client-Platform", "ANDROID")
                    .header("X-HRIS-Client-Build", "1")
            if (register)
                builder
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            """{"platform":"ANDROID","label":"MFA chat fixture"}"""
                        )
                    )
            else builder.GET()
            return f.client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        }
        val registered = request(root, register = true)
        assertEquals(200, registered.statusCode(), registered.body())
        val device = UUID.fromString(json.readTree(registered.body())["id"].asString())
        database()
            .update(
                "update collaboration_devices set state='ACTIVE',version=version+1,updated_at=now() where id=?",
                device,
            )
        val admitted = request("$root/admission")
        assertEquals(200, admitted.statusCode(), admitted.body())
        // Keep current access credentials alive; expire only the previously verified MFA proof.
        database()
            .update(
                "update native_sessions set mfa_verified_at=? where id=?",
                java.sql.Timestamp.from(clock.instant().minus(security.maximumMfaAge)),
                parent,
            )
        val denied = request("$root/admission")
        assertEquals(403, denied.statusCode(), denied.body())
        assertEquals("mfa_required", json.readTree(denied.body())["code"].asString())
        val metadata = request("/api/v1/collaboration/native-devices")
        assertEquals(200, metadata.statusCode(), metadata.body())
        val page = json.readTree(metadata.body())
        assertEquals(f.account.toString(), page["accountId"].asString())
        assertEquals(parent.toString(), page["nativeSessionId"].asString())
        assertEquals(1, page["devices"].size())
        assertEquals(device.toString(), page["devices"][0]["deviceId"].asString())
    }
}
