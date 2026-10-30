package dev.fajar.hris

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.people.domain.usecases.GetEmploymentTransferReview
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class EmploymentTransferReadCompletionTest : PeopleApiFixture() {
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var policies: CompanyClientPolicyRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: CrossCompanyTransactionRunner

    @ParameterizedTest
    @ValueSource(strings = ["last_identity", "employee"])
    fun bothTransferScopesRequireCurrentAssuranceThroughReviewCompletion(boundary: String) {
        val browser = client()
        val csrf = login(browser, "admin@example.test")
        val source = company(browser, csrf)
        val target = company(browser, csrf)
        val employee = employee(browser, csrf, source)
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash,mfa_secret_encrypted) select ?,?,'Transfer read fixture',password_hash,'fixture-enrolled' from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        for (company in listOf(source, target)) {
            database()
                .update(
                    "insert into company_memberships(company_id,account_id) values(?,?)",
                    company,
                    account,
                )
            for (permission in PermissionCatalog.companyAdministrator) database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    company,
                    account,
                    permission,
                )
        }
        val original = clock.instant()
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val actor =
            Actor(
                account,
                source,
                PermissionCatalog.companyAdministrator,
                original,
                UUID.randomUUID(),
                credentialVersion = 0,
                mfaVerifiedAt = original.minus(security.maximumMfaAge).plusSeconds(1),
            )
        var expire = false
        var identityReads = 0
        var employeeReads = 0
        val observedIdentities =
            object : IdentityRepository by identities {
                override fun access(accountId: UUID, companyId: UUID?) =
                    identities.access(accountId, companyId).also {
                        identityReads++
                        if (expire && boundary == "last_identity" && identityReads == 3)
                            clock.set(original.plusSeconds(2))
                    }
            }
        val observedPeople =
            object : PeopleRepository by people {
                override fun find(companyId: UUID, id: UUID, asOf: LocalDate) =
                    people.find(companyId, id, asOf).also {
                        employeeReads++
                        if (expire && boundary == "employee") clock.set(original.plusSeconds(2))
                    }
            }
        val subject =
            GetEmploymentTransferReview(
                observedPeople,
                policies,
                companies,
                members,
                observedIdentities,
                transactions,
                security,
                clock,
            )
        fun counts() =
            listOf("audit_entries", "outbox_events", "operation_receipts").associateWith {
                database()
                    .queryForObject(
                        "select count(*) from $it where company_id in (?,?)",
                        Long::class.java,
                        source,
                        target,
                    )
            }
        try {
            val before = counts()
            val normal = subject.execute(actor, employee, target, ClientRequest(null, false))
            assertTrue(normal is Result.Success, normal.toString())
            assertEquals(3, identityReads)
            assertEquals(1, employeeReads)
            identityReads = 0
            employeeReads = 0
            expire = true
            val denied = subject.execute(actor, employee, target, ClientRequest(null, false))
            assertEquals(3, identityReads)
            assertEquals("mfa_required", (denied as? Result.Failed)?.failure?.code)
            assertTrue(
                (denied as Result.Failed).failure.parameters["companyId"] in
                    setOf(source.toString(), target.toString())
            )
            assertEquals(before, counts())
            val renewed =
                subject.execute(
                    actor.copy(mfaVerifiedAt = clock.instant()),
                    employee,
                    target,
                    ClientRequest(null, false),
                )
            assertTrue(renewed is Result.Success, renewed.toString())
            assertEquals(
                (normal as Result.Success).value.employee,
                (renewed as Result.Success).value.employee,
            )
            assertEquals(normal.value.targetPermissions, renewed.value.targetPermissions)
            assertEquals(before, counts())
        } finally {
            clock.set(original)
        }
    }
}
