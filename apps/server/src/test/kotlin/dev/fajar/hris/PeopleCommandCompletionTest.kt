package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.*
import dev.fajar.hris.people.domain.usecases.*
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class PeopleCommandCompletionTest : LifecycleApiFixture() {
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var transfers: EmploymentTransferRepository
    @Autowired private lateinit var profiles: PersonProfileRepository
    @Autowired private lateinit var lifecycle: LifecycleRepository
    @Autowired private lateinit var units: OrganizationRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var accounts: AccountAdministrationRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "create",
                "revise",
                "cancel_revision",
                "profile",
                "template",
                "start",
                "assign",
                "task",
                "complete",
                "binding_mfa",
                "binding_password",
                "task_receipt",
            ]
    )
    fun commandsAndTaskReceiptRetainAssuranceUntilTheirLastAcquisition(mode: String) {
        val browser = client()
        val csrf = login(browser, "admin@example.test")
        val company = company(browser, csrf)
        fun account(): UUID {
            val id = UUID.randomUUID()
            database()
                .update(
                    "insert into accounts(id,email,display_name,password_hash,mfa_secret_encrypted) select ?,?,'Command completion fixture',password_hash,'fixture-enrolled' from accounts where email='admin@example.test'",
                    id,
                    "$id@example.test",
                )
            database()
                .update(
                    "insert into company_memberships(company_id,account_id) values(?,?)",
                    company,
                    id,
                )
            for (permission in PermissionCatalog.companyAdministrator) database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    company,
                    id,
                    permission,
                )
            return id
        }
        val author = account()
        val target = account()
        val employee = employee(browser, csrf, company)
        val person =
            database()
                .queryForObject(
                    "select person_id from employments where company_id=? and id=?",
                    UUID::class.java,
                    company,
                    employee,
                )!!
        val today = LocalDate.parse("2026-10-01")
        val beginning = LocalDate.parse("2026-01-01")
        val employmentTerms =
            EmploymentTerms(
                beginning,
                ContractKind.PERMANENT,
                beginning,
                null,
                EmploymentStatus.ACTIVE,
                null,
                null,
                null,
                null,
                null,
            )
        if (mode == "cancel_revision") {
            val changed = revise(browser, csrf, company, employee, 0, terms("2026-11-01"))
            assertEquals(200, changed.statusCode(), changed.body())
        }
        val templateId =
            if (mode in setOf("start", "assign", "task", "complete", "task_receipt"))
                template(browser, csrf, company)
            else UUID.randomUUID()
        val caseId =
            if (mode in setOf("assign", "task", "complete", "task_receipt"))
                case(
                    browser,
                    csrf,
                    company,
                    employee,
                    templateId,
                    assignees = if (mode == "assign") emptyMap() else mapOf("equipment" to author),
                )
            else UUID.randomUUID()
        if (mode == "complete") {
            val changed = change(browser, csrf, company, caseId, 0)
            assertEquals(200, changed.statusCode(), changed.body())
        }
        val original = clock.instant()
        val security = IdentitySecurityPolicy(enforceMfa = mode != "binding_password")
        val recent = mode.startsWith("binding_")
        val actor =
            Actor(
                author,
                company,
                PermissionCatalog.companyAdministrator,
                if (recent) original.minus(security.recentAuthenticationAge).plusSeconds(1)
                else original,
                UUID.randomUUID(),
                credentialVersion = 0,
                mfaVerifiedAt =
                    original
                        .minus(
                            if (recent) security.recentAuthenticationAge else security.maximumMfaAge
                        )
                        .plusSeconds(1),
            )
        var expire = mode != "task_receipt"
        var journals = 0
        val observedJournal =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord) =
                    journal.record(actor, change).also {
                        journals++
                        if (expire && mode != "task_receipt") clock.set(original.plusSeconds(2))
                    }
            }
        val observedLifecycle =
            object : LifecycleRepository by lifecycle {
                override fun case(companyId: UUID, id: UUID) =
                    lifecycle.case(companyId, id).also {
                        if (expire && mode == "task_receipt") clock.set(original.plusSeconds(2))
                    }
            }
        val key = UUID.randomUUID()
        val createdId = UUID.randomUUID()
        val newProfile = PersonProfile(UUID.randomUUID(), null, "Created fixture", null, "ID", null)
        val definition =
            LifecycleTemplate(
                templateId,
                "NEW_TEMPLATE",
                "Reviewed checklist",
                LifecycleKind.ONBOARDING,
                true,
                0,
                listOf(LifecycleTaskDefinition("equipment", "Review equipment", true, 0)),
            )
        fun execute(selected: Actor): Result<MutationReceipt> =
            when (mode) {
                "create" ->
                    CreateEmployee(
                            people = people,
                            companies = companies,
                            members = members,
                            identities = identities,
                            units = units,
                            operations = operations,
                            journal = observedJournal,
                            transactions = transactions,
                            security = security,
                            clock = clock,
                        )
                        .execute(
                            selected,
                            key,
                            createdId,
                            "CREATED-001",
                            newProfile,
                            employmentTerms,
                            "Reviewed creation",
                        )
                "revise" ->
                    ReviseEmployment(
                            people = people,
                            companies = companies,
                            members = members,
                            identities = identities,
                            units = units,
                            operations = operations,
                            journal = observedJournal,
                            transactions = transactions,
                            transfers = transfers,
                            lifecycle = observedLifecycle,
                            security = security,
                            clock = clock,
                        )
                        .execute(
                            selected,
                            key,
                            employee,
                            0,
                            employmentTerms.copy(effectiveFrom = today),
                            "Reviewed revision",
                        )
                "cancel_revision" ->
                    CancelEmploymentRevision(
                            people = people,
                            companies = companies,
                            members = members,
                            identities = identities,
                            operations = operations,
                            journal = observedJournal,
                            transactions = transactions,
                            security = security,
                            clock = clock,
                        )
                        .execute(selected, key, employee, 1, 1, "Reviewed cancellation")
                "profile" ->
                    SavePersonProfile(
                            profiles = profiles,
                            people = people,
                            companies = companies,
                            members = members,
                            identities = identities,
                            operations = operations,
                            journal = observedJournal,
                            transactions = transactions,
                            security = security,
                            clock = clock,
                        )
                        .execute(
                            selected,
                            key,
                            employee,
                            0,
                            "Amended fixture",
                            null,
                            "ID",
                            null,
                            "Reviewed profile",
                        )
                "template" ->
                    SaveLifecycleTemplate(
                            lifecycle = observedLifecycle,
                            companies = companies,
                            identities = identities,
                            members = members,
                            operations = operations,
                            journal = observedJournal,
                            transactions = transactions,
                            security = security,
                            clock = clock,
                        )
                        .execute(selected, key, definition, null, "Reviewed checklist")
                "start" ->
                    StartLifecycleCase(
                            lifecycle = observedLifecycle,
                            companies = companies,
                            identities = identities,
                            people = people,
                            members = members,
                            operations = operations,
                            journal = observedJournal,
                            transactions = transactions,
                            security = security,
                            clock = clock,
                        )
                        .execute(
                            selected,
                            key,
                            caseId,
                            StartLifecycleCommand(
                                employee,
                                templateId,
                                0,
                                today,
                                mapOf("equipment" to author),
                                "Reviewed initiation",
                            ),
                        )
                "assign" ->
                    AssignLifecycleTask(
                            lifecycle = observedLifecycle,
                            companies = companies,
                            identities = identities,
                            people = people,
                            members = members,
                            operations = operations,
                            journal = observedJournal,
                            transactions = transactions,
                            security = security,
                            clock = clock,
                        )
                        .execute(
                            selected,
                            key,
                            caseId,
                            "equipment",
                            0,
                            author,
                            "Reviewed assignment",
                        )
                "task",
                "task_receipt" ->
                    ChangeLifecycleTask(
                            lifecycle = observedLifecycle,
                            companies = companies,
                            identities = identities,
                            members = members,
                            people = people,
                            operations = operations,
                            journal = observedJournal,
                            transactions = transactions,
                            security = security,
                            clock = clock,
                        )
                        .execute(
                            selected,
                            key,
                            caseId,
                            "equipment",
                            LifecycleTaskChange(0, LifecycleTaskStatus.DONE, "Reviewed task"),
                        )
                "complete" ->
                    CompleteOnboarding(
                            lifecycle = observedLifecycle,
                            companies = companies,
                            identities = identities,
                            members = members,
                            people = people,
                            operations = operations,
                            journal = observedJournal,
                            transactions = transactions,
                            security = security,
                            clock = clock,
                        )
                        .execute(selected, key, caseId, 1, "Reviewed completion")
                else ->
                    BindPersonAccount(
                            profiles = profiles,
                            people = people,
                            companies = companies,
                            identities = identities,
                            accounts = accounts,
                            members = members,
                            operations = operations,
                            journal = observedJournal,
                            transactions = transactions,
                            security = security,
                            clock = clock,
                        )
                        .execute(selected, key, employee, target, 0, "Verified identity")
            }
        fun state() =
            buildMap<String, Any?> {
                for (table in
                    listOf(
                        "audit_entries",
                        "outbox_events",
                        "operation_receipts",
                        "employments",
                        "lifecycle_cases",
                        "lifecycle_templates",
                    )) put(
                    table,
                    database()
                        .queryForObject(
                            "select count(*) from $table where company_id=?",
                            Long::class.java,
                            company,
                        ),
                )
                put(
                    "employeeVersions",
                    database()
                        .queryForList(
                            "select id,version from employments where company_id=? order by id",
                            company,
                        ),
                )
                put(
                    "profile",
                    database()
                        .queryForList(
                            "select id,version,legal_name,account_id from persons where id=?",
                            person,
                        ),
                )
                put(
                    "cases",
                    database()
                        .queryForList(
                            "select id,version,status from lifecycle_cases where company_id=? order by id",
                            company,
                        ),
                )
            }
        try {
            var receipt: MutationReceipt? = null
            if (mode == "task_receipt") {
                val originalResult = execute(actor)
                assertTrue(originalResult is Result.Success, originalResult.toString())
                receipt = (originalResult as Result.Success).value
                expire = true
            }
            val before = state()
            journals = 0
            val denied = execute(actor)
            assertEquals(
                if (mode == "task_receipt") 0 else 1,
                journals,
                "Actual command must reach the intended boundary",
            )
            assertEquals(
                if (recent) "recent_authentication_required" else "mfa_required",
                (denied as? Result.Failed)?.failure?.code,
            )
            assertEquals(before, state())
            expire = false
            val renewed =
                actor.copy(authenticatedAt = clock.instant(), mfaVerifiedAt = clock.instant())
            val completed = execute(renewed)
            assertTrue(completed is Result.Success, completed.toString())
            if (receipt != null)
                assertEquals(receipt.copy(replayed = true), (completed as Result.Success).value)
            val committed = state()
            assertEquals(
                Result.Success((completed as Result.Success).value.copy(replayed = true)),
                execute(renewed),
            )
            assertEquals(committed, state())
        } finally {
            clock.set(original)
        }
    }
}
