package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.people.domain.usecases.CancelEmploymentRevision
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class EmploymentRevisionCancellationDateTest : PeopleApiFixture() {
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun newCancellationCannotCommitAfterItsRevisionBecomesEffectiveButOldReceiptsRemainRecoverable(
        replay: Boolean
    ) {
        val original = clock.instant()
        try {
            val beforeMidnight = Instant.parse("2026-10-31T16:59:59Z")
            clock.set(beforeMidnight)
            val browser = client()
            val csrf = login(browser, "admin@example.test")
            val company = company(browser, csrf)
            val employee = employee(browser, csrf, company)
            val revised = revise(browser, csrf, company, employee, 0, terms("2026-11-01"))
            assertEquals(200, revised.statusCode(), revised.body())
            val author =
                database()
                    .queryForObject(
                        "select id from accounts where email='admin@example.test'",
                        UUID::class.java,
                    )!!
            val version =
                database()
                    .queryForObject(
                        "select security_version from accounts where id=?",
                        Long::class.java,
                        author,
                    )!!
            val actor =
                Actor(
                    author,
                    company,
                    PermissionCatalog.companyAdministrator,
                    beforeMidnight,
                    UUID.randomUUID(),
                    credentialVersion = version,
                )
            var expire = false
            var journals = 0
            val observedJournal =
                object : ChangeJournalRepository by journal {
                    override fun record(actor: Actor, change: ChangeRecord) =
                        journal.record(actor, change).also {
                            journals++
                            if (expire && !replay) clock.set(beforeMidnight.plusSeconds(2))
                        }
                }
            val observedOperations =
                object : OperationRepository by operations {
                    override fun lockAndReplay(actor: Actor, key: OperationKey) =
                        operations.lockAndReplay(actor, key).also {
                            if (expire && replay) clock.set(beforeMidnight.plusSeconds(2))
                        }
                }
            val subject =
                CancelEmploymentRevision(
                    people,
                    companies,
                    members,
                    identities,
                    observedOperations,
                    observedJournal,
                    transactions,
                    IdentitySecurityPolicy(enforceMfa = false),
                    clock,
                )
            val key = UUID.randomUUID()
            fun invoke() =
                subject.execute(actor, key, employee, 1, 1, "Reviewed future cancellation")
            fun state() =
                buildMap<String, Any?> {
                    for (table in
                        listOf("audit_entries", "outbox_events", "operation_receipts")) put(
                        table,
                        database()
                            .queryForObject(
                                "select count(*) from $table where company_id=?",
                                Long::class.java,
                                company,
                            ),
                    )
                    put(
                        "version",
                        database()
                            .queryForObject(
                                "select version from employments where company_id=? and id=?",
                                Long::class.java,
                                company,
                                employee,
                            ),
                    )
                    put(
                        "revision",
                        transactions.run(actor) { people.findRevision(company, employee, 1) },
                    )
                }
            val first = if (replay) invoke() else null
            if (first != null) assertTrue(first is Result.Success, first.toString())
            val before = state()
            expire = true
            journals = 0
            val result = invoke()
            assertEquals(if (replay) 0 else 1, journals)
            if (replay)
                assertEquals(
                    Result.Success((first as Result.Success).value.copy(replayed = true)),
                    result,
                )
            else
                assertEquals(
                    "effective_revision_cannot_be_cancelled",
                    (result as? Result.Failed)?.failure?.code,
                )
            assertEquals(before, state())
        } finally {
            clock.set(original)
        }
    }
}
