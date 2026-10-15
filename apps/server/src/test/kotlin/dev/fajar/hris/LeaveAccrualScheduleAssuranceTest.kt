package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.leave.domain.usecases.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class LeaveAccrualScheduleAssuranceTest : LeaveAccrualScheduleApiFixture() {
    @Autowired private lateinit var schedules: LeaveAccrualScheduleRepository
    @Autowired private lateinit var batches: LeaveBatchRepository
    @Autowired private lateinit var policies: LeavePolicyRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository

    @BeforeEach
    fun enforceMfaInTheUseCasesUnderTest() {
        // HTTP fixture setup uses its own relaxed policy; these commands and reads do not.
        scheduleAccrual =
            ScheduleLeaveAccrual(
                schedules,
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
                scheduleSecurity,
            )
        reviewSchedule =
            ReviewLeaveAccrualSchedule(
                batches,
                policies,
                people,
                companies,
                members,
                identities,
                transactions,
                clock,
                scheduleSecurity,
            )
        getSchedule =
            GetLeaveAccrualSchedule(
                schedules,
                companies,
                members,
                identities,
                transactions,
                clock,
                scheduleSecurity,
            )
        listSchedules =
            ListLeaveAccrualSchedules(
                schedules,
                companies,
                members,
                identities,
                transactions,
                clock,
                scheduleSecurity,
            )
    }

    @ParameterizedTest
    @ValueSource(strings = ["review", "list", "detail", "schedule"])
    fun assuranceIsRecheckedAfterResourceWaitsAndBeforeOriginalReceiptReplay(action: String) {
        val f = scheduledFixture()
        val invoke = invocation(f, action)
        val actor = scheduleOperator(f)
        val before = scheduleRows(f)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<*>> { invoke(actor) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    clock.set(clock.instant().plusSeconds(2))
                    barrier.release.countDown()
                    failure(pending.get(10, TimeUnit.SECONDS), "mfa_required")
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
            assertEquals(before, scheduleRows(f))
            val renewed = actor.copy(mfaVerifiedAt = clock.instant())
            val allowed = invoke(renewed)
            assertTrue(allowed is Result.Success, allowed.toString())
            if (action == "schedule") {
                val rows = scheduleRows(f)
                failure(invoke(actor), "mfa_required")
                val receipt = (allowed as Result.Success).value as MutationReceipt
                assertEquals(Result.Success(receipt.copy(replayed = true)), invoke(renewed))
                assertEquals(rows, scheduleRows(f))
            }
        } finally {
            barrier.release.countDown()
            accountProbe.current.set(null)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["review", "list", "detail", "schedule"])
    fun revokedAuthorityCannotSurviveAnAlreadyResolvedRequest(action: String) {
        for (credentials in listOf(false, true)) {
            val f = scheduledFixture()
            val invoke = invocation(f, action)
            val actor = scheduleOperator(f).copy(mfaVerifiedAt = clock.instant())
            val before = scheduleRows(f)
            val barrier = AccountLockProbe.Barrier(actor.accountId)
            accountProbe.current.set(barrier)
            try {
                Executors.newSingleThreadExecutor().use { pool ->
                    val pending = pool.submit<Result<*>> { invoke(actor) }
                    try {
                        assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                        if (credentials)
                            database()
                                .update(
                                    "update accounts set security_version=security_version+1 where id=?",
                                    actor.accountId,
                                )
                        else
                            database()
                                .update(
                                    "delete from membership_permissions where company_id=? and account_id=?",
                                    f.company,
                                    actor.accountId,
                                )
                        barrier.release.countDown()
                        failure(
                            pending.get(10, TimeUnit.SECONDS),
                            if (credentials) "session_revoked" else "access_denied",
                        )
                    } finally {
                        barrier.release.countDown()
                        accountProbe.current.set(null)
                    }
                }
                assertEquals(before, scheduleRows(f))
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["review", "list", "detail"])
    fun readsRecheckAssuranceAfterAcquiringPrivateResults(action: String) {
        val f = scheduledFixture()
        val invoke = invocation(f, action)
        val actor = scheduleOperator(f)
        val expire = {
            clock.set(clock.instant().plusSeconds(2))
            Unit
        }
        if (action == "review") batchProbe.afterRead = expire else scheduleProbe.afterRead = expire
        try {
            failure(invoke(actor), "mfa_required")
        } finally {
            batchProbe.afterRead = null
            scheduleProbe.afterRead = null
        }
        assertTrue(invoke(actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success)
    }

    @Test
    fun lateAssuranceFailureRollsBackEveryPeriodAndItsReceipt() {
        val f = scheduledFixture()
        val invoke = invocation(f, "schedule")
        val actor = scheduleOperator(f)
        val before = scheduleRows(f)
        accountingProbe.beforeJournal = { clock.set(clock.instant().plusSeconds(2)) }
        try {
            failure(invoke(actor), "mfa_required")
        } finally {
            accountingProbe.beforeJournal = null
        }
        assertEquals(before, scheduleRows(f))
        val retried = invoke(actor.copy(mfaVerifiedAt = clock.instant()))
        assertTrue(retried is Result.Success, retried.toString())
        assertFalse(((retried as Result.Success).value as MutationReceipt).replayed)
    }

    @ParameterizedTest
    @ValueSource(strings = ["review", "list", "detail", "schedule"])
    fun interruptionReleasesAllGuardsAndRetainsNoPartialWork(action: String) {
        val f = scheduledFixture()
        val invoke = invocation(f, action)
        val actor = scheduleOperator(f).copy(mfaVerifiedAt = clock.instant())
        val before = scheduleRows(f)
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
            assertEquals(before, scheduleRows(f))
            assertTrue(invoke(actor) is Result.Success)
        } finally {
            barrier.release.countDown()
            accountProbe.current.set(null)
        }
    }

    @Test
    fun pendingSubmissionOwnsTheReviewedEmployeeSelectionAndReceiptHash() {
        val f = scheduledFixture()
        val actor = scheduleOperator(f).copy(mfaVerifiedAt = clock.instant())
        val selected = linkedSetOf(f.employee)
        val input = scheduleCommand(f, employees = selected)
        val operation = UUID.randomUUID()
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<Result<MutationReceipt>> {
                        scheduleAccrual.execute(actor, operation, input)
                    }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    selected.clear()
                    selected += f.manager
                    barrier.release.countDown()
                    val result = pending.get(10, TimeUnit.SECONDS)
                    assertTrue(result is Result.Success, result.toString())
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
            val ids =
                database()
                    .queryForList(
                        "select employment_id from leave_batch_targets where company_id=?",
                        UUID::class.java,
                        f.company,
                    )
            assertEquals(listOf(f.employee, f.employee, f.employee), ids)
            val replay =
                scheduleAccrual.execute(
                    actor,
                    operation,
                    input.copy(employeeIds = setOf(f.employee)),
                )
            assertTrue(replay is Result.Success && replay.value.replayed)
        } finally {
            barrier.release.countDown()
            accountProbe.current.set(null)
        }
    }

    private fun invocation(f: LeaveFixture, action: String): (Actor) -> Result<*> {
        val input = scheduleCommand(f)
        val operation = UUID.randomUUID()
        if (action in setOf("list", "detail")) accountingBody(submitSchedule(f, input))
        return when (action) {
            "review" -> { actor ->
                    reviewSchedule.execute(actor, f.type, input.firstPeriod, input.periods)
                }
            "list" -> { actor -> listSchedules.execute(actor, null, 20) }
            "detail" -> { actor -> getSchedule.execute(actor, input.id) }
            "schedule" -> { actor -> scheduleAccrual.execute(actor, operation, input) }
            else -> error("Unknown test action")
        }
    }
}
