package dev.fajar.hris

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
abstract class GroupHeadcountApiFixture : HeadcountReportApiFixture() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun keys(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_IDENTITY_KEYS") {
                "v1:" + Base64.getEncoder().encodeToString(ByteArray(32) { 23 })
            }
        }
    }

    @Autowired protected lateinit var policyProbe: ClientPolicyProbe

    protected fun anotherCompany(f: Fixture): Fixture {
        val company = company(f.browser, f.csrf)
        return f.copy(company = company, actor = f.actor.copy(companyId = company))
    }

    protected fun groupPath(companies: List<UUID>, date: String = "2026-10-01") =
        "/api/v1/reports/headcount?companies=${companies.joinToString(",")}&asOf=$date"

    protected fun readGroup(
        f: Fixture,
        companies: List<UUID>,
        headers: List<Pair<String, String>> = emptyList(),
        date: String = "2026-10-01",
        extra: String = "",
    ): HttpResponse<String> {
        val request =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port${groupPath(companies, date)}$extra"))
                .timeout(Duration.ofSeconds(15))
                .GET()
        headers.forEach { (name, value) -> request.header(name, value) }
        return f.browser.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    protected fun group(
        f: Fixture,
        companies: List<UUID>,
        date: String = "2026-10-01",
    ): tools.jackson.databind.JsonNode {
        val response = readGroup(f, companies, date = date)
        assertEquals(200, response.statusCode(), response.body())
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        return json.readTree(response.body())
    }

    protected fun savePolicy(
        f: Fixture,
        version: Long? = null,
        changes: Map<String, Any?>,
    ): HttpResponse<String> =
        command(
            f.browser,
            "/api/v1/companies/${f.company}/settings/client-policy",
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to version,
                    "activateAt" to null,
                    "disabledModules" to emptyList<String>(),
                    "minimumBuilds" to mapOf("android" to 0, "ios" to 0, "web" to 0),
                    "maintenance" to null,
                    "reason" to "Group reporting fixture",
                ) + changes
            ),
            f.csrf,
            UUID.randomUUID(),
            "PUT",
        )

    @AfterEach fun releaseGroupProbe() = policyProbe.clear()
}
