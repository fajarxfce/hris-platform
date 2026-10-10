package dev.fajar.hris

import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties =
        [
            "server.address=127.0.0.1",
            "hris.security.sign-in-limits=true",
            "hris.security.enforce-mfa=false",
        ],
)
@Import(TestClockConfiguration::class)
class NativeSignInLimitHttpTest : ApiIntegrationTest() {
    @Autowired lateinit var clock: MutableTestClock

    @Test
    fun browserAndNativePasswordAttemptsShareTheSameAccountBudget() {
        clock.set(Instant.parse("2026-10-03T00:00:00Z"))
        val client = client()
        val email = "missing-${UUID.randomUUID()}@example.test"
        val csrf = json.readTree(get(client, "/api/v1/auth/csrf").body())["token"].asString()
        val body =
            json.writeValueAsString(
                mapOf("email" to email, "password" to "wrong", "deviceName" to "Android")
            )
        repeat(5) {
            assertEquals(
                401,
                command(client, "/api/v1/auth/native/login", body, csrf, UUID.randomUUID())
                    .statusCode(),
            )
            assertEquals(
                401,
                post(
                        client,
                        "/api/v1/auth/login",
                        json.writeValueAsString(mapOf("email" to email, "password" to "wrong")),
                        csrf,
                    )
                    .statusCode(),
            )
        }
        val denied = command(client, "/api/v1/auth/native/login", body, csrf, UUID.randomUUID())
        assertEquals(429, denied.statusCode(), denied.body())
        assertEquals("sign_in_rate_limited", json.readTree(denied.body())["code"].asString())
    }
}
