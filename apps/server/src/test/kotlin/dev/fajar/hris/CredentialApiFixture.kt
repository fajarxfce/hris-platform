package dev.fajar.hris

import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource
import java.net.http.HttpClient
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties =
        [
            "server.address=127.0.0.1",
            "hris.security.sign-in-limits=false",
            "hris.security.enforce-mfa=false",
            "HRIS_MAIL_ENABLED=true",
            "HRIS_MAIL_HOST=127.0.0.1",
            "HRIS_MAIL_PORT=1",
            "HRIS_MAIL_FROM=no-reply@example.test",
            "HRIS_MAIL_TRANSPORT=PLAINTEXT",
            "HRIS_MAIL_ALLOW_LOOPBACK_PLAINTEXT=true",
            "HRIS_PUBLIC_URL=https://hris.example.test",
        ],
)
@Import(TestClockConfiguration::class)
abstract class CredentialApiFixture : ApiIntegrationTest() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun identityKey(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_IDENTITY_KEYS") {
                "v1:" + Base64.getEncoder().encodeToString(ByteArray(32) { 17 })
            }
        }
    }

    @Autowired protected lateinit var tokens: IdentityTokenDataSource
    @Autowired protected lateinit var clock: MutableTestClock

    @BeforeEach
    fun resetClockAndOriginBudget() {
        clock.set(Instant.parse("2026-10-01T00:00:00Z"))
        database().execute("delete from authentication_attempts")
    }

    protected fun csrf(browser: HttpClient) =
        json.readTree(get(browser, "/api/v1/auth/csrf").body()).get("token").asString()

    protected fun invite(
        browser: HttpClient,
        csrf: String,
        id: UUID,
        key: UUID = UUID.randomUUID(),
        version: Long? = null,
        email: String = "$id@example.test",
    ) =
        command(
            browser,
            "/api/v1/identity/invitations",
            json.writeValueAsString(
                mapOf(
                    "id" to id,
                    "email" to email,
                    "displayName" to "Invited employee",
                    "reason" to "Account provisioning",
                    "expectedVersion" to version,
                )
            ),
            csrf,
            key,
        )

    protected fun link(id: UUID): Pair<UUID, String> {
        val row =
            database()
                .queryForMap(
                    "select challenge_id,token_encrypted from identity_mail_deliveries where account_id=? and state='PENDING'",
                    id,
                )
        val challenge = row["challenge_id"] as UUID
        return challenge to
            tokens.decrypt("credential-mail:$id:$challenge", row["token_encrypted"] as String)
    }

    protected fun confirm(
        browser: HttpClient,
        csrf: String,
        token: String,
        recovery: Boolean = false,
        password: String = "Replacement-password-123!",
    ) =
        post(
            browser,
            if (recovery) "/api/v1/auth/password-recovery/confirm"
            else "/api/v1/auth/invitations/accept",
            json.writeValueAsString(mapOf("token" to token, "password" to password)),
            csrf,
        )

    protected fun recover(browser: HttpClient, csrf: String, email: String) =
        post(
            browser,
            "/api/v1/auth/password-recovery",
            json.writeValueAsString(mapOf("email" to email)),
            csrf,
        )

    protected fun localAccount(active: Boolean = true, password: Boolean = true): UUID {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,active,password_hash) select ?,?,'Recovery fixture',?,case when ? then password_hash else null end from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
                active,
                password,
            )
        return id
    }
}
