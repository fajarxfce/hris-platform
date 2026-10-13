package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.storage.domain.repositories.ObjectStorageRepository
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ExpenseReadAssuranceTest : ExpenseReadAssuranceApiFixture() {

    @ParameterizedTest
    @ValueSource(strings = ["success", "mfa", "corrupt"])
    fun receiptBuffersAreReleasedOnSuccessfulAndFailedDelivery(mode: String) {
        val scene = prepare()
        val captured = AtomicReference<ByteArray>()
        val observed =
            object : ObjectStorageRepository by objects {
                override fun read(key: String, offset: Long, length: Int, etag: String) =
                    objects.read(key, offset, length, etag).also { result ->
                        if (result is Result.Success) {
                            captured.set(result.value)
                            when (mode) {
                                "mfa" -> clock.set(clock.instant().plus(security.maximumMfaAge))
                                "corrupt" -> result.value[0] = 0
                            }
                        }
                    }
            }
        val result = invoke(scene, "receipt-content", storage = observed)
        if (mode == "success") assertArrayEquals(pdf, (result as Result.Success).value as ByteArray)
        else
            assertEquals(
                if (mode == "mfa") "mfa_required" else "document_content_integrity_failure",
                (result as Result.Failed).failure.code,
            )
        val bytes = requireNotNull(captured.get())
        assertTrue(bytes.isNotEmpty())
        assertTrue(bytes.all { it == 0.toByte() })
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "list",
                "details",
                "drafts",
                "history",
                "submissions",
                "payments",
                "submission",
                "categories",
                "category-history",
                "directory",
                "receipt-metadata",
                "receipt-content",
            ]
    )
    fun expiryDuringAccessAcquisitionCannotAuthorizeAnExpenseRead(action: String) {
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
    @ValueSource(
        strings =
            [
                "list",
                "details",
                "drafts",
                "history",
                "submissions",
                "payments",
                "submission",
                "categories",
                "category-history",
                "directory",
                "receipt-metadata",
                "receipt-content",
            ]
    )
    fun expiryBeforeResultDeliveryCannotReleasePrivateResults(action: String) {
        val scene = prepare()
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val barrier = ExpenseReadProbe.Barrier(scene.company, action)
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
    @ValueSource(
        strings =
            [
                "list",
                "details",
                "drafts",
                "history",
                "submissions",
                "payments",
                "submission",
                "categories",
                "category-history",
                "directory",
                "receipt-metadata",
                "receipt-content",
            ]
    )
    fun interruptedReadReleasesItsGuardsAndTheNextReadSucceeds(action: String) {
        val scene = prepare()
        val barrier = ExpenseReadProbe.Barrier(scene.company, action)
        readProbe.current.set(barrier)
        Executors.newFixedThreadPool(2).use { pool ->
            val pending = pool.submit<Result<*>> { invoke(scene, action) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                barrier.release.countDown()
                val recovered =
                    pool
                        .submit<Result<*>> {
                            val guards = acquireReadGuardsExclusively(scene)
                            assertTrue(guards is Result.Success, guards.toString())
                            invoke(scene, action)
                        }
                        .get(10, TimeUnit.SECONDS)
                assertTrue(recovered is Result.Success, recovered.toString())
            } finally {
                barrier.release.countDown()
                readProbe.current.set(null)
            }
        }
    }
}
