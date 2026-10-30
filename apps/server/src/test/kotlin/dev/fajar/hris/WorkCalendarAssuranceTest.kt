package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.repositories.*
import dev.fajar.hris.workforce.domain.usecases.*
import java.time.*
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class WorkCalendarAssuranceTest : WorkforceAccessApiFixture() {
    @Autowired private lateinit var schedules: ScheduleRepository
    @Autowired private lateinit var periods: WorkPeriodRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "shifts",
                "shift-detail",
                "self-directory",
                "employee-directory",
                "calendar-review",
                "holidays",
                "holiday-detail",
                "calendar",
                "save-shift",
                "save-holiday",
                "weekly",
                "roster",
            ]
    )
    fun expiryDuringAnAccountWaitCannotAuthorizeCalendarReadsWritesOrReceiptReplay(mode: String) =
        exerciseCompletion(mode, false)

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "shifts",
                "shift-detail",
                "self-directory",
                "employee-directory",
                "calendar-review",
                "holidays",
                "holiday-detail",
                "calendar",
                "save-shift",
                "save-holiday",
                "weekly",
                "roster",
            ]
    )
    fun expiryAfterAcquisitionCannotAuthorizeCalendarDataOrCommands(mode: String) =
        exerciseCompletion(mode, true)

    private fun exerciseCompletion(mode: String, completion: Boolean) {
        val f = isolatedFixture()
        val shift = createShift(f)
        val holiday = if (mode == "holiday-detail") createHoliday(f) else null
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash,mfa_secret_encrypted) select ?,?,'Calendar assurance fixture',password_hash,'fixture-enrolled' from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                f.company,
                account,
            )
        val permissions = setOf("company.read", "workforce.read", "workforce.manage")
        for (permission in permissions) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                f.company,
                account,
                permission,
            )
        val security = IdentitySecurityPolicy()
        val actor =
            Actor(
                account,
                f.company,
                permissions,
                clock.instant(),
                UUID.randomUUID(),
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1),
                credentialVersion = 0,
            )
        val key = UUID.randomUUID()
        val id = UUID.randomUUID()
        val day = LocalDate.parse("2026-10-01")
        val details =
            ShiftDetails(
                "A${id.toString().take(8).uppercase()}",
                "Assurance shift",
                LocalTime.of(8, 0),
                LocalTime.of(17, 0),
                60,
                "Asia/Jakarta",
                WorkMode.REMOTE,
                false,
                100.0,
                null,
            )
        var expire = completion
        var advanced = false
        fun advance() {
            if (expire && !advanced) {
                clock.set(clock.instant().plusSeconds(2))
                advanced = true
            }
        }
        fun advanceRead() {
            if (mode !in setOf("save-shift", "save-holiday", "weekly", "roster")) advance()
        }
        val observedSchedules =
            object : ScheduleRepository by schedules {
                override fun findShift(companyId: UUID, id: UUID) =
                    schedules.findShift(companyId, id).also { advanceRead() }

                override fun findHoliday(companyId: UUID, id: UUID) =
                    schedules.findHoliday(companyId, id).also { advanceRead() }

                override fun shifts(companyId: UUID, query: String, after: String?, limit: Int) =
                    schedules.shifts(companyId, query, after, limit).also { advanceRead() }

                override fun holidays(companyId: UUID, from: LocalDate, until: LocalDate) =
                    schedules.holidays(companyId, from, until).also { advanceRead() }

                override fun calendar(
                    companyId: UUID,
                    employeeId: UUID,
                    from: LocalDate,
                    until: LocalDate,
                ) = schedules.calendar(companyId, employeeId, from, until).also { advanceRead() }
            }
        val observedPeople =
            object : PeopleRepository by people {
                override fun list(
                    companyId: UUID,
                    accountId: UUID,
                    visibility: EmployeeVisibility,
                    asOf: LocalDate,
                    accessAt: Instant,
                    query: String,
                    after: String?,
                    limit: Int,
                ): Result<Page<Employee>> =
                    people
                        .list(companyId, accountId, visibility, asOf, accessAt, query, after, limit)
                        .also { advanceRead() }
            }
        val observedJournal =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord) =
                    journal.record(actor, change).also { advance() }
            }
        fun snapshot() =
            listOf(
                    "shift_templates",
                    "shift_revisions",
                    "schedule_versions",
                    "schedule_assignments",
                    "roster_days",
                    "roster_revisions",
                    "work_holidays",
                    "holiday_revisions",
                    "work_periods",
                    "operation_receipts",
                    "audit_entries",
                    "outbox_events",
                    "mobile_sync_changes",
                )
                .associateWith { table ->
                    database()
                        .queryForList(
                            "select to_jsonb(t)::text from $table t where company_id=? order by 1",
                            String::class.java,
                            f.company,
                        )
                }
        fun invoke(current: Actor): Result<*> =
            when (mode) {
                "employee-directory" ->
                    ListWorkforceEmployees(
                            observedPeople,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(current, day, "", null, 50)
                "calendar-review" ->
                    GetEmployeeCalendarReview(
                            observedSchedules,
                            observedPeople,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(current, f.employee, day, day)
                "self-directory" ->
                    ListSelfWorkforceEmployments(
                            observedPeople,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(current, null, 20)
                "shifts" ->
                    ListShifts(
                            observedSchedules,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(current, "", null, 50)
                "shift-detail" ->
                    GetShift(
                            observedSchedules,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(current, shift)
                "holidays" ->
                    ListWorkHolidays(
                            observedSchedules,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(current, day.minusMonths(1), day)
                "holiday-detail" ->
                    GetWorkHoliday(
                            observedSchedules,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(current, requireNotNull(holiday))
                "calendar" ->
                    GetEmployeeCalendar(
                            observedSchedules,
                            observedPeople,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(current, f.employee, day, day)
                "save-shift" ->
                    SaveShift(
                            observedSchedules,
                            operations,
                            observedJournal,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(current, key, id, details, true, null, "Calendar assurance")
                "save-holiday" ->
                    SaveWorkHoliday(
                            periods,
                            observedSchedules,
                            operations,
                            observedJournal,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(
                            current,
                            key,
                            WorkHoliday(id, day.plusDays(1), "Assurance holiday", true, 0),
                            null,
                            "Calendar assurance",
                        )
                "weekly" ->
                    AssignWeeklySchedule(
                            periods,
                            observedSchedules,
                            observedPeople,
                            operations,
                            observedJournal,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(
                            current,
                            key,
                            f.employee,
                            day,
                            mapOf(DayOfWeek.THURSDAY to ShiftReference(shift, 0)),
                            null,
                            "Calendar assurance",
                        )
                else ->
                    SetRosterDay(
                            periods,
                            observedSchedules,
                            observedPeople,
                            operations,
                            observedJournal,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(
                            current,
                            key,
                            f.employee,
                            day,
                            ShiftReference(shift, 0),
                            null,
                            "Calendar assurance",
                        )
            }
        val before = snapshot()
        val barrier = AccountLockProbe.Barrier(account)
        if (!completion) accountProbe.current.set(barrier)
        val result =
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<*>> { invoke(actor) }
                try {
                    if (!completion) {
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
        expire = false
        if (completion)
            assertTrue(advanced, "The selected acquisition/completion boundary was not reached")
        assertEquals("mfa_required", (result as? Result.Failed)?.failure?.code, mode)
        assertEquals(before, snapshot())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where operation_id=?",
                    Int::class.java,
                    key,
                ),
        )
        val renewed = actor.copy(mfaVerifiedAt = clock.instant())
        val accepted = invoke(renewed)
        assertTrue(accepted is Result.Success, "$mode $accepted")
        if (mode in setOf("save-shift", "save-holiday", "weekly", "roster")) {
            val first = (accepted as Result.Success).value as MutationReceipt
            val replay = invoke(renewed)
            assertEquals(first.copy(replayed = true), (replay as Result.Success).value)
            val expired =
                renewed.copy(
                    mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).minusSeconds(1)
                )
            assertEquals("mfa_required", (invoke(expired) as Result.Failed).failure.code)
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
    }

    @org.junit.jupiter.api.Test
    fun admittedWeeklyDaysRemainTheSameAcrossAnAccountWait() {
        val f = isolatedFixture()
        val shift = createShift(f)
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.actor.accountId,
            )
        val actor = f.actor.copy(mfaVerifiedAt = clock.instant())
        val day = LocalDate.parse("2026-10-01")
        val admitted = mapOf(DayOfWeek.THURSDAY to ShiftReference(shift, 0))
        val input = admitted.toMutableMap()
        val key = UUID.randomUUID()
        val command =
            AssignWeeklySchedule(
                periods,
                schedules,
                people,
                operations,
                journal,
                companies,
                members,
                identities,
                transactions,
                IdentitySecurityPolicy(),
                clock,
            )
        fun invoke(days: Map<DayOfWeek, ShiftReference>) =
            command.execute(actor, key, f.employee, day, days, null, "Weekly assignment")
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        val accepted =
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<MutationReceipt>> { invoke(input) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    input.clear()
                    barrier.release.countDown()
                    pending.get(10, TimeUnit.SECONDS)
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
        assertTrue(accepted is Result.Success, accepted.toString())
        val stored = transactions.run(actor) { schedules.calendar(f.company, f.employee, day, day) }
        assertTrue(stored is Result.Success, stored.toString())
        val assignment = (stored as Result.Success).value.assignments.single()
        assertEquals(
            admitted,
            assignment.days.mapValues { ShiftReference(it.value.id, it.value.revision) },
        )
        val before =
            database()
                .queryForList(
                    "select to_jsonb(t)::text from schedule_assignments t where company_id=? order by 1",
                    String::class.java,
                    f.company,
                )
        assertEquals(
            (accepted as Result.Success).value.copy(replayed = true),
            (invoke(admitted) as Result.Success).value,
        )
        assertEquals(
            before,
            database()
                .queryForList(
                    "select to_jsonb(t)::text from schedule_assignments t where company_id=? order by 1",
                    String::class.java,
                    f.company,
                ),
        )
        assertEquals(
            "operation_payload_mismatch",
            (invoke(emptyMap()) as Result.Failed).failure.code,
        )
    }
}
