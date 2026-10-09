package dev.fajar.hris

import dev.fajar.hris.core.domain.Actor
import java.net.URI
import java.net.http.*
import java.time.Duration
import java.util.Base64
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@Import(ClientPolicyProbeConfiguration::class)
abstract class ClientPolicyApiFixture : PeopleApiFixture() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun keys(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_IDENTITY_KEYS") {
                "v1:" + Base64.getEncoder().encodeToString(ByteArray(32) { 17 })
            }
        }
    }

    @Autowired protected lateinit var policyProbe: ClientPolicyProbe

    protected data class Fixture(
        val browser: HttpClient,
        val csrf: String,
        val company: UUID,
        val account: UUID,
        val actor: Actor,
    ) {
        val root
            get() = "/api/v1/companies/$company"

        val settings
            get() = "$root/settings/client-policy"
    }

    protected fun fixture(): Fixture {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        val current = get(browser, "/api/v1/me")
        assertEquals(200, current.statusCode(), current.body())
        val account = UUID.fromString(json.readTree(current.body())["account"]["id"].asString())
        val credential =
            requireNotNull(
                database()
                    .queryForObject(
                        "select security_version from accounts where id=?",
                        Long::class.java,
                        account,
                    )
            )
        return Fixture(
            browser,
            csrf,
            company,
            account,
            Actor(
                account,
                company,
                setOf("settings.manage", "people.read"),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = credential,
            ),
        )
    }

    protected fun body(version: Long? = null, changes: Map<String, Any?> = emptyMap()) =
        json.writeValueAsString(
            mapOf(
                "expectedVersion" to version,
                "activateAt" to null,
                "disabledModules" to emptyList<String>(),
                "minimumBuilds" to mapOf("android" to 0, "ios" to 0, "web" to 0),
                "maintenance" to null,
                "reason" to "Client policy configuration",
            ) + changes
        )

    protected fun save(
        f: Fixture,
        version: Long? = null,
        changes: Map<String, Any?> = emptyMap(),
        key: UUID = UUID.randomUUID(),
    ) = command(f.browser, f.settings, body(version, changes), f.csrf, key, "PUT")

    protected fun read(
        f: Fixture,
        path: String,
        headers: List<Pair<String, String>> = emptyList(),
    ): HttpResponse<String> {
        val request =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                .timeout(Duration.ofSeconds(15))
                .GET()
        headers.forEach { (name, value) -> request.header(name, value) }
        return f.browser.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    protected fun failure(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode(), response.body())
        assertEquals(code, json.readTree(response.body())["code"].asString())
    }

    protected fun versions(f: Fixture) =
        database()
            .queryForObject(
                "select count(*) from company_client_policy_revisions where company_id=?",
                Int::class.java,
                f.company,
            )

    @AfterEach
    fun releasePolicyProbe() {
        policyProbe.clear()
    }
}
