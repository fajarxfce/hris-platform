package dev.fajar.hris

import dev.fajar.hris.core.domain.Actor
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class, HeadcountReportProbeConfiguration::class)
abstract class HeadcountReportApiFixture : PeopleApiFixture() {
    @Autowired protected lateinit var accountProbe: AccountLockProbe
    @Autowired protected lateinit var reportProbe: HeadcountReportProbe

    protected data class Fixture(
        val browser: HttpClient,
        val csrf: String,
        val company: UUID,
        val account: UUID,
        val actor: Actor,
    ) {
        val path
            get() = "/api/v1/companies/$company/reports/headcount"
    }

    protected fun fixture(): Fixture {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Report fixture',password_hash from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        database()
            .update(
                "insert into platform_permissions(account_id,permission) values(?,'companies.create')",
                id,
            )
        val browser = client()
        val csrf = login(browser, "$id@example.test")
        val company = company(browser, csrf)
        val credential =
            requireNotNull(
                database()
                    .queryForObject(
                        "select security_version from accounts where id=?",
                        Long::class.java,
                        id,
                    )
            )
        return Fixture(
            browser,
            csrf,
            company,
            id,
            Actor(
                id,
                company,
                setOf("reports.read", "people.read"),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = credential,
            ),
        )
    }

    protected fun create(f: Fixture, changes: Map<String, Any?> = emptyMap()): UUID {
        val id = UUID.randomUUID()
        val response =
            command(
                f.browser,
                "/api/v1/companies/${f.company}/employees",
                json.writeValueAsString(
                    mapOf(
                        "id" to id,
                        "employeeNumber" to "R${id.toString().take(8)}",
                        "person" to
                            mapOf(
                                "id" to UUID.randomUUID(),
                                "legalName" to "Report employee",
                                "nationality" to "ID",
                            ),
                        "terms" to terms() + changes,
                        "reason" to "Report fixture",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, response.statusCode(), response.body())
        return id
    }

    protected fun report(f: Fixture, date: String = "2026-10-01"): tools.jackson.databind.JsonNode {
        val response = get(f.browser, "${f.path}?asOf=$date")
        assertEquals(200, response.statusCode(), response.body())
        assertTrue(
            response
                .headers()
                .firstValue("Cache-Control")
                .orElse("")
                .split(',')
                .map(String::trim)
                .contains("no-store")
        )
        return json.readTree(response.body())
    }

    protected fun failure(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode(), response.body())
        assertEquals(code, json.readTree(response.body())["code"].asString())
    }

    @AfterEach
    fun clearReportProbes() {
        accountProbe.current.getAndSet(null)?.release?.countDown()
        reportProbe.afterRead.getAndSet(null)?.release?.countDown()
        reportProbe.failure.set(null)
    }
}
