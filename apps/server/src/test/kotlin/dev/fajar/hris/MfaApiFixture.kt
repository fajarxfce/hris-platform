package dev.fajar.hris

import dev.fajar.hris.identity.data.datasources.PasswordDataSource
import java.net.CookieManager
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.util.encoders.Base32
import org.junit.jupiter.api.Assertions.*
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
            "hris.security.enforce-mfa=true",
        ],
)
@Import(TestClockConfiguration::class, AssuranceProbeConfiguration::class)
abstract class MfaApiFixture : ApiIntegrationTest() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun identityKey(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_IDENTITY_KEYS") {
                "v1:" + Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
            }
        }
    }

    @Autowired protected lateinit var clock: MutableTestClock
    @Autowired private lateinit var passwords: PasswordDataSource

    protected data class Fixture(
        val account: UUID,
        val email: String,
        val client: HttpClient,
        val csrf: String,
    )

    protected data class Enrolled(
        val fixture: Fixture,
        val secret: String,
        val codes: List<String>,
    ) {
        override fun toString(): String = "Enrolled(<redacted>)"
    }

    protected fun fixture(): Fixture {
        clock.set(Instant.parse("2026-10-01T00:00:00Z"))
        val account = UUID.randomUUID()
        val email = "mfa-$account@example.test"
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) values(?,?,'MFA test',?)",
                account,
                email,
                passwords.hash("Testing-password-123!"),
            )
        database()
            .update(
                "insert into platform_permissions(account_id,permission) values(?,'companies.create'),(?,'identity.manage')",
                account,
                account,
            )
        val client = client()
        return Fixture(account, email, client, login(client, email))
    }

    protected fun csrf(client: HttpClient): String =
        json.readTree(get(client, "/api/v1/auth/csrf").body()).get("token").asString()

    protected fun sessionCookie(client: HttpClient): String =
        (client.cookieHandler().orElseThrow() as CookieManager)
            .cookieStore
            .cookies
            .first { it.name == "SESSION" }
            .value

    protected fun begin(f: Fixture, id: UUID): HttpResponse<String> =
        command(f.client, "/api/v1/auth/mfa/enrollment", "{}", f.csrf, id)

    protected fun confirm(f: Fixture, id: UUID, code: String): HttpResponse<String> =
        post(
            f.client,
            "/api/v1/auth/mfa/enrollment/confirm",
            json.writeValueAsString(mapOf("operationId" to id, "code" to code)),
            f.csrf,
        )

    protected fun enroll(f: Fixture): Enrolled {
        val id = UUID.randomUUID()
        val response = begin(f, id)
        assertEquals(200, response.statusCode(), response.body())
        val secret = json.readTree(response.body()).get("secret").asString()
        val activated = confirm(f, id, totp(secret, clock.instant()))
        assertEquals(200, activated.statusCode(), activated.body())
        val array = json.readTree(activated.body()).get("recoveryCodes")
        return Enrolled(
            f.copy(csrf = csrf(f.client)),
            secret,
            (0 until array.size()).map { array[it].asString() },
        )
    }

    protected fun verify(
        f: Fixture,
        code: String,
        recovery: Boolean = false,
    ): HttpResponse<String> =
        post(
            f.client,
            "/api/v1/auth/mfa/verify",
            json.writeValueAsString(mapOf("code" to code, "recovery" to recovery)),
            f.csrf,
        )

    protected fun anotherLogin(f: Fixture): Fixture {
        val client = client()
        return f.copy(client = client, csrf = login(client, f.email))
    }

    protected fun totp(secret: String, at: Instant): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(Base32.decode(secret), "HmacSHA1"))
        val bytes = mac.doFinal(ByteBuffer.allocate(8).putLong(at.epochSecond / 30).array())
        val offset = bytes.last().toInt() and 15
        return ((ByteBuffer.wrap(bytes, offset, 4).int and 0x7fffffff) % 1_000_000)
            .toString()
            .padStart(6, '0')
    }
}
