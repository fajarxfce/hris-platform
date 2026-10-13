package dev.fajar.hris

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.TransactionRunner
import dev.fajar.hris.expenses.domain.usecases.ListExpenseCostCenters
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.organization.domain.repositories.OrganizationRepository
import java.net.http.HttpClient
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
abstract class ExpenseCostCenterApiFixture : ClientPolicyApiFixture() {
    @Autowired protected lateinit var units: OrganizationRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired protected lateinit var accounts: AccountLockProbe
    protected val security = IdentitySecurityPolicy()

    protected data class Member(val id: UUID, val browser: HttpClient, val csrf: String)

    protected fun member(company: UUID, permission: String = "expenses.self.manage"): Member {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Expense references fixture',password_hash from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                id,
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                company,
                id,
                permission,
            )
        val browser = client()
        return Member(id, browser, login(browser, "$id@example.test"))
    }

    protected fun unit(
        f: Fixture,
        code: String,
        name: String = "Candidate $code",
        kind: String = "COST_CENTER",
        active: Boolean = true,
    ): UUID {
        val id = UUID.randomUUID()
        val response =
            command(
                f.browser,
                "${f.root}/organization-units/$id",
                json.writeValueAsString(
                    mapOf("code" to code, "name" to name, "kind" to kind, "active" to active)
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, response.statusCode(), response.body())
        return id
    }

    protected fun actor(f: Fixture, member: Member): Actor {
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                member.id,
            )
        return Actor(
            member.id,
            f.company,
            setOf("expenses.self.manage"),
            clock.instant(),
            UUID.randomUUID(),
            mfaVerifiedAt = clock.instant(),
            credentialVersion = 0,
        )
    }

    protected fun reader(source: OrganizationRepository = units) =
        ListExpenseCostCenters(
            source,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @AfterEach
    fun releaseCostCenterAccountProbe() {
        accounts.current.getAndSet(null)?.release?.countDown()
    }
}
