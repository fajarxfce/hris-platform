package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.leave.domain.repositories.LeavePolicyRepository
import dev.fajar.hris.leave.domain.usecases.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.net.http.HttpResponse
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import tools.jackson.databind.JsonNode

@Import(LeavePolicyReadProbeConfiguration::class, AccountLockProbeConfiguration::class)
abstract class LeavePolicyApiFixture : LeaveApiFixture() {
    @Autowired protected lateinit var policies: LeavePolicyRepository
    @Autowired protected lateinit var identities: IdentityRepository
    @Autowired protected lateinit var members: MembershipRepository
    @Autowired protected lateinit var companies: CompanyRepository
    @Autowired protected lateinit var transactions: TransactionRunner
    @Autowired protected lateinit var operations: OperationRepository
    @Autowired protected lateinit var journal: ChangeJournalRepository
    @Autowired protected lateinit var policyReadProbe: LeavePolicyReadProbe
    @Autowired protected lateinit var accountProbe: AccountLockProbe

    @AfterEach
    fun clearPolicyProbes() {
        accountProbe.current.getAndSet(null)?.release?.countDown()
        policyReadProbe.afterFind = null
    }

    protected fun policyPath(f: LeaveFixture) = "/api/v1/companies/${f.company}/leave/policies"

    protected fun reviewBody(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    protected fun policyOperator(f: LeaveFixture, assurance: Boolean = false): Actor {
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.manage')",
                f.company,
                f.account,
            )
        if (assurance)
            database()
                .update(
                    "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                    f.account,
                )
        val security = IdentitySecurityPolicy()
        return Actor(
            f.account,
            f.company,
            setOf("leave.manage"),
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = 0,
            mfaVerifiedAt =
                if (assurance) clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
                else null,
        )
    }

    protected fun policyInvocation(
        f: LeaveFixture,
        actor: Actor,
        operation: String,
        security: IdentitySecurityPolicy,
    ): (Actor) -> Result<*> {
        val current =
            requireNotNull(
                (transactions.run(actor) { policies.find(f.company, f.type) } as Result.Success)
                    .value
            )
        val change =
            current.copy(
                effectiveFrom = LocalDate.of(2027, 1, 1),
                policy = current.policy.copy(name = "Updated policy"),
            )
        val key = UUID.randomUUID()
        return when (operation) {
            "catalog" -> { live ->
                    ListLeavePolicies(policies, identities, members, transactions, clock, security)
                        .execute(live, null, null, 20)
                }
            "review" -> { live ->
                    GetLeavePolicy(policies, identities, members, transactions, clock, security)
                        .execute(live, f.type, null, 20)
                }
            "effective" -> { live ->
                    ListLeaveTypes(policies, transactions, identities, members, clock, security)
                        .execute(live, LocalDate.of(2026, 10, 1), null, 20)
                }
            "save" -> { live ->
                    SaveLeaveType(
                            policies,
                            operations,
                            journal,
                            transactions,
                            companies,
                            members,
                            identities,
                            clock,
                            security,
                        )
                        .execute(live, key, change, 0, "Reviewed future policy")
                }
            else -> error("Unknown owned policy test operation")
        }
    }
}
