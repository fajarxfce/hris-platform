package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.JobKind
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.*
import dev.fajar.hris.people.domain.usecases.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired

class EmployeeImportCommandCompletionTest : EmployeeImportApiFixture() {
    @Autowired private lateinit var input: EmployeeImportInputRepository
    @Autowired private lateinit var imports: EmployeeImportRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var jobs: JobRepository

    @ParameterizedTest
    @CsvSource(
        "start,true",
        "start,false",
        "apply,true",
        "apply,false",
        "resume,true",
        "resume,false",
        "cancel,true",
        "cancel,false",
    )
    fun journalCompletionCannotOutliveTheReviewedImportProof(mode: String, mfa: Boolean) {
        val f = fixture()
        val id = if (mode == "start") UUID.randomUUID() else begin(f, csv("E01"))
        if (mode == "apply") drain(f, lease(id, JobKind.EMPLOYEE_IMPORT_PREVIEW))
        if (mode == "resume") {
            val stopped =
                abort.execute(
                    lease(id, JobKind.EMPLOYEE_IMPORT_PREVIEW),
                    Failure(FailureKind.CONFLICT, "fixture_stop"),
                )
            assertEquals(Result.Success(Unit), stopped)
        }
        val version = if (mode == "start") 0L else details(f, id)["batch"]["version"].asLong()
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash,mfa_secret_encrypted) select ?,?,'Import command fixture',password_hash,'fixture-enrolled' from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                f.company,
                account,
            )
        for (permission in f.actor.permissions) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                f.company,
                account,
                permission,
            )
        val original = clock.instant()
        val security = IdentitySecurityPolicy(enforceMfa = mfa)
        val actor =
            f.actor.copy(
                accountId = account,
                credentialVersion = 0,
                authenticatedAt = original.minus(security.recentAuthenticationAge).plusSeconds(1),
                mfaVerifiedAt = original.minus(security.recentAuthenticationAge).plusSeconds(1),
            )
        var expire = true
        var journals = 0
        val observedJournal =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord) =
                    journal.record(actor, change).also {
                        journals++
                        if (expire) clock.set(original.plusSeconds(2))
                    }
            }
        val key = UUID.randomUUID()
        fun execute(selected: Actor): Result<MutationReceipt> =
            when (mode) {
                "start" ->
                    StartEmployeeImport(
                            input = input,
                            imports = imports,
                            companies = companies,
                            members = members,
                            identities = identities,
                            jobs = jobs,
                            operations = operations,
                            journal = observedJournal,
                            transactions = transactions,
                            clock = clock,
                            security = security,
                        )
                        .execute(selected, key, id, "employees.csv", csv("E01"), "Reviewed intake")
                "apply" ->
                    ApplyEmployeeImport(
                            imports = imports,
                            companies = companies,
                            members = members,
                            identities = identities,
                            jobs = jobs,
                            operations = operations,
                            journal = observedJournal,
                            transactions = transactions,
                            clock = clock,
                            security = security,
                        )
                        .execute(selected, key, id, version, false, "Reviewed preview")
                "resume" ->
                    ResumeEmployeeImport(
                            imports = imports,
                            companies = companies,
                            members = members,
                            identities = identities,
                            jobs = jobs,
                            operations = operations,
                            journal = observedJournal,
                            transactions = transactions,
                            clock = clock,
                            security = security,
                        )
                        .execute(selected, key, id, version, "Reviewed recovery")
                else ->
                    CancelEmployeeImport(
                            imports = imports,
                            companies = companies,
                            members = members,
                            identities = identities,
                            jobs = jobs,
                            operations = operations,
                            journal = observedJournal,
                            transactions = transactions,
                            clock = clock,
                            security = security,
                        )
                        .execute(selected, key, id, version, "Reviewed cancellation")
            }
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
                            "select count(*) from $table where company_id=?",
                            Long::class.java,
                            f.company,
                        ),
                )
                put(
                    "batches",
                    database()
                        .queryForList(
                            "select id,version,status,job_id from employee_imports where company_id=? order by id",
                            f.company,
                        ),
                )
                put(
                    "jobs",
                    database()
                        .queryForList(
                            "select id,version,status,cancellation_requested from background_jobs where company_id=? order by id",
                            f.company,
                        ),
                )
            }
        try {
            val before = state()
            val denied = execute(actor)
            assertEquals(1, journals, "Actual journal boundary must be reached")
            assertEquals(
                "recent_authentication_required",
                (denied as? Result.Failed)?.failure?.code,
            )
            assertEquals(before, state())
            expire = false
            val renewed =
                actor.copy(authenticatedAt = clock.instant(), mfaVerifiedAt = clock.instant())
            val completed = execute(renewed)
            assertTrue(completed is Result.Success, completed.toString())
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
