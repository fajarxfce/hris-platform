package dev.fajar.hris

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CredentialDisabledHttpTest : ApiIntegrationTest() {
    @Test
    fun disabledMailDoesNotCreateUnusableInvitationsOrExposeRecoveryEligibility() {
        val admin = client()
        val csrf = login(admin)
        val id = UUID.randomUUID()
        val response =
            command(
                admin,
                "/api/v1/identity/invitations",
                json.writeValueAsString(
                    mapOf(
                        "id" to id,
                        "email" to "$id@example.test",
                        "displayName" to "Employee",
                        "reason" to "Provisioning",
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(503, response.statusCode(), response.body())
        assertEquals(
            0,
            database()
                .queryForObject("select count(*) from accounts where id=?", Int::class.java, id),
        )
        val browser = client()
        val anonymousCsrf =
            json.readTree(get(browser, "/api/v1/auth/csrf").body()).get("token").asString()
        for (email in listOf("admin@example.test", "missing@example.test")) assertEquals(
            202,
            post(
                    browser,
                    "/api/v1/auth/password-recovery",
                    json.writeValueAsString(mapOf("email" to email)),
                    anonymousCsrf,
                )
                .statusCode(),
        )
        assertEquals(
            0,
            database()
                .queryForObject("select count(*) from identity_mail_deliveries", Int::class.java),
        )
    }
}
