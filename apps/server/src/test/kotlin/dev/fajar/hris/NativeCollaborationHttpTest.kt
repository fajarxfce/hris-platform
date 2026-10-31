package dev.fajar.hris

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["HRIS_COLLABORATION_ENABLED=true"])
class NativeCollaborationHttpTest : NativeCollaborationApiFixture() {
    @Test
    fun existingMembershipNeedsAnExplicitChatGrant() {
        val n = native(grant = false)
        failure(register(n), 403, "access_denied")
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_devices where company_id=?",
                    Int::class.java,
                    n.fixture.company,
                ),
        )
    }

    @Test
    fun registrationBindsAuthenticatedParentAndReplaysWithoutCookieOrDuplicateWork() {
        val n = native()
        val operation = UUID.randomUUID()
        val first = register(n, operation)
        assertEquals(200, first.statusCode(), first.body())
        assertTrue(first.headers().firstValue("Set-Cookie").isEmpty)
        assertEquals("no-store", first.headers().firstValue("Cache-Control").orElse(""))
        assertEquals(first.body(), register(n, operation).body())
        val id = UUID.fromString(json.readTree(first.body())["id"].asString())
        val row =
            database()
                .queryForMap(
                    "select b.native_session_id,b.account_id,b.company_id,d.state from collaboration_device_sessions b join collaboration_devices d on d.company_id=b.company_id and d.id=b.device_id where b.device_id=?",
                    id,
                )
        assertEquals(n.session, row["native_session_id"])
        assertEquals(n.fixture.account, row["account_id"])
        assertEquals(n.fixture.company, row["company_id"])
        assertEquals("PROVISIONING", row["state"])
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from outbox_events where resource_id=?",
                    Int::class.java,
                    id,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=?",
                    Int::class.java,
                    id,
                ),
        )
    }

    @Test
    fun cookiesAndMalformedBearerNeverSubstituteForNativeAuthority() {
        val n = native()
        failure(
            command(
                n.fixture.browser,
                n.path,
                """{"platform":"ANDROID","label":"Cookie"}""",
                n.fixture.csrf,
                UUID.randomUUID(),
            ),
            403,
            "invalid_session_transport",
        )
        failure(register(n, token = "invalid"), 401, "native_session_invalid")
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_devices where company_id=?",
                    Int::class.java,
                    n.fixture.company,
                ),
        )
    }

    @Test
    fun missingIdempotencyAndForgedParentFieldsAreRejected() {
        val n = native()
        assertEquals(400, register(n, operation = null).statusCode())
        assertEquals(
            400,
            register(
                    n,
                    body =
                        mapOf(
                            "platform" to "ANDROID",
                            "label" to "Forged",
                            "sessionId" to UUID.randomUUID().toString(),
                        ),
                )
                .statusCode(),
        )
        failure(
            register(n, body = mapOf("platform" to "ANDROID", "label" to "x".repeat(65))),
            422,
            "invalid_collaboration_device",
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_devices where company_id=?",
                    Int::class.java,
                    n.fixture.company,
                ),
        )
    }

    @Test
    fun revokedPermissionAndRevokedParentDenyARecordedReceipt() {
        val n = native()
        val operation = UUID.randomUUID()
        assertEquals(200, register(n, operation).statusCode())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
                n.fixture.company,
                n.fixture.account,
            )
        failure(register(n, operation), 403, "access_denied")
        database().update("update native_sessions set revoked_at=now() where id=?", n.session)
        failure(register(n, operation), 401, "native_session_invalid")
    }

    @Test
    fun independentModulePolicyAndNativeBuildHeadersApplyToRegistration() {
        val n = native()
        assertEquals(
            200,
            save(n.fixture, changes = mapOf("disabledModules" to listOf("COMMUNICATIONS")))
                .statusCode(),
        )
        assertEquals(200, register(n).statusCode())
        assertEquals(
            200,
            save(n.fixture, 0, mapOf("disabledModules" to listOf("COLLABORATION"))).statusCode(),
        )
        failure(register(n), 403, "company_module_disabled")
        assertEquals(
            200,
            save(
                    n.fixture,
                    1,
                    mapOf("minimumBuilds" to mapOf("android" to 2, "ios" to 0, "web" to 0)),
                )
                .statusCode(),
        )
        failure(register(n), 403, "client_update_required")
        failure(register(n, platform = "WEB", build = "100"), 422, "invalid_client_version")
        assertEquals(200, register(n, build = "2").statusCode())
    }
}
