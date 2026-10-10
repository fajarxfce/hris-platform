package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.*
import dev.fajar.hris.people.domain.usecases.*
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
class EmploymentAssuranceHttpTest : PeopleApiFixture() {
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var units: OrganizationRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var transfers: EmploymentTransferRepository
    @Autowired private lateinit var lifecycle: LifecycleRepository
    @Autowired private lateinit var accountProbe: AccountLockProbe

    @ParameterizedTest
    @ValueSource(
        strings =
            ["get", "list", "self", "history", "details", "revision", "create", "revise", "cancel"]
    )
    fun proofExpiryDuringAnAccessWaitRequiresRenewalBeforeReadWriteOrReceiptReplay(
        operation: String
    ) {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        val existing = employee(browser, csrf, company)
        val baseline = if (operation == "cancel") 1L else 0L
        if (operation == "cancel") {
            val scheduled =
                revise(
                    browser,
                    csrf,
                    company,
                    existing,
                    0,
                    terms("2026-11-01", status = "SUSPENDED"),
                )
            assertEquals(200, scheduled.statusCode(), scheduled.body())
        }
        val employee = if (operation == "create") UUID.randomUUID() else existing
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash,mfa_secret_encrypted) select ?,?,'Employment assurance fixture',password_hash,'fixture-enrolled' from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
        for (permission in listOf("people.read", "people.manage")) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                company,
                account,
                permission,
            )
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val get = GetEmployee(people, companies, members, identities, transactions, security, clock)
        val details =
            GetEmploymentDetails(
                people,
                units,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
            )
        val list =
            ListEmployees(people, companies, members, identities, transactions, security, clock)
        val self =
            ListSelfEmployments(
                people,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
            )
        val history =
            GetEmploymentHistory(
                people,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
            )
        val revision =
            GetEmploymentRevision(
                people,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
            )
        val create =
            CreateEmployee(
                people,
                companies,
                members,
                identities,
                units,
                operations,
                journal,
                transactions,
                security,
                clock,
            )
        val revise =
            ReviseEmployment(
                people,
                companies,
                members,
                identities,
                units,
                operations,
                journal,
                transactions,
                transfers,
                lifecycle,
                security,
                clock,
            )
        val cancel =
            CancelEmploymentRevision(
                people,
                companies,
                members,
                identities,
                operations,
                journal,
                transactions,
                security,
                clock,
            )
        val actor =
            Actor(
                account,
                company,
                setOf("people.read", "people.manage"),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1),
            )
        val key = UUID.randomUUID()
        val initialTerms =
            EmploymentTerms(
                effectiveFrom = LocalDate.parse("2026-01-01"),
                contract = ContractKind.PERMANENT,
                startDate = LocalDate.parse("2026-01-01"),
                status = EmploymentStatus.ACTIVE,
                endDate = null,
                branchId = null,
                departmentId = null,
                positionId = null,
                costCenterId = null,
                managerId = null,
            )
        val person = PersonProfile(UUID.randomUUID(), null, "New employee", null, "ID", null)
        val invoke = { current: Actor ->
            when (operation) {
                "get" -> get.execute(current, existing, LocalDate.parse("2026-10-01"))
                "details" -> details.execute(current, existing, LocalDate.parse("2026-10-01"))
                "list" -> list.execute(current, LocalDate.parse("2026-10-01"), "", null, 50)
                "self" -> self.execute(current, null, 20)
                "history" -> history.execute(current, existing, null, 50)
                "revision" -> revision.execute(current, existing, 0)
                "create" ->
                    create.execute(
                        current,
                        key,
                        employee,
                        "E${employee.toString().take(8)}",
                        person,
                        initialTerms,
                        "Onboarding assurance fixture",
                    )
                "revise" ->
                    revise.execute(
                        current,
                        key,
                        existing,
                        0,
                        initialTerms.copy(
                            effectiveFrom = LocalDate.parse("2026-11-01"),
                            status = EmploymentStatus.SUSPENDED,
                        ),
                        "Planned correction",
                    )
                else -> cancel.execute(current, key, existing, 1, 1, "Cancel planned correction")
            }
        }
        val barrier = AccountLockProbe.Barrier(account)
        accountProbe.current.set(barrier)
        try {
            val expired =
                Executors.newSingleThreadExecutor().use { executor ->
                    val pending = executor.submit<Result<*>> { invoke(actor) }
                    try {
                        assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                        clock.set(clock.instant().plusSeconds(2))
                        barrier.release.countDown()
                        pending.get(10, TimeUnit.SECONDS)
                    } finally {
                        barrier.release.countDown()
                        accountProbe.current.set(null)
                    }
                }
            assertEquals("mfa_required", (expired as? Result.Failed)?.failure?.code)
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from operation_receipts where operation_id=?",
                        Int::class.java,
                        key,
                    ),
            )
            if (operation == "create")
                assertEquals(
                    0,
                    database()
                        .queryForObject(
                            "select count(*) from employments where id=?",
                            Int::class.java,
                            employee,
                        ),
                )
            else
                assertEquals(
                    baseline,
                    database()
                        .queryForObject(
                            "select version from employments where id=?",
                            Long::class.java,
                            employee,
                        ),
                )
            val renewed = actor.copy(mfaVerifiedAt = clock.instant())
            val accepted = invoke(renewed)
            assertTrue(accepted is Result.Success)
            if (operation in setOf("create", "revise", "cancel")) {
                val receipt = (accepted as Result.Success).value as MutationReceipt
                assertEquals(employee, receipt.id)
                assertEquals(if (operation == "create") 0L else baseline + 1, receipt.version)
                assertEquals(Result.Success(receipt.copy(replayed = true)), invoke(renewed))
                clock.set(clock.instant().plus(security.maximumMfaAge).plusSeconds(1))
                assertEquals("mfa_required", (invoke(renewed) as? Result.Failed)?.failure?.code)
                assertEquals(
                    receipt.version,
                    database()
                        .queryForObject(
                            "select version from employments where id=?",
                            Long::class.java,
                            employee,
                        ),
                )
                assertEquals(
                    1,
                    database()
                        .queryForObject(
                            "select count(*) from operation_receipts where operation_id=?",
                            Int::class.java,
                            key,
                        ),
                )
            }
        } finally {
            barrier.release.countDown()
            accountProbe.current.set(null)
        }
    }
}
