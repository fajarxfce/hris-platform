package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import dev.fajar.hris.workforce.domain.usecases.*
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class CalendarSourceConsistencyTest : WorkforceAccessApiFixture() {
    @Autowired private lateinit var schedules: ScheduleRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner
    private val security = IdentitySecurityPolicy()

    @ParameterizedTest
    @CsvSource(
        "schedule,guard",
        "schedule,read",
        "roster,guard",
        "roster,read",
        "holiday,guard",
        "holiday,read",
    )
    fun lateAssuranceExpiryCannotReleaseAnyCanonicalCalendarSource(kind: String, phase: String) {
        val (f, id) = prepare(kind)
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.employeeAccount,
            )
        val actor =
            ownActor(f)
                .copy(mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        if (phase == "guard")
            accountProbe.current.set(AccountLockProbe.Barrier(f.employeeAccount, entered, release))
        val repository = if (phase == "read") afterRead(entered, release) else schedules
        val result =
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<*>> { read(kind, actor, id, repository) }
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    clock.set(clock.instant().plusSeconds(2))
                    release.countDown()
                    pending.get(10, TimeUnit.SECONDS)
                } finally {
                    release.countDown()
                    accountProbe.current.set(null)
                }
            }
        assertEquals("mfa_required", (result as? Result.Failed)?.failure?.code)
        assertTrue(read(kind, actor.copy(mfaVerifiedAt = clock.instant()), id) is Result.Success)
    }

    @ParameterizedTest
    @ValueSource(strings = ["schedule", "roster", "holiday"])
    fun cancellationReleasesCalendarAndAccessGuards(kind: String) {
        val (f, id) = prepare(kind)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<*>> { read(kind, ownActor(f), id, afterRead(entered, release)) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertThrows(CancellationException::class.java) {
                    pending.get(10, TimeUnit.SECONDS)
                }
            } finally {
                release.countDown()
            }
        }
        assertTrue(read(kind, ownActor(f), id) is Result.Success)
        val other = isolatedFixture()
        assertEquals(
            FailureKind.NOT_FOUND,
            (read(kind, ownActor(other), id) as Result.Failed).failure.kind,
        )
    }

    private fun prepare(kind: String): Pair<Fixture, UUID> {
        val f = isolatedFixture()
        val id =
            when (kind) {
                "schedule" -> {
                    val result =
                        command(
                            f.admin,
                            "${f.path}/employees/${f.employee}/schedule",
                            """{"effectiveFrom":"2026-10-01","days":{},"reason":"Calendar setup"}""",
                            f.csrf,
                            UUID.randomUUID(),
                            "PUT",
                        )
                    assertEquals(200, result.statusCode(), result.body())
                    requireNotNull(
                        database()
                            .queryForObject(
                                "select id from work_schedule_sync_heads where company_id=? and employment_id=?",
                                UUID::class.java,
                                f.company,
                                f.employee,
                            )
                    )
                }
                "roster" -> {
                    val result =
                        command(
                            f.admin,
                            "${f.path}/employees/${f.employee}/roster/2026-10-01",
                            """{"reason":"Calendar setup"}""",
                            f.csrf,
                            UUID.randomUUID(),
                            "PUT",
                        )
                    assertEquals(200, result.statusCode(), result.body())
                    requireNotNull(
                        database()
                            .queryForObject(
                                "select id from roster_days where company_id=? and employment_id=?",
                                UUID::class.java,
                                f.company,
                                f.employee,
                            )
                    )
                }
                else -> f.holiday
            }
        return f to id
    }

    private fun ownActor(f: Fixture) =
        f.actor.copy(accountId = f.employeeAccount, permissions = setOf("attendance.self.record"))

    private fun afterRead(entered: CountDownLatch, release: CountDownLatch): ScheduleRepository =
        object : ScheduleRepository by schedules {
            private fun acquired() {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }

            override fun workSchedule(companyId: UUID, id: UUID) =
                schedules.workSchedule(companyId, id).also { acquired() }

            override fun rosterDay(companyId: UUID, id: UUID) =
                schedules.rosterDay(companyId, id).also { acquired() }

            override fun findHoliday(companyId: UUID, id: UUID) =
                schedules.findHoliday(companyId, id).also { acquired() }
        }

    private fun read(
        kind: String,
        actor: Actor,
        id: UUID,
        repository: ScheduleRepository = schedules,
    ): Result<*> =
        when (kind) {
            "schedule" ->
                GetOwnedWorkSchedule(
                        repository,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        security,
                        clock,
                    )
                    .execute(actor, id)
            "roster" ->
                GetOwnedRosterDay(
                        repository,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        security,
                        clock,
                    )
                    .execute(actor, id)
            else ->
                GetOwnedWorkHoliday(
                        repository,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        security,
                        clock,
                    )
                    .execute(actor, id)
        }
}
