package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.leave.domain.usecases.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class LeaveCarryExpiryAssuranceTest : LeaveCarryStorageFixture() {
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired
    private lateinit var batches: dev.fajar.hris.leave.domain.repositories.LeaveBatchRepository
    private val security = IdentitySecurityPolicy(enforceMfa = true)

    private fun prepared(action: String): PreparedCarry {
        val c = if (action == "resume") carried() else prepareCarry()
        if (action == "resume")
            database()
                .update(
                    "update background_jobs set status='FAILED',finished_at=now() where company_id=?",
                    c.f.company,
                )
        return c
    }

    private fun actor(c: PreparedCarry): Actor {
        val actor = accountingActor(c.f)
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                actor.accountId,
            )
        return actor.copy(mfaVerifiedAt = clock.instant())
    }

    private fun retained(c: PreparedCarry): Map<String, Any> = buildMap {
        for (table in
            listOf(
                "background_jobs",
                "leave_batches",
                "leave_batch_targets",
                "leave_carry_expiries",
                "leave_carry_expiry_attempts",
                "leave_ledger",
                "leave_carry_buckets",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
                "mobile_sync_changes",
            )) {
            put(table, accountingRows(c.f, table))
        }
        put(
            "origins",
            success(
                transactions.run(accountingActor(c.f)) {
                    carryExpiries.list(c.f.company, null, 100)
                }
            ),
        )
    }

    private fun invoke(
        action: String,
        c: PreparedCarry,
        actor: Actor,
        operation: UUID,
        id: UUID,
        jobs: JobRepository = carryExpiryJobs,
        changes: ChangeJournalRepository = journal,
    ): Result<MutationReceipt> =
        when (action) {
            "direct" ->
                CloseEmployeeLeaveYear(
                        carryLedger,
                        carryPolicies,
                        carryEntitlements,
                        people,
                        companies,
                        members,
                        identities,
                        operations,
                        changes,
                        transactions,
                        clock,
                        carryRequests,
                        security,
                        carryRepository,
                        jobs,
                        carryExpiries,
                    )
                    .execute(
                        actor,
                        operation,
                        id,
                        c.f.employee,
                        c.f.type,
                        2026,
                        c.closing.policy.revision,
                        c.closing.sourceVersion,
                        0,
                        "Reviewed closing",
                    )
            "batch" ->
                StartLeaveYearCloseBatch(
                        batches,
                        carryPolicies,
                        people,
                        companies,
                        members,
                        identities,
                        jobs,
                        operations,
                        changes,
                        transactions,
                        clock,
                        security,
                        carryExpiries,
                    )
                    .execute(
                        actor,
                        operation,
                        id,
                        c.f.type,
                        2026,
                        c.closing.policy.revision,
                        setOf(c.f.employee),
                        "Reviewed closing",
                    )
            "resume" ->
                ResumeLeaveCarryExpiry(
                        carryExpiries,
                        jobs,
                        companies,
                        members,
                        identities,
                        operations,
                        changes,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, operation, id, 0, "Resume fixed expiry")
            else -> error("Unknown fixture action")
        }

    @ParameterizedTest
    @CsvSource(
        "direct,queue",
        "batch,queue",
        "resume,queue",
        "direct,journal",
        "batch,journal",
        "resume,journal",
    )
    fun expiredAssuranceCannotAdmitJobsOrSurviveLateJournalWrites(action: String, phase: String) {
        val c = prepared(action)
        val actor =
            actor(c)
                .copy(mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1))
        val id =
            if (action == "resume")
                success(transactions.run(actor) { carryExpiries.list(c.f.company, null, 10) })
                    .items
                    .single()
                    .id
            else UUID.randomUUID()
        val operation = UUID.randomUUID()
        val before = retained(c)
        val delayed =
            object : JobRepository by carryExpiryJobs {
                override fun lockQueue(companyId: UUID): Result<Unit> =
                    carryExpiryJobs.lockQueue(companyId).also {
                        if (phase == "queue") clock.set(clock.instant().plusSeconds(2))
                    }
            }
        val lateJournal =
            object : ChangeJournalRepository {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                    journal.record(actor, change).also {
                        if (phase == "journal") clock.set(clock.instant().plusSeconds(2))
                    }
            }
        assertEquals(
            "mfa_required",
            (invoke(action, c, actor, operation, id, delayed, lateJournal) as? Result.Failed)
                ?.failure
                ?.code,
        )
        assertEquals(before, retained(c))
        val renewed = actor.copy(mfaVerifiedAt = clock.instant())
        val receipt = success(invoke(action, c, renewed, operation, id))
        assertTrue(success(invoke(action, c, renewed, operation, id)).replayed)
        assertEquals(id, receipt.id)
    }

    @ParameterizedTest
    @ValueSource(strings = ["direct", "batch", "resume"])
    fun cancellationAfterJournalRollsBackJobsOriginsAndReceipts(action: String) {
        val c = prepared(action)
        val actor = actor(c)
        val id =
            if (action == "resume")
                success(transactions.run(actor) { carryExpiries.list(c.f.company, null, 10) })
                    .items
                    .single()
                    .id
            else UUID.randomUUID()
        val operation = UUID.randomUUID()
        val before = retained(c)
        val cancelled =
            object : ChangeJournalRepository {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> {
                    success(journal.record(actor, change))
                    throw CancellationException("Expiry journal fixture")
                }
            }
        assertThrows(CancellationException::class.java) {
            invoke(action, c, actor, operation, id, changes = cancelled)
        }
        assertEquals(before, retained(c))
        success(invoke(action, c, actor, operation, id))
    }
}
