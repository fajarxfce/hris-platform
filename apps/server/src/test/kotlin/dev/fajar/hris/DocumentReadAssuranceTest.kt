package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.usecases.GetDocument
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DocumentReadAssuranceTest : DocumentAssuranceApiFixture() {
    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "header",
                "revision",
                "directory",
                "history",
                "attempts",
                "download",
                "content",
                "content-after-storage",
            ]
    )
    fun anExpiredProofCannotReleaseMetadataOrContentAfterWaiting(operation: String) {
        val f = fixture()
        val document = UUID.randomUUID()
        val revision = ready(f, document)
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val actor = enrolled(f, security)
        val invoke = reader(operation, f, document, revision, security)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        if (operation == "content-after-storage")
            storageProbe.beforeRead = {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        else accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { executor ->
                val pending = executor.submit<Result<*>> { invoke(actor) }
                try {
                    assertTrue(
                        (if (operation == "content-after-storage") entered else barrier.entered)
                            .await(5, TimeUnit.SECONDS)
                    )
                    clock.set(clock.instant().plusSeconds(2))
                    barrier.release.countDown()
                    release.countDown()
                    assertEquals(
                        "mfa_required",
                        (pending.get(10, TimeUnit.SECONDS) as? Result.Failed)?.failure?.code,
                    )
                } finally {
                    barrier.release.countDown()
                    release.countDown()
                }
            }
            assertEquals(
                if (operation == "content-after-storage") 1 else 0,
                storageProbe.reads.get(),
            )
        } finally {
            accountProbe.current.set(null)
            storageProbe.beforeRead = null
            barrier.release.countDown()
            release.countDown()
        }
        val renewed = invoke(actor.copy(mfaVerifiedAt = clock.instant()))
        assertTrue(renewed is Result.Success, renewed.toString())
        if (operation.startsWith("content"))
            assertArrayEquals(pdf, (renewed as Result.Success).value as ByteArray)
    }

    @ParameterizedTest
    @ValueSource(strings = ["permission", "credentials"])
    fun authorityIsReadAgainAfterThePendingAccountGuard(change: String) {
        val f = fixture()
        val document = UUID.randomUUID()
        val revision = ready(f, document)
        val actor =
            f.actor.copy(
                credentialVersion =
                    database()
                        .queryForObject(
                            "select security_version from accounts where id=?",
                            Long::class.java,
                            f.actor.accountId,
                        )
            )
        val invoke =
            reader("header", f, document, revision, IdentitySecurityPolicy(enforceMfa = false))
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { executor ->
                val pending = executor.submit<Result<*>> { invoke(actor) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    if (change == "permission")
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission='documents.read'",
                                f.company,
                                actor.accountId,
                            )
                    else
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                actor.accountId,
                            )
                    barrier.release.countDown()
                    val code = (pending.get(10, TimeUnit.SECONDS) as? Result.Failed)?.failure?.code
                    assertEquals(
                        if (change == "permission") "document_access_denied" else "session_revoked",
                        code,
                    )
                } finally {
                    barrier.release.countDown()
                }
            }
        } finally {
            accountProbe.current.set(null)
            barrier.release.countDown()
        }
    }

    @Test
    fun readersShareAggregateGuardsAndAWriterWaitsUntilTheSnapshotIsReleased() {
        val f = fixture()
        val document = UUID.randomUUID()
        val revision = ready(f, document)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val heldProfiles =
            object : PersonProfileRepository by profiles {
                override fun findForEmployee(companyId: UUID, employeeId: UUID) =
                    profiles.findForEmployee(companyId, employeeId).also {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                    }
            }
        val slow =
            GetDocument(
                documents,
                heldProfiles,
                people,
                members,
                identities,
                transactions,
                clock,
                IdentitySecurityPolicy(enforceMfa = false),
            )
        val normal =
            reader("revision", f, document, revision, IdentitySecurityPolicy(enforceMfa = false))
        val writerEntered = CountDownLatch(1)
        Executors.newFixedThreadPool(3).use { executor ->
            val first = executor.submit<Result<*>> { slow.execute(f.actor, document) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertTrue(
                    executor.submit<Result<*>> { normal(f.actor) }.get(3, TimeUnit.SECONDS)
                        is Result.Success
                )
                val writer =
                    executor.submit<Result<Unit>> {
                        transactions.run(f.actor) {
                            documents.lock(f.company).map { writerEntered.countDown() }
                        }
                    }
                assertFalse(writerEntered.await(150, TimeUnit.MILLISECONDS))
                release.countDown()
                assertTrue(first.get(5, TimeUnit.SECONDS) is Result.Success)
                assertEquals(Result.Success(Unit), writer.get(5, TimeUnit.SECONDS))
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun cancellationReleasesPendingReadGuardsForTheNextOperation() {
        val f = fixture()
        val document = UUID.randomUUID()
        val revision = ready(f, document)
        val invoke =
            reader("header", f, document, revision, IdentitySecurityPolicy(enforceMfa = false))
        val barrier = AccountLockProbe.Barrier(f.actor.accountId)
        val exited = CountDownLatch(1)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { executor ->
                val pending =
                    executor.submit<Result<*>> {
                        try {
                            invoke(f.actor)
                        } finally {
                            exited.countDown()
                        }
                    }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    assertTrue(pending.cancel(true))
                    assertTrue(exited.await(5, TimeUnit.SECONDS))
                } finally {
                    barrier.release.countDown()
                }
            }
        } finally {
            accountProbe.current.set(null)
            barrier.release.countDown()
        }
        assertTrue(invoke(f.actor) is Result.Success)
        assertEquals(Result.Success(Unit), transactions.run(f.actor) { documents.lock(f.company) })
    }
}
