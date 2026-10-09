package dev.fajar.hris

import dev.fajar.hris.core.domain.Actor
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class, AuditProbeConfiguration::class)
abstract class AuditApiFixture : PeopleApiFixture() {
    @Autowired protected lateinit var accountProbe: AccountLockProbe
    @Autowired protected lateinit var auditProbe: AuditProbe

    protected data class Fixture(
        val browser: HttpClient,
        val csrf: String,
        val company: UUID,
        val account: UUID,
        val actor: Actor,
    ) {
        val path
            get() = "/api/v1/companies/$company/audit-events"
    }

    protected fun fixture(): Fixture {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Audit fixture',password_hash from accounts where email='admin@example.test'",
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
                setOf("audit.read"),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = credential,
            ),
        )
    }

    protected fun seed(
        f: Fixture,
        at: Instant = clock.instant().minusSeconds(1),
        company: UUID? = f.company,
        actor: UUID = f.account,
        resource: UUID = UUID.randomUUID(),
        action: String = "fixture.created",
        resourceType: String = "fixture",
        id: UUID = UUID.randomUUID(),
    ): UUID {
        database()
            .update(
                "insert into audit_entries(id,company_id,actor_id,resource_type,resource_id,action,reason,details,correlation_id,created_at) values(?,?,?,?,?,?,'PRIVATE_REASON',?::jsonb,?,?)",
                id,
                company,
                actor,
                resourceType,
                resource,
                action,
                json.writeValueAsString(
                    mapOf("private" to "PRIVATE_PAYLOAD", "content" to "x".repeat(16384))
                ),
                UUID.randomUUID(),
                at.atOffset(ZoneOffset.UTC),
            )
        return id
    }

    protected fun read(f: Fixture, query: Map<String, Any> = emptyMap()): HttpResponse<String> {
        val parameters =
            query.entries.joinToString("&") { (name, value) ->
                "$name=${URLEncoder.encode(value.toString(), StandardCharsets.UTF_8)}"
            }
        return get(f.browser, "${f.path}?$parameters")
    }

    protected fun page(
        f: Fixture,
        query: Map<String, Any> = emptyMap(),
    ): tools.jackson.databind.JsonNode {
        val response = read(f, query)
        assertEquals(200, response.statusCode(), response.body())
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        assertFalse(response.body().contains("PRIVATE_REASON"))
        assertFalse(response.body().contains("PRIVATE_PAYLOAD"))
        return json.readTree(response.body())
    }

    protected fun failure(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode(), response.body())
        assertEquals(code, json.readTree(response.body())["code"].asString())
    }

    @AfterEach
    fun releaseAuditProbes() {
        accountProbe.current.getAndSet(null)?.release?.countDown()
        auditProbe.afterRead.getAndSet(null)?.release?.countDown()
        auditProbe.failure.set(null)
        auditProbe.reads.set(0)
    }
}
