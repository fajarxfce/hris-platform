package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import dev.fajar.hris.workforce.domain.repositories.WorkPeriodRepository
import dev.fajar.hris.workforce.domain.usecases.ApplyRosterBulk
import dev.fajar.hris.workforce.domain.usecases.ReviewRosterBulk
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class RosterBulkCompletionTest : RosterBulkApiFixture() {
    @Autowired private lateinit var schedules: ScheduleRepository
    @Autowired private lateinit var periods: WorkPeriodRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    private val security = IdentitySecurityPolicy()

    private fun writer(
        calendar: ScheduleRepository = schedules,
        changes: ChangeJournalRepository = journal,
        receipts: OperationRepository = operations,
        policy: IdentitySecurityPolicy = security,
    ) =
        ApplyRosterBulk(
            calendar,
            periods,
            people,
            receipts,
            changes,
            companies,
            members,
            identities,
            transactions,
            policy,
            clock,
        )

    private fun reader(
        calendar: ScheduleRepository = schedules,
        policy: IdentitySecurityPolicy = security,
    ) =
        ReviewRosterBulk(
            calendar,
            periods,
            people,
            companies,
            members,
            identities,
            transactions,
            policy,
            clock,
        )

    private fun selected(f: Fixture) =
        RosterBulkSelection(
            listOf(f.employee),
            listOf(LocalDate.parse(workDate), LocalDate.parse("2026-10-03")),
            RosterBulkMode.DAY_OFF,
        )

    private fun enrolled(f: Fixture): Actor {
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.actor.accountId,
            )
        return f.actor.copy(
            mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
        )
    }

    private fun unchanged(f: Fixture, key: UUID) {
        assertTrue(savedRows(f).isEmpty())
        noBulkCommit(f, key)
        for (query in
            listOf(
                "select count(*) from roster_revisions where company_id=?",
                "select count(*) from audit_entries where company_id=? and action like 'workforce.roster_%'",
                "select count(*) from mobile_sync_changes where company_id=? and collection='ROSTER_DAYS'",
                "select count(*) from work_periods where company_id=? and month='2026-10-01'::date",
            )) assertEquals(0, database().queryForObject(query, Int::class.java, f.company))
    }

    @ParameterizedTest
    @ValueSource(strings = ["guard", "calendar", "journal", "receipt"])
    fun currentMfaIsRequiredThroughTheLastWriteAndOriginalRetryRemainsSafe(phase: String) {
        val f = isolatedFixture()
        val selection = selected(f)
        val revision = bulkReview(f)["revision"].asString()
        val actor = enrolled(f)
        val key = UUID.randomUUID()
        val calendar =
            object : ScheduleRepository by schedules {
                override fun calendar(
                    companyId: UUID,
                    employeeId: UUID,
                    from: LocalDate,
                    until: LocalDate,
                ) =
                    schedules.calendar(companyId, employeeId, from, until).also {
                        if (phase == "calendar") clock.set(clock.instant().plusSeconds(2))
                    }
            }
        val changes =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord) =
                    journal.record(actor, change).also {
                        if (phase == "journal") clock.set(clock.instant().plusSeconds(2))
                    }
            }
        val receipts =
            object : OperationRepository by operations {
                override fun record(actor: Actor, key: OperationKey, receipt: MutationReceipt) =
                    operations.record(actor, key, receipt).also {
                        if (phase == "receipt") clock.set(clock.instant().plusSeconds(2))
                    }
            }
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        if (phase == "guard") accountProbe.current.set(barrier)
        val result =
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<Result<*>> {
                        writer(calendar, changes, receipts)
                            .execute(actor, key, selection, revision, "Reviewed bulk")
                    }
                try {
                    if (phase == "guard") {
                        assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                        clock.set(clock.instant().plusSeconds(2))
                        barrier.release.countDown()
                    }
                    pending.get(10, TimeUnit.SECONDS)
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
        assertEquals("mfa_required", (result as? Result.Failed)?.failure?.code)
        unchanged(f, key)
        assertTrue(
            writer()
                .execute(
                    actor.copy(mfaVerifiedAt = clock.instant()),
                    key,
                    selection,
                    revision,
                    "Reviewed bulk",
                ) is Result.Success
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["permission", "membership", "account", "credential"])
    fun revokedAuthorityAfterWaitingCannotWriteOrReplayTheBatch(mode: String) {
        val f = isolatedFixture()
        val selected = selection(f)
        val reviewed = bulkReview(f, selected)
        val key = UUID.randomUUID()
        val response =
            waiting(
                f.actor.accountId,
                {
                    when (mode) {
                        "permission" ->
                            database()
                                .update(
                                    "delete from membership_permissions where company_id=? and account_id=? and permission='workforce.manage'",
                                    f.company,
                                    f.actor.accountId,
                                )
                        "membership" ->
                            database()
                                .update(
                                    "update company_memberships set active=false where company_id=? and account_id=?",
                                    f.company,
                                    f.actor.accountId,
                                )
                        "account" ->
                            database()
                                .update(
                                    "update accounts set active=false where id=?",
                                    f.actor.accountId,
                                )
                        else ->
                            database()
                                .update(
                                    "update accounts set security_version=security_version+1 where id=?",
                                    f.actor.accountId,
                                )
                    }
                },
            ) {
                bulkApply(f, selected, reviewed, key)
            }
        assertTrue(response.statusCode() in setOf(401, 403), response.body())
        unchanged(f, key)
    }

    @Test
    fun aStoredReceiptDoesNotReplaceCurrentCommandPermission() {
        val f = isolatedFixture()
        val selected = selection(f)
        val reviewed = bulkReview(f, selected)
        val key = UUID.randomUUID()
        body(bulkApply(f, selected, reviewed, key))
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='workforce.manage'",
                f.company,
                f.actor.accountId,
            )
        assertEquals(403, bulkApply(f, selected, reviewed, key).statusCode())
        assertEquals(2, savedRows(f).size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["write", "journal", "receipt"])
    fun aLateFailureRollsBackEveryCellHistoryJournalSyncAndReceipt(phase: String) {
        val f = isolatedFixture()
        val revision = bulkReview(f)["revision"].asString()
        val key = UUID.randomUUID()
        var writes = 0
        val calendar =
            object : ScheduleRepository by schedules {
                override fun roster(
                    actor: Actor,
                    employeeId: UUID,
                    day: LocalDate,
                    shift: ShiftSnapshot?,
                    expectedVersion: Long?,
                    reason: String,
                    active: Boolean,
                ): Result<MutationReceipt> =
                    schedules
                        .roster(actor, employeeId, day, shift, expectedVersion, reason, active)
                        .flatMap {
                            writes++
                            if (phase == "write" && writes == 2)
                                Result.Failed(Failure(FailureKind.CONFLICT, "owned_write_failure"))
                            else Result.Success(it)
                        }
            }
        val changes =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord) =
                    journal.record(actor, change).flatMap {
                        if (phase == "journal" && writes == 2)
                            Result.Failed(Failure(FailureKind.CONFLICT, "owned_write_failure"))
                        else Result.Success(Unit)
                    }
            }
        val receipts =
            object : OperationRepository by operations {
                override fun record(actor: Actor, key: OperationKey, receipt: MutationReceipt) =
                    operations.record(actor, key, receipt).flatMap {
                        if (phase == "receipt")
                            Result.Failed(Failure(FailureKind.CONFLICT, "owned_write_failure"))
                        else Result.Success(Unit)
                    }
            }
        val result =
            writer(calendar, changes, receipts, security.copy(enforceMfa = false))
                .execute(f.actor, key, selected(f), revision, "Reviewed bulk")
        assertEquals("owned_write_failure", (result as? Result.Failed)?.failure?.code)
        assertEquals(2, writes)
        unchanged(f, key)
        assertTrue(
            writer(policy = security.copy(enforceMfa = false))
                .execute(f.actor, key, selected(f), revision, "Reviewed bulk") is Result.Success
        )
    }

    @Test
    fun pendingCancellationReleasesTheBatchLocksAndAllowsTheSameOperationToRetry() {
        val f = isolatedFixture()
        val revision = bulkReview(f)["revision"].asString()
        val key = UUID.randomUUID()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val changes =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord) =
                    journal.record(actor, change).also {
                        if (change.resourceType == "roster_bulk") {
                            entered.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                        }
                    }
            }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<*>> {
                    try {
                        writer(changes = changes, policy = security.copy(enforceMfa = false))
                            .execute(f.actor, key, selected(f), revision, "Reviewed bulk")
                    } finally {
                        finished.countDown()
                    }
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertTrue(finished.await(5, TimeUnit.SECONDS))
            } finally {
                release.countDown()
            }
        }
        unchanged(f, key)
        assertTrue(
            writer(policy = security.copy(enforceMfa = false))
                .execute(f.actor, key, selected(f), revision, "Reviewed bulk") is Result.Success
        )
    }

    @Test
    fun aReadCannotReleaseItsPreviewAfterAssuranceExpiresDuringAcquisition() {
        val f = isolatedFixture()
        val actor = enrolled(f)
        val calendar =
            object : ScheduleRepository by schedules {
                override fun calendar(
                    companyId: UUID,
                    employeeId: UUID,
                    from: LocalDate,
                    until: LocalDate,
                ) =
                    schedules.calendar(companyId, employeeId, from, until).also {
                        clock.set(clock.instant().plusSeconds(2))
                    }
            }
        val result = reader(calendar).execute(actor, selected(f))
        assertEquals("mfa_required", (result as? Result.Failed)?.failure?.code)
        unchanged(f, UUID.randomUUID())
    }

    @Test
    fun replacingCallerCollectionsDuringTheGuardCannotChangeTheReviewedTarget() {
        val f = isolatedFixture()
        val employeeIds = mutableListOf(f.employee)
        val dates = mutableListOf(LocalDate.parse(workDate), LocalDate.parse("2026-10-03"))
        val input = RosterBulkSelection(employeeIds, dates, RosterBulkMode.DAY_OFF)
        val revision = bulkReview(f)["revision"].asString()
        val calendar =
            object : ScheduleRepository by schedules {
                override fun lock(companyId: UUID, shared: Boolean) =
                    schedules.lock(companyId, shared).also {
                        employeeIds.clear()
                        dates.clear()
                    }
            }
        val result =
            writer(calendar, policy = security.copy(enforceMfa = false))
                .execute(f.actor, UUID.randomUUID(), input, revision, "Reviewed bulk")
        assertTrue(result is Result.Success)
        assertEquals(2, savedRows(f).size)
    }
}
