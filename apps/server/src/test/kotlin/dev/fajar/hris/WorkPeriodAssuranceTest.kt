package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(WorkPeriodProbeConfiguration::class)
class WorkPeriodAssuranceTest : WorkPeriodAssuranceApiFixture() {
    @Autowired private lateinit var probe: WorkPeriodProbe

    @AfterEach
    fun releaseProbes() {
        probe.current.getAndSet(null)?.release?.countDown()
        accountProbe.current.getAndSet(null)?.release?.countDown()
    }

    @ParameterizedTest
    @ValueSource(strings = ["close", "recover", "list", "snapshot", "overview", "employees"])
    fun expiryAtTheAccountGuardCannotAuthorizeResultsChangesOrReceipts(action: String) {
        val scene = prepare(action)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        assertEquals(
            "company_required",
            (invoke(scene, actor.copy(companyId = null)) as Result.Failed).failure.code,
        )
        val before = stored(scene)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { invoke(scene, actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                clock.set(clock.instant().plusSeconds(2))
                barrier.release.countDown()
                assertEquals(
                    "mfa_required",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertEquals(before, stored(scene))
        val renewed = actor.copy(mfaVerifiedAt = clock.instant())
        val accepted = invoke(scene, renewed)
        assertTrue(accepted is Result.Success, accepted.toString())
        val committed = stored(scene)
        assertEquals("mfa_required", (invoke(scene, actor) as Result.Failed).failure.code)
        assertEquals(committed, stored(scene))
        val value = (accepted as Result.Success).value
        val replay =
            if (value is MutationReceipt) Result.Success(value.copy(replayed = true)) else accepted
        assertEquals(replay, invoke(scene, renewed))
        assertEquals(committed, stored(scene))
    }

    @ParameterizedTest
    @ValueSource(strings = ["close", "recover", "list", "snapshot", "overview", "employees"])
    fun expiryAfterAcquisitionRejectsReadsAndRollsBackEveryClosingWrite(action: String) {
        val scene = prepare(action)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val before = stored(scene)
        val barrier = WorkPeriodProbe.Barrier(scene.fixture.company, point(action))
        probe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { invoke(scene, actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                clock.set(clock.instant().plusSeconds(2))
                barrier.release.countDown()
                assertEquals(
                    "mfa_required",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                barrier.release.countDown()
                probe.current.set(null)
            }
        }
        assertEquals(before, stored(scene))
        assertTrue(invoke(scene, actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success)
    }

    @ParameterizedTest
    @ValueSource(strings = ["close", "recover", "list", "snapshot", "overview", "employees"])
    fun cancellationReleasesOwnedResourcesAndTheOriginalCommandCanBeRetried(action: String) {
        val scene = prepare(action)
        val before = stored(scene)
        val barrier = WorkPeriodProbe.Barrier(scene.fixture.company, point(action))
        val finished = CountDownLatch(1)
        probe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<*>> {
                    try {
                        invoke(scene)
                    } finally {
                        finished.countDown()
                    }
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertTrue(finished.await(5, TimeUnit.SECONDS))
            } finally {
                barrier.release.countDown()
                probe.current.set(null)
            }
        }
        assertEquals(before, stored(scene))
        assertTrue(invoke(scene) is Result.Success)
    }

    private fun point(action: String) =
        when (action) {
            "close" -> "workforce.period_close_started"
            "recover" -> "workforce.period_recovered"
            else -> action
        }
}
