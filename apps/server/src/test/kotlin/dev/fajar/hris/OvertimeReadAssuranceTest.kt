package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.entities.OvertimeRequestDetails
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(OvertimeReadProbeConfiguration::class)
class OvertimeReadAssuranceTest : OvertimeAssuranceApiFixture() {
    @Autowired private lateinit var readProbe: OvertimeReadProbe

    @ParameterizedTest
    @ValueSource(strings = ["details", "list"])
    fun expiryAfterAcquisitionCannotReleasePrivateData(action: String) {
        val scene = prepare(action)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val barrier = OvertimeReadProbe.Barrier(scene.fixture.company, action)
        readProbe.current.set(barrier)
        val result =
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<*>> { invoke(scene, actor) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    clock.set(clock.instant().plusSeconds(2))
                    barrier.release.countDown()
                    pending.get(10, TimeUnit.SECONDS)
                } finally {
                    barrier.release.countDown()
                    readProbe.current.set(null)
                }
            }
        assertEquals("mfa_required", (result as Result.Failed).failure.code)
        val recovered = invoke(scene, actor.copy(mfaVerifiedAt = clock.instant()))
        assertTrue(recovered is Result.Success, recovered.toString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["details", "list"])
    fun cancellationReleasesReadOwnershipAndDoesNotPoisonTheNextTransaction(action: String) {
        val scene = prepare(action)
        val barrier = OvertimeReadProbe.Barrier(scene.fixture.company, action)
        readProbe.current.set(barrier)
        Executors.newFixedThreadPool(2).use { pool ->
            val pending = pool.submit<Result<*>> { invoke(scene) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                barrier.release.countDown()
                val recovered = pool.submit<Result<*>> { invoke(scene) }.get(10, TimeUnit.SECONDS)
                assertTrue(recovered is Result.Success, recovered.toString())
            } finally {
                barrier.release.countDown()
                readProbe.current.set(null)
            }
        }
    }

    @Test
    fun independentDetailReadersShareGuardsAndRetainCoherentHistory() {
        val scene = prepare("details")
        val barrier = OvertimeReadProbe.Barrier(scene.fixture.company, "details")
        readProbe.current.set(barrier)
        Executors.newFixedThreadPool(2).use { pool ->
            val pending = pool.submit<Result<*>> { invoke(scene) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val concurrent = pool.submit<Result<*>> { invoke(scene) }.get(4, TimeUnit.SECONDS)
                assertTrue(concurrent is Result.Success, concurrent.toString())
                val view = (concurrent as Result.Success).value as OvertimeRequestDetails
                assertEquals(1L, view.request.version)
                assertNotNull(view.approval)
                assertEquals(2, view.history.items.size)
                assertEquals(
                    false,
                    database()
                        .queryForObject(
                            "select pg_try_advisory_xact_lock(hashtextextended(?,0))",
                            Boolean::class.java,
                            "overtime:${scene.fixture.company}:${scene.fixture.employee}",
                        ),
                )
                barrier.release.countDown()
                assertEquals(concurrent, pending.get(10, TimeUnit.SECONDS))
            } finally {
                barrier.release.countDown()
                readProbe.current.set(null)
            }
        }
    }
}
