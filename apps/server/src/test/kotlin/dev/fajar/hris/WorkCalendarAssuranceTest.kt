package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
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
                "holidays",
                "calendar",
                "save-shift",
                "save-holiday",
                "weekly",
                "roster",
            ]
    )
    fun expiryDuringAnAccountWaitCannotAuthorizeCalendarReadsWritesOrReceiptReplay(mode: String) {
        val f = isolatedFixture()
        val shift = createShift(f)
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
        fun invoke(current: Actor): Result<*> =
            when (mode) {
                "shifts" ->
                    ListShifts(
                            schedules,
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
                            schedules,
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
                            schedules,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(current, day.minusMonths(1), day)
                "calendar" ->
                    GetEmployeeCalendar(
                            schedules,
                            people,
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
                            schedules,
                            operations,
                            journal,
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
                            schedules,
                            operations,
                            journal,
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
                            schedules,
                            people,
                            operations,
                            journal,
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
                            schedules,
                            people,
                            operations,
                            journal,
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
        val barrier = AccountLockProbe.Barrier(account)
        accountProbe.current.set(barrier)
        val result =
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<*>> { invoke(actor) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    clock.set(clock.instant().plusSeconds(2))
                    barrier.release.countDown()
                    pending.get(10, TimeUnit.SECONDS)
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
        assertEquals("mfa_required", (result as? Result.Failed)?.failure?.code, mode)
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
}
