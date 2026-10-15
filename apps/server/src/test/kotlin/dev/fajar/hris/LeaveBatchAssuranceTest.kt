package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.leave.domain.usecases.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Instant
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class LeaveBatchAssuranceTest : LeaveBatchApiFixture() {
    @Autowired private lateinit var batches: LeaveBatchRepository
    @Autowired private lateinit var policies: LeavePolicyRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    private val security = IdentitySecurityPolicy()

    @ParameterizedTest
    @ValueSource(strings = ["types", "review", "list", "detail", "accrual", "close", "resume"])
    fun staleInteractiveAssuranceCannotSurviveAResourceWaitOrReceiptReplay(action: String) {
        val f = batchAssuranceFixture()
        val actor = operator(f)
        val invoke = invocation(f, action)
        val before = retainedRows(f)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<*>> { invoke(actor) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    clock.set(clock.instant().plusSeconds(2))
                    barrier.release.countDown()
                    failure(pending.get(5, TimeUnit.SECONDS), "mfa_required")
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
            assertEquals(before, retainedRows(f))
            val renewed = actor.copy(mfaVerifiedAt = clock.instant())
            val allowed = invoke(renewed)
            assertTrue(allowed is Result.Success, allowed.toString())
            if (action in setOf("accrual", "close", "resume")) {
                val rows = retainedRows(f)
                failure(invoke(actor), "mfa_required")
                val receipt = (allowed as Result.Success).value as MutationReceipt
                assertEquals(Result.Success(receipt.copy(replayed = true)), invoke(renewed))
                assertEquals(rows, retainedRows(f))
            }
        } finally {
            barrier.release.countDown()
            accountProbe.current.set(null)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["types", "review", "list", "detail", "accrual", "close", "resume"])
    fun revokedGrantsCannotSurviveAnAlreadyResolvedRequest(action: String) {
        val f = batchAssuranceFixture()
        val actor = operator(f).copy(mfaVerifiedAt = clock.instant())
        val invoke = invocation(f, action)
        val before = retainedRows(f)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<*>> { invoke(actor) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=?",
                            f.company,
                            actor.accountId,
                        )
                    barrier.release.countDown()
                    failure(pending.get(5, TimeUnit.SECONDS), "access_denied")
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
            assertEquals(before, retainedRows(f))
        } finally {
            barrier.release.countDown()
            accountProbe.current.set(null)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["types", "review", "list", "detail"])
    fun readsRecheckAssuranceAfterAcquiringTheirPrivateResult(action: String) {
        val f = batchAssuranceFixture()
        val actor = operator(f)
        val invoke = invocation(f, action)
        batchProbe.afterRead = {
            batchProbe.afterRead = null
            clock.set(clock.instant().plusSeconds(2))
        }
        try {
            failure(invoke(actor), "mfa_required")
        } finally {
            batchProbe.afterRead = null
        }
        assertTrue(invoke(actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success)
    }

    @ParameterizedTest
    @ValueSource(strings = ["accrual", "close", "resume"])
    fun lateAssuranceFailureRollsBackBatchJobTargetsAttemptJournalAndReceipt(action: String) {
        val f = batchAssuranceFixture()
        val actor = operator(f)
        val invoke = invocation(f, action)
        val before = retainedRows(f)
        accountingProbe.beforeJournal = { clock.set(clock.instant().plusSeconds(2)) }
        try {
            failure(invoke(actor), "mfa_required")
        } finally {
            accountingProbe.beforeJournal = null
        }
        assertEquals(before, retainedRows(f))
        val retried = invoke(actor.copy(mfaVerifiedAt = clock.instant()))
        assertTrue(retried is Result.Success, retried.toString())
        assertFalse(((retried as Result.Success).value as MutationReceipt).replayed)
    }

    @ParameterizedTest
    @ValueSource(strings = ["types", "review", "list", "detail", "accrual", "close", "resume"])
    fun cancellationUnwindsPendingWorkAndReleasesAllGuards(action: String) {
        val f = batchAssuranceFixture()
        val actor = operator(f).copy(mfaVerifiedAt = clock.instant())
        val invoke = invocation(f, action)
        val before = retainedRows(f)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<*>> { invoke(actor) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    assertTrue(pending.cancel(true))
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
                assertEquals(true, pool.submit<Boolean> { true }.get(5, TimeUnit.SECONDS))
            }
            assertEquals(before, retainedRows(f))
            assertTrue(invoke(actor) is Result.Success)
        } finally {
            barrier.release.countDown()
            accountProbe.current.set(null)
        }
    }

    @Test
    fun pendingSubmissionOwnsItsOriginalEmployeeSelectionAndReceiptHash() {
        val f = batchAssuranceFixture()
        val actor = operator(f).copy(mfaVerifiedAt = clock.instant())
        val selected = linkedSetOf(f.employee)
        val operation = UUID.randomUUID()
        val id = UUID.randomUUID()
        val useCase = accrual()
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<Result<MutationReceipt>> {
                        useCase.execute(
                            actor,
                            operation,
                            id,
                            f.type,
                            YearMonth.of(2026, 9),
                            1,
                            selected,
                            "Reviewed selection",
                        )
                    }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    selected.clear()
                    selected += f.manager
                    barrier.release.countDown()
                    assertTrue(pending.get(5, TimeUnit.SECONDS) is Result.Success)
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
            val targets =
                database()
                    .queryForList(
                        "select employment_id from leave_batch_targets where company_id=? and batch_id=?",
                        UUID::class.java,
                        f.company,
                        id,
                    )
            assertEquals(listOf(f.employee), targets)
            val original =
                useCase.execute(
                    actor,
                    operation,
                    id,
                    f.type,
                    YearMonth.of(2026, 9),
                    1,
                    setOf(f.employee),
                    "Reviewed selection",
                )
            assertTrue(original is Result.Success && original.value.replayed)
        } finally {
            barrier.release.countDown()
            accountProbe.current.set(null)
        }
    }

    private fun batchAssuranceFixture(): LeaveFixture =
        accountingFixture(at = Instant.parse("2027-01-02T00:00:00Z")).also { trackBatchCompany(it) }

    private fun operator(f: LeaveFixture): Actor {
        val permissions =
            setOf("company.read", "leave.read", "leave.accrual.post", "leave.year.close")
        for (permission in permissions) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?) on conflict do nothing",
                f.company,
                f.managerAccount,
                permission,
            )
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.managerAccount,
            )
        return Actor(
            f.managerAccount,
            f.company,
            permissions,
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = 0,
            mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1),
        )
    }

    private fun retainedRows(f: LeaveFixture): List<Int> =
        listOf(
                "leave_batches",
                "leave_batch_targets",
                "leave_batch_attempts",
                "leave_batch_results",
                "background_jobs",
                "audit_entries",
                "operation_receipts",
            )
            .map { accountingRows(f, it) }

    private fun accrual() =
        StartLeaveAccrualBatch(
            batches,
            policies,
            people,
            companies,
            members,
            identities,
            batchJobs,
            operations,
            journal,
            transactions,
            clock,
            security,
        )

    private fun invocation(f: LeaveFixture, action: String): (Actor) -> Result<*> {
        val operation = UUID.randomUUID()
        val id = UUID.randomUUID()
        return when (action) {
            "types" -> {
                val observedPolicies =
                    object : LeavePolicyRepository by policies {
                        override fun catalog(
                            companyId: UUID,
                            active: Boolean?,
                            after: String?,
                            limit: Int,
                        ): Result<Page<LeaveType>> =
                            policies.catalog(companyId, active, after, limit).also {
                                batchProbe.afterRead?.invoke()
                            }
                    }
                val invoke: (Actor) -> Result<*> = { actor ->
                    ListLeaveBatchTypes(
                            observedPolicies,
                            identities,
                            members,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(actor, LeaveBatchKind.ACCRUAL, null, 20)
                }
                invoke
            }
            "review" -> { actor ->
                    ReviewLeaveBatch(
                            batches,
                            policies,
                            people,
                            companies,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(actor, LeaveBatchKind.ACCRUAL, f.type, YearMonth.of(2026, 9))
                }
            "list" -> { actor ->
                    ListLeaveBatches(
                            batches,
                            companies,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(actor, null, 20)
                }
            "detail" -> {
                accountingBody(createBatch(f, batchBody(f, id)))
                val invoke: (Actor) -> Result<*> = { actor ->
                    GetLeaveBatch(
                            batches,
                            batchJobs,
                            companies,
                            members,
                            identities,
                            transactions,
                            policies,
                            people,
                            clock,
                            security,
                        )
                        .execute(actor, id, null, 20)
                }
                invoke
            }
            "accrual" -> { actor ->
                    accrual()
                        .execute(
                            actor,
                            operation,
                            id,
                            f.type,
                            YearMonth.of(2026, 9),
                            1,
                            null,
                            "Accrual processing",
                        )
                }
            "close" -> { actor ->
                    StartLeaveYearCloseBatch(
                            batches,
                            policies,
                            people,
                            companies,
                            members,
                            identities,
                            batchJobs,
                            operations,
                            journal,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(actor, operation, id, f.type, 2026, 1, null, "Year closing")
                }
            "resume" -> {
                accountingBody(
                    createBatch(f, batchBody(f, id), client = f.supervisor, csrf = f.supervisorCsrf)
                )
                val lease =
                    claimDocuments(dev.fajar.hris.jobs.domain.entities.JobKind.LEAVE_ACCRUAL)
                        .single { batchId(it) == id }
                cancelBatch(f, lease)
                val version = batchView(f, id)["batch"]["version"].asLong()
                val invoke: (Actor) -> Result<*> = { actor ->
                    ResumeLeaveBatch(
                            batches,
                            policies,
                            batchJobs,
                            companies,
                            members,
                            identities,
                            operations,
                            journal,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(actor, operation, id, version, "Resume processing")
                }
                invoke
            }
            else -> error("Unknown test action")
        }
    }
}
