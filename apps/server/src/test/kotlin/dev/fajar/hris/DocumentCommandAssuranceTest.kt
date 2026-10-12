package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DocumentCommandAssuranceTest : DocumentAssuranceApiFixture() {
    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "start",
                "chunk",
                "cancel",
                "validate",
                "start-replay",
                "chunk-replay",
                "cancel-replay",
                "validate-replay",
            ]
    )
    fun commandsAndOriginalReceiptsRequireCurrentProofAfterTheAccountWait(scenario: String) {
        val f = fixture()
        val operation = scenario.removeSuffix("-replay")
        val revision = if (operation == "validate") filled(f) else begin(f, pdf)
        val version = revision(f, revision).get("version").asLong()
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val actor = enrolled(f, security)
        val invoke = mutation(operation, f, revision, version, UUID.randomUUID(), security)
        val previous = if (scenario.endsWith("-replay")) invoke(actor) else null
        if (previous != null) assertTrue(previous is Result.Success, previous.toString())
        val before = revision(f, revision).toString()
        val documentsBefore = documentCount(f)
        storageProbe.calls.set(0)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { executor ->
                val pending = executor.submit<Result<*>> { invoke(actor) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    clock.set(clock.instant().plusSeconds(2))
                    barrier.release.countDown()
                    assertEquals(
                        "mfa_required",
                        (pending.get(10, TimeUnit.SECONDS) as? Result.Failed)?.failure?.code,
                    )
                } finally {
                    barrier.release.countDown()
                }
            }
        } finally {
            accountProbe.current.set(null)
            barrier.release.countDown()
        }
        assertEquals(before, revision(f, revision).toString())
        assertEquals(documentsBefore, documentCount(f))
        assertEquals(0, storageProbe.calls.get())
        val renewed = invoke(actor.copy(mfaVerifiedAt = clock.instant()))
        assertTrue(renewed is Result.Success, renewed.toString())
        if (previous != null) {
            assertEquals(
                (previous as Result.Success).value.copy(replayed = true),
                (renewed as Result.Success).value,
            )
            assertEquals(0, storageProbe.calls.get())
        }
    }

    @Test
    fun expiryDuringStorageDoesNotPublishProgressAndTheOriginalChunkCanBeRecovered() {
        val f = fixture()
        val revision = begin(f, pdf)
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val actor = enrolled(f, security)
        val invoke = mutation("chunk", f, revision, 0, UUID.randomUUID(), security)
        storageProbe.beforeWrite = { clock.set(clock.instant().plusSeconds(2)) }
        try {
            assertEquals("mfa_required", (invoke(actor) as? Result.Failed)?.failure?.code)
        } finally {
            storageProbe.beforeWrite = null
        }
        assertEquals(1, storageProbe.calls.get())
        assertEquals(0, revision(f, revision).get("uploadedBytes").asLong())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from document_upload_chunks where revision_id=? and status='COMMITTED'",
                    Int::class.java,
                    revision,
                ),
        )
        expireLease(revision)
        clock.set(java.time.Instant.now().plusSeconds(5))
        val recovered = invoke(actor.copy(mfaVerifiedAt = clock.instant()))
        assertTrue(recovered is Result.Success, recovered.toString())
        assertEquals(pdf.size.toLong(), revision(f, revision).get("uploadedBytes").asLong())
        assertEquals(2, storageProbe.calls.get())
        assertEquals(
            Result.Success((recovered as Result.Success).value.copy(replayed = true)),
            invoke(actor.copy(mfaVerifiedAt = clock.instant())),
        )
        assertEquals(2, storageProbe.calls.get())
    }

    private fun documentCount(f: Fixture): Int =
        database()
            .queryForObject(
                "select count(*) from documents where company_id=?",
                Int::class.java,
                f.company,
            )!!
}
