package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.identity.domain.usecases.*
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import tools.jackson.databind.JsonNode

@Import(
    RoleTemplateReadProbeConfiguration::class,
    AccountLockProbeConfiguration::class,
    TestClockConfiguration::class,
)
abstract class RoleTemplateApiFixture : ApiIntegrationTest() {
    @Autowired protected lateinit var roles: RoleTemplateRepository
    @Autowired protected lateinit var identities: IdentityRepository
    @Autowired protected lateinit var members: MembershipRepository
    @Autowired protected lateinit var transactions: TransactionRunner
    @Autowired protected lateinit var operations: OperationRepository
    @Autowired protected lateinit var journal: ChangeJournalRepository
    @Autowired protected lateinit var roleProbe: RoleTemplateReadProbe
    @Autowired protected lateinit var accountProbe: AccountLockProbe
    @Autowired protected lateinit var clock: MutableTestClock

    protected data class RoleFixture(
        val browser: HttpClient,
        val csrf: String,
        val company: UUID,
        val id: UUID,
        val operator: UUID,
    ) {
        val path
            get() = "/api/v1/companies/$company/role-templates"
    }

    @AfterEach
    fun resetRoleProbes() {
        roleProbe.beforeLock = null
        roleProbe.afterFind = null
        roleProbe.afterHistory = null
        roleProbe.afterList = null
        accountProbe.current.getAndSet(null)?.release?.countDown()
    }

    protected fun roleFixture(): RoleFixture {
        clock.set(Instant.parse("2026-10-01T00:00:00Z"))
        val browser = client()
        val csrf = login(browser)
        val company =
            UUID.fromString(
                body(
                        command(
                            browser,
                            "/api/v1/companies",
                            json.writeValueAsString(
                                mapOf(
                                    "code" to "R${UUID.randomUUID().toString().take(8)}",
                                    "name" to "Role review fixture",
                                    "timezone" to "UTC",
                                )
                            ),
                            csrf,
                            UUID.randomUUID(),
                        )
                    )["id"]
                    .asString()
            )
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Role operator',password_hash from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        body(
            command(
                browser,
                "/api/v1/companies/$company/members/$account",
                json.writeValueAsString(
                    mapOf(
                        "permissions" to setOf("company.read", "identity.manage"),
                        "reason" to "Fixture role administration",
                    )
                ),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        val fixture = RoleFixture(browser, csrf, company, UUID.randomUUID(), account)
        body(save(fixture))
        return fixture
    }

    protected fun save(
        f: RoleFixture,
        version: Long? = null,
        active: Boolean = true,
    ): HttpResponse<String> =
        command(
            f.browser,
            "${f.path}/${f.id}",
            json.writeValueAsString(
                mapOf(
                    "code" to "REVIEWER",
                    "name" to "Operations reviewer",
                    "permissions" to setOf("company.read"),
                    "expectedVersion" to version,
                    "active" to active,
                    "reason" to "Reviewed role definition",
                )
            ),
            f.csrf,
            UUID.randomUUID(),
            "PUT",
        )

    protected fun body(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    protected fun actor(f: RoleFixture, assurance: Boolean = false): Actor {
        if (assurance)
            database()
                .update(
                    "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                    f.operator,
                )
        return Actor(
            f.operator,
            f.company,
            setOf("company.read", "identity.manage"),
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = 0,
            mfaVerifiedAt = if (assurance) clock.instant() else null,
        )
    }

    protected fun review(
        f: RoleFixture,
        actor: Actor,
        security: IdentitySecurityPolicy = IdentitySecurityPolicy(enforceMfa = false),
    ) =
        GetRoleTemplate(roles, identities, members, transactions, security, clock)
            .execute(actor, f.id, null, 25)

    protected fun saving(
        changes: ChangeJournalRepository = journal,
        security: IdentitySecurityPolicy = IdentitySecurityPolicy(enforceMfa = false),
    ) =
        SaveRoleTemplate(
            roles,
            operations,
            changes,
            identities,
            members,
            transactions,
            security,
            clock,
        )

    protected fun assertUnchanged(f: RoleFixture, key: UUID) {
        assertEquals(
            0L,
            database()
                .queryForObject(
                    "select version from company_role_templates where company_id=? and id=?",
                    Long::class.java,
                    f.company,
                    f.id,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from role_template_revisions where company_id=? and role_id=?",
                    Int::class.java,
                    f.company,
                    f.id,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where operation_id=?",
                    Int::class.java,
                    key,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where company_id=? and resource_id=? and action='identity.role_template_saved'",
                    Int::class.java,
                    f.company,
                    f.id,
                ),
        )
    }
}
