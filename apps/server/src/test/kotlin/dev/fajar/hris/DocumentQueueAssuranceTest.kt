package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.repositories.DocumentInventoryRepository
import dev.fajar.hris.documents.domain.usecases.StartDocumentInventory
import dev.fajar.hris.documents.domain.usecases.StartDocumentValidation
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class DocumentQueueAssuranceTest : DocumentAssuranceApiFixture() {
    @Autowired private lateinit var inventory: DocumentInventoryRepository
    private val security = IdentitySecurityPolicy(enforceMfa = true)

    @ParameterizedTest
    @ValueSource(strings = ["validation", "inventory"])
    fun queueWaitCannotExtendInteractiveProofAndTheOriginalCommandCanRecover(operation: String) {
        val f = fixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val waiting = AtomicBoolean(true)
        val queue =
            object : JobRepository by jobs {
                override fun lockQueue(companyId: UUID): Result<Unit> {
                    val result = jobs.lockQueue(companyId)
                    if (result is Result.Success && waiting.get()) {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                    }
                    return result
                }
            }
        val invoke =
            if (operation == "validation") validationCommand(f, queue) else inventoryCommand(queue)
        val enrolled = enrolled(f, security)
        val actor =
            if (operation == "validation") enrolled
            else
                enrolled.copy(
                    mfaVerifiedAt =
                        clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
                )
        try {
            Executors.newSingleThreadExecutor().use { executor ->
                val pending = executor.submit<Result<MutationReceipt>> { invoke(actor) }
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    clock.set(clock.instant().plusSeconds(2))
                    release.countDown()
                    assertEquals(
                        if (operation == "validation") "mfa_required"
                        else "recent_authentication_required",
                        (pending.get(10, TimeUnit.SECONDS) as? Result.Failed)?.failure?.code,
                    )
                } finally {
                    release.countDown()
                }
            }
        } finally {
            waiting.set(false)
            release.countDown()
        }
        assertEquals(0, jobCount(f))
        val renewed = actor.copy(mfaVerifiedAt = clock.instant())
        val recovered = invoke(renewed)
        assertTrue(recovered is Result.Success, recovered.toString())
        assertEquals(1, jobCount(f))
        assertEquals(
            Result.Success((recovered as Result.Success).value.copy(replayed = true)),
            invoke(renewed),
        )
        assertEquals(1, jobCount(f))
    }

    @Test
    fun uploadExpiryDuringQueueAdmissionDoesNotCreateAValidationJob() {
        val f = fixture()
        val revision = filled(f)
        val expiresAt = java.time.Instant.parse(revision(f, revision).get("expiresAt").asString())
        clock.set(expiresAt.minusSeconds(1))
        val actor = enrolled(f, security).copy(mfaVerifiedAt = clock.instant())
        val queue =
            object : JobRepository by jobs {
                override fun lockQueue(companyId: UUID): Result<Unit> =
                    jobs.lockQueue(companyId).also {
                        if (it is Result.Success) clock.set(expiresAt.plusSeconds(1))
                    }
            }
        val useCase =
            StartDocumentValidation(
                documents,
                profiles,
                companies,
                members,
                identities,
                queue,
                operations,
                journal,
                transactions,
                clock,
                security,
            )
        val result =
            useCase.execute(actor, UUID.randomUUID(), revision, 1, "Validate employment evidence")
        assertEquals("document_upload_closed", (result as? Result.Failed)?.failure?.code)
        assertEquals(0, jobCount(f))
    }

    private fun validationCommand(
        f: Fixture,
        queue: JobRepository,
    ): (Actor) -> Result<MutationReceipt> {
        val revision = filled(f)
        val operation = UUID.randomUUID()
        val useCase =
            StartDocumentValidation(
                documents,
                profiles,
                companies,
                members,
                identities,
                queue,
                operations,
                journal,
                transactions,
                clock,
                security,
            )
        return { actor ->
            useCase.execute(actor, operation, revision, 1, "Validate employment evidence")
        }
    }

    private fun inventoryCommand(queue: JobRepository): (Actor) -> Result<MutationReceipt> {
        val id = UUID.randomUUID()
        val operation = UUID.randomUUID()
        val useCase =
            StartDocumentInventory(
                inventory,
                documents,
                companies,
                members,
                identities,
                queue,
                operations,
                journal,
                transactions,
                security,
                clock,
            )
        return { actor -> useCase.execute(actor, operation, id, null, "Review private storage") }
    }

    private fun jobCount(f: Fixture): Int =
        database()
            .queryForObject(
                "select count(*) from background_jobs where company_id=?",
                Int::class.java,
                f.company,
            )!!
}
