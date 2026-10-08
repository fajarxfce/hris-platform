package dev.fajar.hris

import java.net.http.HttpClient
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(TestClockConfiguration::class)
abstract class PeopleApiFixture : ApiIntegrationTest() {
    @Autowired protected lateinit var clock: MutableTestClock

    @BeforeEach
    fun resetPeopleClock() {
        clock.set(Instant.parse("2026-10-01T00:00:00Z"))
    }

    protected fun company(
        browser: HttpClient,
        csrf: String,
        timezone: String = "Asia/Jakarta",
    ): UUID {
        val result =
            command(
                browser,
                "/api/v1/companies",
                json.writeValueAsString(
                    mapOf(
                        "code" to "P${UUID.randomUUID().toString().take(8)}",
                        "name" to "People fixture",
                        "timezone" to timezone,
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, result.statusCode(), result.body())
        return UUID.fromString(json.readTree(result.body()).get("id").asString())
    }

    protected fun terms(
        from: String = "2026-01-01",
        manager: UUID? = null,
        status: String = "ACTIVE",
        start: String = "2026-01-01",
    ): Map<String, Any?> =
        mapOf(
            "effectiveFrom" to from,
            "startDate" to start,
            "contract" to "PERMANENT",
            "status" to status,
            "managerId" to manager,
        )

    protected fun employee(
        browser: HttpClient,
        csrf: String,
        company: UUID,
        manager: UUID? = null,
        account: UUID? = null,
        start: String = "2026-01-01",
    ): UUID {
        val id = UUID.randomUUID()
        val result =
            command(
                browser,
                "/api/v1/companies/$company/employees",
                json.writeValueAsString(
                    mapOf(
                        "id" to id,
                        "employeeNumber" to "E${id.toString().take(8)}",
                        "person" to
                            mapOf(
                                "id" to UUID.randomUUID(),
                                "legalName" to "Example employee",
                                "nationality" to "ID",
                                "accountId" to account,
                            ),
                        "terms" to terms(start, manager, start = start),
                        "reason" to "Onboarding",
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, result.statusCode(), result.body())
        return id
    }

    protected fun revise(
        browser: HttpClient,
        csrf: String,
        company: UUID,
        id: UUID,
        version: Long,
        terms: Map<String, Any?>,
    ) =
        command(
            browser,
            "/api/v1/companies/$company/employees/$id/revisions",
            json.writeValueAsString(
                mapOf("version" to version, "terms" to terms, "reason" to "Scheduled change")
            ),
            csrf,
            UUID.randomUUID(),
        )

    protected fun cancellation(
        browser: HttpClient,
        csrf: String,
        company: UUID,
        id: UUID,
        version: Long,
        revision: Long,
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            browser,
            "/api/v1/companies/$company/employees/$id/revisions/$revision/cancel",
            json.writeValueAsString(
                mapOf("expectedVersion" to version, "reason" to "Cancelled scheduled change")
            ),
            csrf,
            key,
        )
}
