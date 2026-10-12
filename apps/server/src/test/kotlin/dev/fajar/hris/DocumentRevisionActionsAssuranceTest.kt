package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.documents.domain.usecases.GetDocumentRevision
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DocumentRevisionActionsAssuranceTest : DocumentAssuranceApiFixture() {
    @ParameterizedTest
    @CsvSource("job,false", "job,true", "upload,false", "upload,true")
    fun expiryOrCancellationWhileReadingRevisionWorkCannotReleaseActionCapabilities(
        resource: String,
        cancel: Boolean,
    ) {
        val f = fixture()
        val id = if (resource == "job") filled(f) else begin(f)
        if (resource == "job") assertEquals(200, validate(f, id).statusCode())
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val actor = enrolled(f, security)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val held =
            object : JobRepository by jobs {
                override fun find(companyId: UUID, id: UUID, lock: Boolean) =
                    jobs.find(companyId, id, lock).also {
                        if (resource == "job") {
                            entered.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                        }
                    }
            }
        val heldDocuments =
            object : DocumentRepository by documents {
                override fun chunkAt(companyId: UUID, revisionId: UUID, ordinal: Int) =
                    documents.chunkAt(companyId, revisionId, ordinal).also {
                        if (resource == "upload") {
                            entered.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                        }
                    }
            }
        val read =
            GetDocumentRevision(
                heldDocuments,
                held,
                profiles,
                people,
                members,
                identities,
                transactions,
                clock,
                security,
            )
        Executors.newSingleThreadExecutor().use { executor ->
            val pending =
                executor.submit<Result<*>> {
                    try {
                        read.execute(actor, id)
                    } finally {
                        exited.countDown()
                    }
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                if (cancel) {
                    assertTrue(pending.cancel(true))
                    assertTrue(exited.await(5, TimeUnit.SECONDS))
                } else {
                    clock.set(clock.instant().plusSeconds(2))
                    release.countDown()
                    assertEquals(
                        "mfa_required",
                        (pending.get(5, TimeUnit.SECONDS) as? Result.Failed)?.failure?.code,
                    )
                }
            } finally {
                release.countDown()
            }
        }
        assertEquals(Result.Success(Unit), transactions.run(f.actor) { documents.lock(f.company) })
        val restored = read.execute(actor.copy(mfaVerifiedAt = clock.instant()), id)
        assertTrue(restored is Result.Success, restored.toString())
    }
}
