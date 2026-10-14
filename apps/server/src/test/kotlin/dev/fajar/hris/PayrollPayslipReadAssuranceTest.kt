package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(PayrollPayslipReadProbeConfiguration::class)
class PayrollPayslipReadAssuranceTest : PayrollPayslipAssuranceApiFixture() {
    @Autowired private lateinit var readProbe: PayrollPayslipReadProbe

    @ParameterizedTest
    @ValueSource(strings = ["list", "search", "details", "payments"])
    fun expiryDuringAccessAcquisitionCannotAuthorizeAPayslipRead(action: String) {
        val scene = prepare()
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { invoke(scene, action, actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                clock.set(clock.instant().plusSeconds(2))
                barrier.release.countDown()
                val result = pending.get(10, TimeUnit.SECONDS)
                assertEquals("mfa_required", (result as Result.Failed).failure.code)
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertTrue(
            invoke(scene, action, actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["list", "search", "details", "payments"])
    fun expiryAfterDataAcquisitionCannotReleasePrivateResults(action: String) {
        val scene = prepare()
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val barrier = PayrollPayslipReadProbe.Barrier(scene.company, action)
        readProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { invoke(scene, action, actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                clock.set(clock.instant().plusSeconds(2))
                barrier.release.countDown()
                val result = pending.get(10, TimeUnit.SECONDS)
                assertEquals("mfa_required", (result as Result.Failed).failure.code)
            } finally {
                barrier.release.countDown()
                readProbe.current.set(null)
            }
        }
        assertTrue(
            invoke(scene, action, actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["list", "search", "details", "payments"])
    fun interruptedReadReleasesItsGuardsAndTheNextReadSucceeds(action: String) {
        val scene = prepare()
        val barrier = PayrollPayslipReadProbe.Barrier(scene.company, action)
        readProbe.current.set(barrier)
        Executors.newFixedThreadPool(2).use { pool ->
            val pending = pool.submit<Result<*>> { invoke(scene, action) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                barrier.release.countDown()
                val recovered =
                    pool.submit<Result<*>> { invoke(scene, action) }.get(10, TimeUnit.SECONDS)
                assertTrue(recovered is Result.Success, recovered.toString())
            } finally {
                barrier.release.countDown()
                readProbe.current.set(null)
            }
        }
    }
}
