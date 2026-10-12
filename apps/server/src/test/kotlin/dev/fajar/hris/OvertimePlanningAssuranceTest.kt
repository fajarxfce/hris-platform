package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import dev.fajar.hris.workforce.domain.repositories.WorkPeriodRepository
import dev.fajar.hris.workforce.domain.usecases.GetOvertimePlanningReview
import dev.fajar.hris.workforce.domain.usecases.ListOvertimePlanningEmployees
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(OvertimePlanningProbeConfiguration::class)
class OvertimePlanningAssuranceTest : OvertimeAssuranceApiFixture() {
    @Autowired private lateinit var planningProbe: OvertimePlanningProbe
    @Autowired private lateinit var schedules: ScheduleRepository
    @Autowired private lateinit var periods: WorkPeriodRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository

    @AfterEach
    fun clearPlanningProbe() {
        planningProbe.current.getAndSet(null)?.release?.countDown()
    }

    private fun read(f: Fixture, actor: Actor, review: Boolean): Result<*> =
        if (review)
            GetOvertimePlanningReview(
                    schedules,
                    periods,
                    people,
                    companies,
                    members,
                    identities,
                    transactions,
                    security,
                    clock,
                )
                .execute(actor, f.employee, LocalDate.parse("2026-09-01"))
        else
            ListOvertimePlanningEmployees(
                    people,
                    companies,
                    members,
                    identities,
                    transactions,
                    security,
                    clock,
                )
                .execute(actor, LocalDate.parse("2026-09-01"), "", null, 50)

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun expiredAssuranceAfterAcquisitionDoesNotReleasePrivateResults(review: Boolean) {
        val scene = prepare("list")
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val barrier = OvertimePlanningProbe.Barrier(scene.fixture.company, review)
        planningProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { read(scene.fixture, actor, review) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                clock.set(clock.instant().plusSeconds(2))
                barrier.release.countDown()
                val result = pending.get(10, TimeUnit.SECONDS)
                assertEquals("mfa_required", (result as Result.Failed).failure.code)
            } finally {
                barrier.release.countDown()
                planningProbe.current.set(null)
            }
        }
        val recovered = read(scene.fixture, actor.copy(mfaVerifiedAt = clock.instant()), review)
        assertTrue(recovered is Result.Success, recovered.toString())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun parallelReadersShareOwnershipAndCancellationReleasesItForWriters(review: Boolean) {
        val scene = prepare("list")
        val barrier = OvertimePlanningProbe.Barrier(scene.fixture.company, review)
        val finished = CountDownLatch(1)
        planningProbe.current.set(barrier)
        val key = "${if (review) "calendar" else "people"}:${scene.fixture.company}"
        Executors.newFixedThreadPool(2).use { pool ->
            val pending =
                pool.submit<Result<*>> {
                    try {
                        read(scene.fixture, scene.actor, review)
                    } finally {
                        finished.countDown()
                    }
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val parallel =
                    pool
                        .submit<Result<*>> { read(scene.fixture, scene.actor, review) }
                        .get(4, TimeUnit.SECONDS)
                assertTrue(parallel is Result.Success, parallel.toString())
                assertEquals(
                    false,
                    database()
                        .queryForObject(
                            "select pg_try_advisory_xact_lock(hashtextextended(?,0))",
                            Boolean::class.java,
                            key,
                        ),
                )
                assertTrue(pending.cancel(true))
                assertTrue(finished.await(5, TimeUnit.SECONDS))
                assertEquals(
                    true,
                    database()
                        .queryForObject(
                            "select pg_try_advisory_xact_lock(hashtextextended(?,0))",
                            Boolean::class.java,
                            key,
                        ),
                )
            } finally {
                barrier.release.countDown()
                planningProbe.current.set(null)
            }
        }
        assertTrue(read(scene.fixture, scene.actor, review) is Result.Success)
    }
}
