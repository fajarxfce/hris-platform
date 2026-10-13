package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.AttendanceRecord
import dev.fajar.hris.workforce.domain.entities.AttendanceStatus
import dev.fajar.hris.workforce.domain.repositories.AttendanceCorrectionRepository
import dev.fajar.hris.workforce.domain.repositories.AttendanceRepository
import dev.fajar.hris.workforce.domain.usecases.GetAttendanceDay
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AttendanceReadProbeConfiguration::class)
class AttendanceDayConsistencyTest : WorkforceAccessApiFixture() {
    @Autowired private lateinit var attendance: AttendanceRepository
    @Autowired private lateinit var corrections: AttendanceCorrectionRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var readProbe: AttendanceReadProbe
    private val security = IdentitySecurityPolicy()

    @ParameterizedTest
    @ValueSource(strings = ["guards", "facts"])
    fun expiredAssuranceCannotReleaseAPartiallyAcquiredDay(phase: String) {
        val f = isolatedFixture()
        capture(f)
        val id = recordId(f)
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
        if (phase == "guards")
            accountProbe.current.set(AccountLockProbe.Barrier(f.employeeAccount, entered, release))
        else readProbe.current.set(AttendanceReadProbe.Barrier(f.company, entered, release))
        val result =
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<AttendanceRecord>> { reader().execute(actor, id) }
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    clock.set(clock.instant().plusSeconds(2))
                    release.countDown()
                    pending.get(10, TimeUnit.SECONDS)
                } finally {
                    release.countDown()
                    accountProbe.current.set(null)
                    readProbe.current.set(null)
                }
            }
        assertEquals("mfa_required", (result as? Result.Failed)?.failure?.code)
        assertTrue(
            reader().execute(actor.copy(mfaVerifiedAt = clock.instant()), id) is Result.Success
        )
    }

    @Test
    fun reviewCommittedBeforeTheDayGuardIsReflectedInBothVersionAndFacts() {
        val f = isolatedFixture()
        val event = capture(f)
        val id = recordId(f)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val once = AtomicBoolean()
        val guarded =
            object : AttendanceRepository by attendance {
                override fun dayHead(companyId: UUID, id: UUID) =
                    attendance.dayHead(companyId, id).also {
                        if (once.compareAndSet(false, true)) {
                            entered.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                        }
                    }
            }
        val result =
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<Result<AttendanceRecord>> {
                        reader(guarded).execute(ownActor(f), id)
                    }
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    val reviewed = reject(f, event)
                    assertEquals(200, reviewed.statusCode(), reviewed.body())
                    release.countDown()
                    pending.get(10, TimeUnit.SECONDS)
                } finally {
                    release.countDown()
                }
            }
        val value = (result as Result.Success).value
        assertEquals(1L, value.version)
        assertEquals(AttendanceStatus.REJECTED, value.day.entries.single().status)
    }

    @Test
    fun interruptedReadReleasesItsTransactionAndDayGuard() {
        val f = isolatedFixture()
        capture(f)
        val id = recordId(f)
        val barrier = AttendanceReadProbe.Barrier(f.company)
        readProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<AttendanceRecord>> { reader().execute(ownActor(f), id) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertThrows(CancellationException::class.java) {
                    pending.get(10, TimeUnit.SECONDS)
                }
            } finally {
                barrier.release.countDown()
                readProbe.current.set(null)
            }
        }
        assertTrue(reader().execute(ownActor(f), id) is Result.Success)
        val another = isolatedFixture()
        assertEquals(
            "attendance_day_not_found",
            (reader().execute(ownActor(another), id) as Result.Failed).failure.code,
        )
    }

    private fun ownActor(f: Fixture) =
        f.actor.copy(accountId = f.employeeAccount, permissions = setOf("attendance.self.record"))

    private fun recordId(f: Fixture): UUID =
        requireNotNull(
            database()
                .queryForObject(
                    "select id from attendance_sync_heads where company_id=? and employment_id=?",
                    UUID::class.java,
                    f.company,
                    f.employee,
                )
        )

    private fun reader(source: AttendanceRepository = attendance) =
        GetAttendanceDay(
            source,
            corrections,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )
}
