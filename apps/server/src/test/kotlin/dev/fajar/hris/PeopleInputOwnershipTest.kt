package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.*
import dev.fajar.hris.people.domain.usecases.*
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class PeopleInputOwnershipTest : LifecycleApiFixture() {
    @Autowired private lateinit var lifecycle: LifecycleRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner

    @ParameterizedTest
    @ValueSource(strings = ["template", "assignees"])
    fun lifecycleWritesOwnTheirReviewedCollectionsBeforeOperationAcquisition(mode: String) {
        val browser = client()
        val csrf = login(browser, "admin@example.test")
        val company = company(browser, csrf)
        val author =
            database()
                .queryForObject(
                    "select id from accounts where email='admin@example.test'",
                    UUID::class.java,
                )!!
        val securityVersion =
            database()
                .queryForObject(
                    "select security_version from accounts where id=?",
                    Long::class.java,
                    author,
                )!!
        val target = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Task recipient',password_hash from accounts where email='admin@example.test'",
                target,
                "$target@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                target,
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'people.lifecycle.perform')",
                company,
                target,
            )
        val employee = employee(browser, csrf, company)
        val templateId =
            if (mode == "template") UUID.randomUUID() else template(browser, csrf, company)
        val caseId = UUID.randomUUID()
        val actor =
            Actor(
                author,
                company,
                PermissionCatalog.companyAdministrator,
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = securityVersion,
            )
        val security = IdentitySecurityPolicy(enforceMfa = false)
        val original = LifecycleTaskDefinition("equipment", "Reviewed equipment", true, 0)
        val tasks = mutableListOf(original)
        val assignees = mutableMapOf("equipment" to author)
        val definition =
            LifecycleTemplate(
                templateId,
                "OWNED_TASKS",
                "Owned tasks",
                LifecycleKind.ONBOARDING,
                true,
                0,
                tasks,
            )
        val command =
            StartLifecycleCommand(
                employee,
                templateId,
                0,
                LocalDate.parse("2026-10-01"),
                assignees,
                "Reviewed assignments",
            )
        var mutate = true
        val observedOperations =
            object : OperationRepository by operations {
                override fun lockAndReplay(actor: Actor, key: OperationKey) =
                    operations.lockAndReplay(actor, key).also {
                        if (mutate) {
                            tasks[0] =
                                original.copy(title = "Unreviewed replacement", required = false)
                            assignees["equipment"] = target
                        }
                    }
            }
        val key = UUID.randomUUID()
        val save =
            SaveLifecycleTemplate(
                lifecycle,
                companies,
                identities,
                members,
                observedOperations,
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
                observedOperations,
                journal,
                transactions,
                security,
                clock,
            )
        val result =
            if (mode == "template") save.execute(actor, key, definition, null, "Reviewed tasks")
            else start.execute(actor, key, caseId, command)
        assertTrue(result is Result.Success, result.toString())
        if (mode == "template") {
            val stored = transactions.run(actor) { lifecycle.template(company, templateId) }
            assertEquals(listOf(original), (stored as Result.Success).value!!.tasks)
        } else {
            val stored = transactions.run(actor) { lifecycle.case(company, caseId) }
            assertEquals(author, (stored as Result.Success).value!!.tasks.single().assigneeId)
        }
        mutate = false
        val replay =
            if (mode == "template")
                save.execute(
                    actor,
                    key,
                    definition.copy(tasks = listOf(original)),
                    null,
                    "Reviewed tasks",
                )
            else
                start.execute(
                    actor,
                    key,
                    caseId,
                    command.copy(assignees = mapOf("equipment" to author)),
                )
        assertEquals(Result.Success((result as Result.Success).value.copy(replayed = true)), replay)
    }
}
