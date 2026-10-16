package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import dev.fajar.hris.workforce.domain.repositories.WorkPeriodRepository
import dev.fajar.hris.workforce.domain.usecases.GetEmployeeCalendarReview
import dev.fajar.hris.workforce.domain.usecases.ResetRosterDay
import dev.fajar.hris.workforce.domain.usecases.SetRosterDay
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

class RosterResetCompletionTest : RosterResetApiFixture() {
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

    private fun resetter(
        calendar: ScheduleRepository = schedules,
        changes: ChangeJournalRepository = journal,
        receipts: OperationRepository = operations,
        policy: IdentitySecurityPolicy = security,
    ) =
        ResetRosterDay(
            periods,
            calendar,
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
        assertEquals(0L, row(f)["version"])
        assertEquals(true, row(f)["active"])
        assertEquals(1, revisions(f).size)
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where company_id=? and operation_id=?",
                    Int::class.java,
                    f.company,
                    key,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where company_id=? and action='workforce.roster_reset'",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from mobile_sync_changes where company_id=? and collection='ROSTER_DAYS' and operation='DELETE'",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["guard", "calendar", "journal", "receipt"])
    fun mfaMustRemainValidAfterWaitsAndThroughTheLastWrite(phase: String) {
        val f = isolatedFixture()
        body(roster(f))
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
        val outcome =
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<Result<*>> {
                        resetter(calendar, changes, receipts)
                            .execute(
                                actor,
                                key,
                                f.employee,
                                LocalDate.parse(workDate),
                                0,
                                "Reviewed reset",
                            )
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
        assertEquals("mfa_required", (outcome as? Result.Failed)?.failure?.code)
        unchanged(f, key)
        assertTrue(
            resetter()
                .execute(
                    actor.copy(mfaVerifiedAt = clock.instant()),
                    key,
                    f.employee,
                    LocalDate.parse(workDate),
                    0,
                    "Reviewed reset",
                ) is Result.Success
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["permission", "membership", "account", "credential"])
    fun authorityWithdrawnDuringTheAccountWaitCannotResetAReviewedDay(mode: String) {
        val f = isolatedFixture()
        body(roster(f))
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
                reset(f, operation = key)
            }
        assertTrue(response.statusCode() in setOf(401, 403), response.body())
        unchanged(f, key)
    }

    @ParameterizedTest
    @ValueSource(strings = ["failure", "interrupt"])
    fun journalingFailureRollsBackTheResetHistoryReceiptAndSyncEvent(mode: String) {
        val f = isolatedFixture()
        body(roster(f))
        val key = UUID.randomUUID()
        val changes =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                    journal.record(actor, change).flatMap {
                        if (mode == "interrupt")
                            throw InterruptedException("Owned roster cancellation")
                        Result.Failed(Failure(FailureKind.CONFLICT, "owned_journal_failure"))
                    }
            }
        val execute = {
            resetter(changes = changes, policy = security.copy(enforceMfa = false))
                .execute(f.actor, key, f.employee, LocalDate.parse(workDate), 0, "Reviewed reset")
        }
        if (mode == "interrupt") assertThrows(InterruptedException::class.java) { execute() }
        else assertEquals("owned_journal_failure", (execute() as? Result.Failed)?.failure?.code)
        unchanged(f, key)
        assertTrue(
            resetter(policy = security.copy(enforceMfa = false))
                .execute(f.actor, key, f.employee, LocalDate.parse(workDate), 0, "Reviewed reset")
                is Result.Success
        )
    }

    @Test
    fun cancellationDuringJournalingReleasesTheCalendarLockAndAllowsOriginalRetry() {
        val f = isolatedFixture()
        body(roster(f))
        val key = UUID.randomUUID()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val changes =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord) =
                    journal.record(actor, change).also {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                    }
            }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<*>> {
                    try {
                        resetter(changes = changes, policy = security.copy(enforceMfa = false))
                            .execute(
                                f.actor,
                                key,
                                f.employee,
                                LocalDate.parse(workDate),
                                0,
                                "Reviewed reset",
                            )
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
            resetter(policy = security.copy(enforceMfa = false))
                .execute(f.actor, key, f.employee, LocalDate.parse(workDate), 0, "Reviewed reset")
                is Result.Success
        )
    }

    @Test
    fun reviewCannotReleaseRosterMetadataAfterAssuranceExpiresDuringAcquisition() {
        val f = isolatedFixture()
        body(roster(f))
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
        val result =
            GetEmployeeCalendarReview(
                    calendar,
                    people,
                    companies,
                    members,
                    identities,
                    transactions,
                    security,
                    clock,
                )
                .execute(actor, f.employee, LocalDate.parse(workDate), LocalDate.parse(workDate))
        assertEquals("mfa_required", (result as? Result.Failed)?.failure?.code)
    }

    @Test
    fun rosterReactivationRetainsAssuranceThroughJournaling() {
        val f = isolatedFixture()
        body(roster(f))
        body(reset(f))
        val actor = enrolled(f)
        val key = UUID.randomUUID()
        val changes =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord) =
                    journal.record(actor, change).also { clock.set(clock.instant().plusSeconds(2)) }
            }
        val result =
            SetRosterDay(
                    periods,
                    schedules,
                    people,
                    operations,
                    changes,
                    companies,
                    members,
                    identities,
                    transactions,
                    security,
                    clock,
                )
                .execute(actor, key, f.employee, LocalDate.parse(workDate), null, 1, "New override")
        assertEquals("mfa_required", (result as? Result.Failed)?.failure?.code)
        assertEquals(false, row(f)["active"])
        assertEquals(1L, row(f)["version"])
        assertEquals(2, revisions(f).size)
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where company_id=? and operation_id=?",
                    Int::class.java,
                    f.company,
                    key,
                ),
        )
    }
}
