package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.*
import dev.fajar.hris.people.domain.usecases.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class PeopleReadCompletionTest : LifecycleApiFixture() {
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var profiles: PersonProfileRepository
    @Autowired private lateinit var transfers: EmploymentTransferRepository
    @Autowired private lateinit var directory: CompanyDirectoryRepository
    @Autowired private lateinit var lifecycle: LifecycleRepository
    @Autowired private lateinit var units: OrganizationRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "employee",
                "self_employee",
                "employees",
                "details",
                "self_details",
                "history",
                "revision",
                "transfers",
                "self_employments",
                "profile",
                "self_profile",
                "profile_history",
                "directory",
                "departments",
                "case",
                "cases",
                "case_history",
                "template",
                "templates",
                "assigned",
                "assignees",
                "offboarding",
            ]
    )
    fun privateAndDirectoryReadsRequireCurrentAssuranceAfterAcquisition(mode: String) {
        val browser = client()
        val csrf = login(browser, "admin@example.test")
        val company = company(browser, csrf)
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash,mfa_secret_encrypted) select ?,?,'Read completion fixture',password_hash,'fixture-enrolled' from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
        for (permission in (PermissionCatalog.companyAdministrator + "people.self.read")) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                company,
                account,
                permission,
            )
        val manager = employee(browser, csrf, company)
        val employee = employee(browser, csrf, company, manager = manager, account = account)
        val lifecycleMode =
            mode in
                setOf(
                    "case",
                    "cases",
                    "case_history",
                    "template",
                    "templates",
                    "assigned",
                    "assignees",
                    "offboarding",
                )
        var template = UUID.randomUUID()
        var case = UUID.randomUUID()
        if (lifecycleMode) {
            template =
                template(
                    browser,
                    csrf,
                    company,
                    kind = if (mode == "offboarding") "OFFBOARDING" else "ONBOARDING",
                )
            case =
                case(
                    browser,
                    csrf,
                    company,
                    employee,
                    template,
                    assignees = mapOf("equipment" to account),
                )
        }
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val original = clock.instant()
        val permissions =
            if (mode in setOf("self_employee", "self_details", "self_profile"))
                setOf("company.read", "people.self.read")
            else PermissionCatalog.companyAdministrator
        val actor =
            Actor(
                account,
                company,
                permissions,
                original,
                UUID.randomUUID(),
                credentialVersion = 0,
                mfaVerifiedAt = original.minus(security.maximumMfaAge).plusSeconds(1),
            )
        var expire = false
        var acquired = 0
        fun <T> acquired(value: Result<T>): Result<T> {
            assertTrue(value is Result.Success, value.toString())
            acquired++
            if (expire) clock.set(original.plusSeconds(2))
            return value
        }
        val observedPeople =
            object : PeopleRepository by people {
                override fun find(companyId: UUID, id: UUID, asOf: LocalDate) =
                    people.find(companyId, id, asOf).let {
                        if (mode in setOf("details", "self_details") && id == manager) acquired(it)
                        else it
                    }

                override fun findAtInstant(companyId: UUID, id: UUID, at: Instant) =
                    people.findAtInstant(companyId, id, at).let {
                        if (mode in setOf("employee", "self_employee")) acquired(it) else it
                    }

                override fun history(companyId: UUID, id: UUID, after: Long?, limit: Int) =
                    acquired(people.history(companyId, id, after, limit))

                override fun list(
                    companyId: UUID,
                    accountId: UUID,
                    visibility: EmployeeVisibility,
                    asOf: LocalDate,
                    accessAt: Instant,
                    query: String,
                    after: String?,
                    limit: Int,
                ) =
                    acquired(
                        people.list(
                            companyId,
                            accountId,
                            visibility,
                            asOf,
                            accessAt,
                            query,
                            after,
                            limit,
                        )
                    )
            }
        val observedCompanies =
            object : CompanyRepository by companies {
                override fun find(id: UUID) =
                    companies.find(id).let {
                        if (mode in setOf("revision", "offboarding")) acquired(it) else it
                    }
            }
        val observedProfiles =
            object : PersonProfileRepository by profiles {
                override fun findForEmployee(companyId: UUID, employeeId: UUID) =
                    profiles.findForEmployee(companyId, employeeId).let {
                        if (mode in setOf("profile", "self_profile")) acquired(it) else it
                    }

                override fun history(personId: UUID, after: Long?, limit: Int) =
                    acquired(profiles.history(personId, after, limit))
            }
        val observedTransfers =
            object : EmploymentTransferRepository by transfers {
                override fun forEmployee(companyId: UUID, id: UUID) =
                    acquired(transfers.forEmployee(companyId, id))
            }
        val observedDirectory =
            object : CompanyDirectoryRepository by directory {
                override fun list(
                    companyId: UUID,
                    asOf: LocalDate,
                    statuses: Set<EmploymentStatus>,
                    query: String,
                    after: String?,
                    limit: Int,
                    departmentId: UUID?,
                ) =
                    acquired(
                        directory.list(companyId, asOf, statuses, query, after, limit, departmentId)
                    )

                override fun departments(
                    companyId: UUID,
                    asOf: LocalDate,
                    statuses: Set<EmploymentStatus>,
                    query: String,
                    limit: Int,
                ) = acquired(directory.departments(companyId, asOf, statuses, query, limit))
            }
        val observedLifecycle =
            object : LifecycleRepository by lifecycle {
                override fun caseDetails(companyId: UUID, id: UUID) =
                    lifecycle.caseDetails(companyId, id).let {
                        if (mode == "case") acquired(it) else it
                    }

                override fun cases(
                    companyId: UUID,
                    employeeId: UUID?,
                    status: LifecycleStatus?,
                    after: UUID?,
                    limit: Int,
                ) = acquired(lifecycle.cases(companyId, employeeId, status, after, limit))

                override fun history(companyId: UUID, id: UUID, after: Long?, limit: Int) =
                    acquired(lifecycle.history(companyId, id, after, limit))

                override fun template(companyId: UUID, id: UUID) =
                    acquired(lifecycle.template(companyId, id))

                override fun templates(companyId: UUID, after: String?, limit: Int) =
                    acquired(lifecycle.templates(companyId, after, limit))

                override fun assigned(
                    companyId: UUID,
                    accountId: UUID,
                    after: String?,
                    limit: Int,
                ) = acquired(lifecycle.assigned(companyId, accountId, after, limit))
            }
        val observedMembers =
            object : MembershipRepository by members {
                override fun activeReferences(
                    companyId: UUID,
                    permissions: Set<String>,
                    query: String,
                    after: UUID?,
                    limit: Int,
                ) = acquired(members.activeReferences(companyId, permissions, query, after, limit))
            }
        fun read(selected: Actor): Result<*> =
            when (mode) {
                "employee",
                "self_employee" ->
                    GetEmployee(
                            observedPeople,
                            observedCompanies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, employee, LocalDate.parse("2026-10-01"))
                "employees" ->
                    ListEmployees(
                            observedPeople,
                            observedCompanies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, LocalDate.parse("2026-10-01"), "", null, 20)
                "details",
                "self_details" ->
                    GetEmploymentDetails(
                            observedPeople,
                            units,
                            observedCompanies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, employee, LocalDate.parse("2026-10-01"))
                "history" ->
                    GetEmploymentHistory(
                            observedPeople,
                            observedCompanies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, employee, null, 20)
                "revision" ->
                    GetEmploymentRevision(
                            observedPeople,
                            observedCompanies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, employee, 0)
                "transfers" ->
                    GetEmploymentTransfers(
                            observedTransfers,
                            observedPeople,
                            observedCompanies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, employee)
                "self_employments" ->
                    ListSelfEmployments(
                            observedPeople,
                            observedCompanies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, null, 20)
                "profile",
                "self_profile" ->
                    GetPersonProfile(
                            observedProfiles,
                            observedPeople,
                            observedCompanies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, employee)
                "profile_history" ->
                    GetPersonProfileHistory(
                            observedProfiles,
                            observedPeople,
                            observedCompanies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, employee, null, 20)
                "directory" ->
                    ListCompanyDirectory(
                            observedDirectory,
                            observedPeople,
                            observedCompanies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, "", null, 20)
                "departments" ->
                    FindDirectoryDepartments(
                            observedDirectory,
                            observedPeople,
                            observedCompanies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, "", 20)
                "case" ->
                    GetLifecycleCase(
                            observedLifecycle,
                            observedCompanies,
                            identities,
                            members,
                            observedPeople,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, case)
                "cases" ->
                    ListLifecycleCases(
                            observedLifecycle,
                            observedCompanies,
                            identities,
                            members,
                            observedPeople,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, employee, null, null, 20)
                "case_history" ->
                    GetLifecycleHistory(
                            observedLifecycle,
                            observedCompanies,
                            identities,
                            members,
                            observedPeople,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, case, null, 20)
                "template" ->
                    GetLifecycleTemplate(
                            observedLifecycle,
                            observedCompanies,
                            identities,
                            members,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, template)
                "templates" ->
                    ListLifecycleTemplates(
                            observedLifecycle,
                            observedCompanies,
                            identities,
                            members,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, null, 20)
                "assigned" ->
                    ListAssignedLifecycleTasks(
                            observedLifecycle,
                            observedCompanies,
                            identities,
                            members,
                            observedPeople,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, null, 20)
                "assignees" ->
                    ListLifecycleAssignees(
                            observedCompanies,
                            identities,
                            observedMembers,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, "", null, 20)
                else ->
                    GetOffboardingReview(
                            observedLifecycle,
                            observedCompanies,
                            identities,
                            members,
                            observedPeople,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, case)
            }
        fun counts() =
            listOf("audit_entries", "outbox_events", "operation_receipts").associateWith {
                database()
                    .queryForObject(
                        "select count(*) from $it where company_id=?",
                        Long::class.java,
                        company,
                    )
            }
        try {
            val before = counts()
            val normal = read(actor)
            assertTrue(normal is Result.Success, normal.toString())
            assertEquals(1, acquired, "The intended final acquisition must be exercised")
            acquired = 0
            expire = true
            val denied = read(actor)
            assertEquals(1, acquired)
            assertEquals(
                "mfa_required",
                (denied as? Result.Failed)?.failure?.code,
                "Acquired data must not be released after proof expiry",
            )
            assertEquals(before, counts())
            val renewed = read(actor.copy(mfaVerifiedAt = clock.instant()))
            assertEquals(normal, renewed, "A renewed proof must recover the same scoped result")
            assertEquals(before, counts())
        } finally {
            clock.set(original)
        }
    }
}
