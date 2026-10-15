package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.LeaveCalendarRepository
import dev.fajar.hris.leave.domain.usecases.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(LeaveCalendarProbeConfiguration::class, PeopleLockProbeConfiguration::class)
class LeaveCalendarAssuranceTest : LeaveAttachmentApiFixture() {
    @Autowired private lateinit var calendar: LeaveCalendarRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var probe: LeaveCalendarProbe
    @Autowired private lateinit var peopleProbe: PeopleLockProbe
    private val security = IdentitySecurityPolicy(enforceMfa = true)

    @AfterEach
    fun clearCalendarProbes() {
        probe.current.getAndSet(null)?.release?.countDown()
        peopleProbe.current.getAndSet(null)?.release?.countDown()
    }

    @ParameterizedTest
    @CsvSource("month,false", "month,true", "entries,false", "entries,true")
    fun assuranceIsRecheckedAfterWaitingAndAfterAcquiringPrivateData(
        kind: String,
        acquired: Boolean,
    ) {
        val f = leaveFixture()
        val actor =
            reader(f)
                .copy(mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1))
        val account = AccountLockProbe.Barrier(actor.accountId)
        val data = LeaveCalendarProbe.Barrier(f.company)
        if (acquired) probe.current.set(data) else accountProbe.current.set(account)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { read(kind, actor) }
            try {
                assertTrue(
                    (if (acquired) data.entered else account.entered).await(5, TimeUnit.SECONDS)
                )
                clock.set(clock.instant().plusSeconds(2))
                account.release.countDown()
                data.release.countDown()
                assertEquals(
                    "mfa_required",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                account.release.countDown()
                data.release.countDown()
                accountProbe.current.set(null)
                probe.current.set(null)
            }
        }
        assertTrue(read(kind, actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success)
    }

    @ParameterizedTest
    @ValueSource(strings = ["month", "entries"])
    fun neitherNewGrantsNorRevokedOriginalGrantsAuthorizePendingReads(kind: String) {
        val f = leaveFixture()
        val actor = reader(f)
        assertEquals(
            "access_denied",
            (read(kind, actor.copy(permissions = emptySet())) as Result.Failed).failure.code,
        )
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { read(kind, actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='leave.read'",
                        f.company,
                        actor.accountId,
                    )
                barrier.release.countDown()
                assertEquals(
                    "access_denied",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["month", "entries"])
    fun revokedCredentialsCannotSurviveAdmission(kind: String) {
        val f = leaveFixture()
        val actor = reader(f)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { read(kind, actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "update accounts set security_version=security_version+1 where id=?",
                        actor.accountId,
                    )
                barrier.release.countDown()
                assertEquals(
                    "session_revoked",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["month", "entries"])
    fun cancellationUnwindsTheTransactionAndReleasesEveryGuard(kind: String) {
        val f = leaveFixture()
        val actor = reader(f)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { read(kind, actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertThrows(CancellationException::class.java) {
                    pending.get(10, TimeUnit.SECONDS)
                }
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
            assertTrue(
                pool.submit<Result<*>> { read(kind, actor) }.get(10, TimeUnit.SECONDS)
                    is Result.Success
            )
            assertTrue(
                pool
                    .submit<Result<*>> {
                        transactions.run(actor) {
                            people
                                .lockReportingLines(f.company)
                                .flatMap { companies.lock(f.company) }
                                .flatMap { members.lock(f.company) }
                                .flatMap { identities.lockAccount(actor.accountId) }
                        }
                    }
                    .get(10, TimeUnit.SECONDS) is Result.Success
            )
        }
    }

    @Test
    fun reportingLineChangesDuringAdmissionCannotLeakFormerTeamDates() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "2"))
        body(submit(f, UUID.randomUUID(), listOf("2026-10-05" to "FULL")))
        val actor =
            Actor(
                f.managerAccount,
                f.company,
                setOf("leave.team.read"),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val barrier = PeopleLockProbe.Barrier(f.company)
        peopleProbe.current.set(barrier)
        val get =
            GetLeaveCalendar(
                calendar,
                people,
                companies,
                members,
                identities,
                transactions,
                IdentitySecurityPolicy(enforceMfa = false),
                clock,
            )
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<LeaveCalendar>> {
                    get.execute(actor, LeaveCalendarScope.TEAM, YearMonth.of(2026, 10))
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                body(
                    revise(
                        f.admin,
                        f.adminCsrf,
                        f.company,
                        f.employee,
                        0,
                        terms(from = "2026-10-01", manager = null, start = "2025-01-01"),
                    )
                )
                barrier.release.countDown()
                assertEquals(
                    emptyList<LeaveCalendarDay>(),
                    (pending.get(10, TimeUnit.SECONDS) as Result.Success).value.days,
                )
            } finally {
                barrier.release.countDown()
                peopleProbe.current.set(null)
            }
        }
    }

    private fun reader(f: LeaveFixture): Actor {
        val (account, _) = member(f, listOf("leave.read"))
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                account,
            )
        return Actor(
            account,
            f.company,
            setOf("leave.read"),
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = 0,
            mfaVerifiedAt = clock.instant(),
        )
    }

    private fun read(kind: String, actor: Actor): Result<*> =
        when (kind) {
            "month" ->
                GetLeaveCalendar(
                        calendar,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        security,
                        clock,
                    )
                    .execute(actor, LeaveCalendarScope.COMPANY, YearMonth.of(2026, 10))
            "entries" ->
                ListLeaveCalendarEntries(
                        calendar,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        security,
                        clock,
                    )
                    .execute(actor, LeaveCalendarScope.COMPANY, LocalDate.of(2026, 10, 5), null, 20)
            else -> error("Unknown calendar read")
        }
}
