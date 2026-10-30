package dev.fajar.hris

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.*
import dev.fajar.hris.people.domain.usecases.TransferEmployee
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired

class EmploymentTransferCommandCompletionTest : PeopleApiFixture() {
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var lifecycle: LifecycleRepository
    @Autowired private lateinit var transfers: EmploymentTransferRepository
    @Autowired private lateinit var policies: CompanyClientPolicyRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var units: OrganizationRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var roles: RoleTemplateRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: CrossCompanyTransactionRunner

    @ParameterizedTest
    @CsvSource(
        "mfa,1",
        "mfa,2",
        "password,1",
        "password,2",
        "midnight,1",
        "midnight,2",
        "receipt_mfa,0",
        "receipt_password,0",
        "receipt_date,0",
    )
    fun transferProofAndImmediateDatesRemainValidThroughTheOriginalCompletion(
        mode: String,
        boundary: Int,
    ) {
        val priorClock = clock.instant()
        val dateCase = mode in setOf("midnight", "receipt_date")
        if (dateCase) clock.set(Instant.parse("2026-10-01T16:59:59Z"))
        val browser = client()
        val csrf = login(browser, "admin@example.test")
        val source = company(browser, csrf)
        val target = company(browser, csrf)
        val employee = employee(browser, csrf, source)
        val author = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash,mfa_secret_encrypted) select ?,?,'Transfer completion fixture',password_hash,'fixture-enrolled' from accounts where email='admin@example.test'",
                author,
                "$author@example.test",
            )
        for (company in listOf(source, target)) {
            database()
                .update(
                    "insert into company_memberships(company_id,account_id) values(?,?)",
                    company,
                    author,
                )
            for (permission in PermissionCatalog.companyAdministrator) database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    company,
                    author,
                    permission,
                )
        }
        val original = clock.instant()
        val security = IdentitySecurityPolicy(enforceMfa = !mode.contains("password"))
        val proof =
            if (dateCase) original
            else original.minus(security.recentAuthenticationAge).plusSeconds(1)
        val actor =
            Actor(
                author,
                source,
                PermissionCatalog.companyAdministrator,
                proof,
                UUID.randomUUID(),
                mfaVerifiedAt = proof,
                credentialVersion = 0,
            )
        var expire = false
        var journals = 0
        var policyReads = 0
        val observedJournal =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord) =
                    journal.record(actor, change).also {
                        journals++
                        if (expire && journals == boundary) clock.set(original.plusSeconds(2))
                    }
            }
        val observedPolicies =
            object : CompanyClientPolicyRepository by policies {
                override fun effective(companyId: UUID, at: Instant) =
                    policies.effective(companyId, at).also {
                        policyReads++
                        if (expire && mode.startsWith("receipt_") && policyReads == 2)
                            clock.set(original.plusSeconds(2))
                    }
            }
        val subject =
            TransferEmployee(
                people,
                lifecycle,
                transfers,
                observedPolicies,
                companies,
                units,
                identities,
                members,
                roles,
                operations,
                observedJournal,
                transactions,
                security,
                clock,
            )
        val today = LocalDate.ofInstant(original, ZoneId.of("Asia/Jakarta"))
        val terms =
            EmploymentTerms(
                today,
                ContractKind.PERMANENT,
                today,
                null,
                EmploymentStatus.ACTIVE,
                null,
                null,
                null,
                null,
                null,
            )
        val command =
            EmploymentTransferCommand(
                target,
                UUID.randomUUID(),
                0,
                "TRANSFER-001",
                terms,
                "Reviewed transfer",
            )
        val key = UUID.randomUUID()
        fun invoke(
            selected: Actor,
            selectedKey: UUID = key,
            input: EmploymentTransferCommand = command,
        ) = subject.execute(selected, selectedKey, employee, input, ClientRequest(null, false))
        fun state() =
            buildMap<String, Any?> {
                for (table in
                    listOf(
                        "audit_entries",
                        "outbox_events",
                        "operation_receipts",
                        "employments",
                    )) put(
                    table,
                    database()
                        .queryForObject(
                            "select count(*) from $table where company_id in (?,?)",
                            Long::class.java,
                            source,
                            target,
                        ),
                )
                put(
                    "employmentVersions",
                    database()
                        .queryForList(
                            "select id,version from employments where company_id in (?,?) order by id",
                            source,
                            target,
                        ),
                )
                put(
                    "transfers",
                    database()
                        .queryForList(
                            "select id,source_version from employment_transfers where source_company_id=? order by id",
                            source,
                        ),
                )
            }
        try {
            val receiptMode = mode.startsWith("receipt_")
            var receipt: MutationReceipt? = null
            if (receiptMode) {
                val first = invoke(actor)
                assertTrue(first is Result.Success, first.toString())
                receipt = (first as Result.Success).value
            }
            val before = state()
            journals = 0
            policyReads = 0
            expire = true
            val result = invoke(actor)
            assertEquals(if (receiptMode) 0 else 2, journals)
            if (mode == "receipt_date") {
                assertEquals(Result.Success(receipt!!.copy(replayed = true)), result)
                assertEquals(before, state())
                return
            }
            assertEquals(
                if (mode == "midnight") "transfer_requires_current_company_date"
                else "recent_authentication_required",
                (result as? Result.Failed)?.failure?.code,
            )
            assertEquals(before, state())
            expire = false
            val renewed =
                actor.copy(authenticatedAt = clock.instant(), mfaVerifiedAt = clock.instant())
            val newDate = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Jakarta"))
            val reviewed =
                if (mode == "midnight")
                    command.copy(terms = terms.copy(effectiveFrom = newDate, startDate = newDate))
                else command
            val nextKey = if (mode == "midnight") UUID.randomUUID() else key
            val completed = invoke(renewed, nextKey, reviewed)
            assertTrue(completed is Result.Success, completed.toString())
            val committed = state()
            assertEquals(
                Result.Success((completed as Result.Success).value.copy(replayed = true)),
                invoke(renewed, nextKey, reviewed),
            )
            assertEquals(committed, state())
        } finally {
            clock.set(priorClock)
        }
    }
}
