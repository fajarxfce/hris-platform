package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.repositories.*
import dev.fajar.hris.documents.domain.usecases.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class DocumentRetirementReviewAssuranceTest : DocumentAssuranceApiFixture() {
    @Autowired private lateinit var references: DocumentReferenceRepository

    @ParameterizedTest
    @CsvSource(
        "review,revision,false",
        "review,revision,true",
        "review,retention,false",
        "review,retention,true",
        "review,reference,false",
        "review,reference,true",
        "retire,reference,false",
        "retire,reference,true",
        "retire,manifest,false",
        "retire,manifest,true",
    )
    fun pendingMetadataCannotOutliveAssuranceOrCancellation(
        operation: String,
        resource: String,
        cancel: Boolean,
    ) {
        val f = fixture()
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'documents.retention')",
                f.company,
                f.actor.accountId,
            )
        val doc = UUID.randomUUID()
        val revision = ready(f, doc)
        if (operation == "retire") {
            val policy =
                command(
                    f.browser,
                    "${f.path}/retention-policies",
                    json.writeValueAsString(
                        mapOf(
                            "policyId" to UUID.randomUUID(),
                            "classification" to "PERSONAL",
                            "expectedVersion" to null,
                            "retentionDays" to 1,
                            "reason" to "Retention test policy",
                        )
                    ),
                    f.csrf,
                    UUID.randomUUID(),
                )
            assertEquals(200, policy.statusCode(), policy.body())
            val archive =
                command(
                    f.browser,
                    "${f.path}/$doc/retention/archive",
                    json.writeValueAsString(
                        mapOf("expectedVersion" to null, "reason" to "Archive test document")
                    ),
                    f.csrf,
                    UUID.randomUUID(),
                )
            assertEquals(200, archive.statusCode(), archive.body())
            clock.set(clock.instant().plusSeconds(86401))
        }
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val age =
            if (operation == "retire") security.recentAuthenticationAge else security.maximumMfaAge
        val actor =
            enrolled(f, security)
                .copy(
                    permissions = f.actor.permissions + "documents.retention",
                    mfaVerifiedAt = clock.instant().minus(age).plusSeconds(1),
                )
        val version =
            database()
                .queryForObject(
                    "select version from document_revisions where id=?",
                    Long::class.java,
                    revision,
                )!!
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val heldDocuments =
            object : DocumentRepository by documents {
                override fun revision(companyId: UUID, id: UUID) =
                    documents.revision(companyId, id).also {
                        if (resource == "revision") {
                            entered.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                        }
                    }

                override fun chunks(companyId: UUID, revisionId: UUID) =
                    documents.chunks(companyId, revisionId).also {
                        if (resource == "manifest") {
                            entered.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                        }
                    }
            }
        val heldRetention =
            object : DocumentRetentionRepository by retention {
                override fun state(companyId: UUID, documentId: UUID) =
                    retention.state(companyId, documentId).also {
                        if (resource == "retention") {
                            entered.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                        }
                    }
            }
        val heldReferences =
            object : DocumentReferenceRepository by references {
                override fun referenced(companyId: UUID, revisionIds: Set<UUID>) =
                    references.referenced(companyId, revisionIds).also {
                        if (resource == "reference") {
                            entered.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                        }
                    }
            }
        val read =
            GetDocumentRetirementReview(
                heldRetention,
                heldDocuments,
                heldReferences,
                members,
                identities,
                transactions,
                clock,
                security,
            )
        val retire =
            RetireDocumentRevision(
                heldRetention,
                heldDocuments,
                heldReferences,
                cleanup,
                companies,
                members,
                identities,
                operations,
                journal,
                transactions,
                security,
                clock,
            )
        val commandId = UUID.randomUUID()
        val invoke: (Actor) -> Result<*> = {
            if (operation == "review") read.execute(it, revision)
            else retire.execute(it, commandId, revision, version, 0, "Expired retention")
        }
        Executors.newSingleThreadExecutor().use { executor ->
            val pending =
                executor.submit<Result<*>> {
                    try {
                        invoke(actor)
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
                        if (operation == "review") "mfa_required"
                        else "recent_authentication_required",
                        (pending.get(5, TimeUnit.SECONDS) as? Result.Failed)?.failure?.code,
                    )
                }
            } finally {
                release.countDown()
            }
        }
        assertEquals(Result.Success(Unit), transactions.run(f.actor) { documents.lock(f.company) })
        assertEquals(
            "READY",
            database()
                .queryForObject(
                    "select status from document_revisions where id=?",
                    String::class.java,
                    revision,
                ),
        )
        assertEquals(0, garbage(revision))
        val restored = invoke(actor.copy(mfaVerifiedAt = clock.instant()))
        assertTrue(restored is Result.Success, restored.toString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["permission", "credentials", "assurance"])
    fun aPendingAccountGuardCannotAuthorizeAnObsoleteReview(change: String) {
        val f = fixture()
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'documents.retention')",
                f.company,
                f.actor.accountId,
            )
        val revision = ready(f, UUID.randomUUID())
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val actor =
            enrolled(f, security).copy(permissions = f.actor.permissions + "documents.retention")
        val read =
            GetDocumentRetirementReview(
                retention,
                documents,
                references,
                members,
                identities,
                transactions,
                clock,
                security,
            )
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { executor ->
                val pending = executor.submit<Result<*>> { read.execute(actor, revision) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    when (change) {
                        "permission" ->
                            database()
                                .update(
                                    "delete from membership_permissions where company_id=? and account_id=? and permission='documents.retention'",
                                    f.company,
                                    actor.accountId,
                                )
                        "credentials" ->
                            database()
                                .update(
                                    "update accounts set security_version=security_version+1 where id=?",
                                    actor.accountId,
                                )
                        else -> clock.set(clock.instant().plusSeconds(2))
                    }
                    barrier.release.countDown()
                    val result = pending.get(5, TimeUnit.SECONDS) as? Result.Failed
                    assertNotNull(result)
                    if (change == "permission")
                        assertEquals(FailureKind.FORBIDDEN, result?.failure?.kind)
                    else
                        assertEquals(
                            if (change == "credentials") "session_revoked" else "mfa_required",
                            result?.failure?.code,
                        )
                } finally {
                    barrier.release.countDown()
                }
            }
        } finally {
            accountProbe.current.set(null)
            barrier.release.countDown()
        }
        assertEquals(Result.Success(Unit), transactions.run(f.actor) { documents.lock(f.company) })
        assertEquals(0, storageProbe.reads.get())
    }
}
