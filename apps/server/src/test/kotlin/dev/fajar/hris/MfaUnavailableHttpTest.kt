package dev.fajar.hris

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties =
        [
            "server.address=127.0.0.1",
            "hris.security.sign-in-limits=false",
            "hris.security.enforce-mfa=true",
            "HRIS_IDENTITY_KEYS=",
        ],
)
class MfaUnavailableHttpTest : ApiIntegrationTest() {
    @Test
    fun missingEncryptionConfigurationDoesNotWeakenRequiredMfa() {
        val client = client()
        val csrf = login(client)
        val me = json.readTree(get(client, "/api/v1/me").body())
        assertTrue(me.get("assurance").get("required").asBoolean())
        assertFalse(me.get("assurance").get("setupAvailable").asBoolean())
        val begin = command(client, "/api/v1/auth/mfa/enrollment", "{}", csrf, UUID.randomUUID())
        assertEquals(503, begin.statusCode(), begin.body())
        assertEquals("identity_key_unavailable", json.readTree(begin.body()).get("code").asString())
        assertEquals(
            403,
            command(
                    client,
                    "/api/v1/companies",
                    """{"code":"BLOCKED","name":"Blocked","timezone":"UTC"}""",
                    csrf,
                    UUID.randomUUID(),
                )
                .statusCode(),
        )
        assertEquals(
            0,
            database().queryForObject("select count(*) from mfa_enrollments", Int::class.java),
        )
    }
}
