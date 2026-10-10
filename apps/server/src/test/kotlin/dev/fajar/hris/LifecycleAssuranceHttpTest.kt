package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.*
import dev.fajar.hris.people.domain.usecases.*
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
class LifecycleAssuranceHttpTest : LifecycleApiFixture() {
    @Autowired private lateinit var lifecycle: LifecycleRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var roles: RoleTemplateRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var accountProbe: AccountLockProbe

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "templates",
                "template",
                "get",
                "list",
                "history",
                "assigned",
                "assignees",
                "save",
                "start",
                "change",
                "assign",
                "cancel",
                "onboarding",
                "offboarding",
                "offboarding-review",
            ]
    )
    fun expiringProofCannotAuthorizeAReadMutationOrReceiptAfterAnAccessWait(operation: String) {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        val employment = employee(browser, csrf, company)
        val account = user()
        val permissions =
            setOf(
                "people.lifecycle.read",
                "people.lifecycle.manage",
                "people.lifecycle.perform",
                "people.manage",
                "people.offboard",
            )
        member(company, account, permissions)
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                account,
            )
        val kind =
            if (operation in setOf("offboarding", "offboarding-review")) "OFFBOARDING"
            else "ONBOARDING"
        val template = template(browser, csrf, company, kind)
        val targetDate = if (operation == "offboarding") "2026-09-30" else "2026-10-01"
        val id =
            if (operation == "start") UUID.randomUUID()
            else
                case(
                    browser,
                    csrf,
                    company,
                    employment,
                    template,
                    targetDate,
                    mapOf("equipment" to account),
                )
        val version =
            if (operation in setOf("onboarding", "offboarding")) {
                val completed = change(browser, csrf, company, id, 0)
                assertEquals(200, completed.statusCode(), completed.body())
                1L
            } else 0L
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val templates =
            ListLifecycleTemplates(
                lifecycle,
                companies,
                identities,
                members,
                transactions,
                security,
                clock,
            )
        val get =
            GetLifecycleCase(
                lifecycle,
                companies,
                identities,
                members,
                people,
                transactions,
                security,
                clock,
            )
        val review =
            GetOffboardingReview(
                lifecycle,
                companies,
                identities,
                members,
                people,
                transactions,
                security,
                clock,
            )
        val list =
            ListLifecycleCases(
                lifecycle,
                companies,
                identities,
                members,
                people,
                transactions,
                security,
                clock,
            )
        val history =
            GetLifecycleHistory(
                lifecycle,
                companies,
                identities,
                members,
                people,
                transactions,
                security,
                clock,
            )
        val assignees =
            ListLifecycleAssignees(companies, identities, members, transactions, security, clock)
        val assigned =
            ListAssignedLifecycleTasks(
                lifecycle,
                companies,
                identities,
                members,
                people,
                transactions,
                security,
                clock,
            )
        val save =
            SaveLifecycleTemplate(
                lifecycle,
                companies,
                identities,
                members,
                operations,
                journal,
                transactions,
                security,
                clock,
            )
        val start =
            StartLifecycleCase(
                lifecycle,
                companies,
                identities,
                people,
                members,
                operations,
                journal,
                transactions,
                security,
                clock,
            )
        val change =
            ChangeLifecycleTask(
                lifecycle,
                companies,
                identities,
                members,
                people,
                operations,
                journal,
                transactions,
                security,
                clock,
            )
        val assign =
            AssignLifecycleTask(
                lifecycle,
                companies,
                identities,
                people,
                members,
                operations,
                journal,
                transactions,
                security,
                clock,
            )
        val cancel =
            CancelLifecycleCase(
                lifecycle,
                companies,
                identities,
                members,
                people,
                operations,
                journal,
                transactions,
                security,
                clock,
            )
        val onboarding =
            CompleteOnboarding(
                lifecycle,
                companies,
                identities,
                members,
                people,
                operations,
                journal,
                transactions,
                security,
                clock,
            )
        val offboarding =
            CompleteOffboarding(
                lifecycle,
                people,
                companies,
                identities,
                members,
                roles,
                operations,
                journal,
                transactions,
                security,
                clock,
            )
        val getTemplate =
            GetLifecycleTemplate(
                lifecycle,
                companies,
                identities,
                members,
                transactions,
                security,
                clock,
            )
        val createdTemplate = UUID.randomUUID()
        val definition =
            LifecycleTemplate(
                createdTemplate,
                "T${createdTemplate.toString().take(8)}",
                "Assurance checklist",
                LifecycleKind.ONBOARDING,
                true,
                0,
                listOf(LifecycleTaskDefinition("equipment", "Review equipment", true, 0)),
            )
        val key = UUID.randomUUID()
        val actor =
            Actor(
                account,
                company,
                permissions,
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1),
            )
        val invoke = { current: Actor ->
            when (operation) {
                "templates" -> templates.execute(current, null, 50)
                "template" -> getTemplate.execute(current, template)
                "get" -> get.execute(current, id)
                "offboarding-review" -> review.execute(current, id)
                "list" -> list.execute(current, employment, null, null, 50)
                "history" -> history.execute(current, id, null, 50)
                "assigned" -> assigned.execute(current, null, 50)
                "assignees" -> assignees.execute(current, "", null, 50)
                "save" -> save.execute(current, key, definition, null, "Checklist assurance")
                "start" ->
                    start.execute(
                        current,
                        key,
                        id,
                        StartLifecycleCommand(
                            employment,
                            template,
                            0,
                            LocalDate.parse(targetDate),
                            emptyMap(),
                            "Start transition",
                        ),
                    )
                "change" ->
                    change.execute(
                        current,
                        key,
                        id,
                        "equipment",
                        LifecycleTaskChange(version, LifecycleTaskStatus.DONE, "Complete task"),
                    )
                "assign" ->
                    assign.execute(current, key, id, "equipment", version, null, "Clear assignment")
                "cancel" -> cancel.execute(current, key, id, version, "Cancel transition")
                "onboarding" -> onboarding.execute(current, key, id, version, "Complete onboarding")
                else -> offboarding.execute(current, key, id, version, 0, "Complete offboarding")
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
                    } finally {
                        barrier.release.countDown()
                    }
                    pending.get(10, TimeUnit.SECONDS)
                }
            accountProbe.current.set(null)
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
            val fresh = actor.copy(mfaVerifiedAt = clock.instant())
            val accepted = invoke(fresh)
            assertInstanceOf(Result.Success::class.java, accepted, accepted.toString())
            assertEquals("mfa_required", (invoke(actor) as? Result.Failed)?.failure?.code)
            val read =
                operation in
                    setOf(
                        "templates",
                        "template",
                        "get",
                        "offboarding-review",
                        "list",
                        "history",
                        "assigned",
                        "assignees",
                    )
            val replay = invoke(fresh)
            if (read) assertEquals(accepted, replay)
            else {
                val receipt = (accepted as Result.Success<*>).value as MutationReceipt
                assertEquals(Result.Success(receipt.copy(replayed = true)), replay)
            }
            assertEquals(
                if (read) 0 else 1,
                database()
                    .queryForObject(
                        "select count(*) from operation_receipts where operation_id=?",
                        Int::class.java,
                        key,
                    ),
            )
        } finally {
            barrier.release.countDown()
            accountProbe.current.set(null)
        }
    }
}
