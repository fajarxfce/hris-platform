package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.documents.domain.usecases.GetSelfDocumentRecord
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SelfDocumentRecordHttpTest : SelfDocumentApiFixture() {
    private fun reader(
        source: DocumentRepository = documents,
        security: IdentitySecurityPolicy = IdentitySecurityPolicy(enforceMfa = false),
    ) =
        GetSelfDocumentRecord(
            source,
            profiles,
            people,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Test
    fun canonicalMetadataKeepsAcceptedEvidenceWhileReplacementIsPending() {
        val (admin, owner) = selfFixture()
        val document = UUID.randomUUID()
        val accepted = ready(admin, document)
        val path = "${owner.path}/me/evidence/$document"
        val response = get(owner.browser, path)
        assertEquals(200, response.statusCode(), response.body())
        val record = json.readTree(response.body())
        assertEquals(
            setOf("id", "version", "employmentId", "classification", "evidence"),
            record.properties().map { it.key }.toSet(),
        )
        assertEquals(document.toString(), record["id"].asString())
        assertEquals(owner.employee.toString(), record["employmentId"].asString())
        assertEquals("PERSONAL", record["classification"].asString())
        assertEquals(accepted.toString(), record["evidence"]["revisionId"].asString())
        assertEquals(digest(pdf), record["evidence"]["sha256"].asString())
        val header = json.readTree(get(admin.browser, "${admin.path}/$document").body())
        assertEquals(header["version"].asLong(), record["version"].asLong())
        val replacement = filled(admin, document = document, version = record["version"].asLong())
        val pending = json.readTree(get(owner.browser, path).body())
        assertEquals(accepted.toString(), pending["evidence"]["revisionId"].asString())
        assertTrue(pending["version"].asLong() > record["version"].asLong())
        assertTrue(run(admin, beginValidation(admin, replacement)) is Result.Success)
        storageProbe.reads.set(0)
        val published = json.readTree(get(owner.browser, path).body())
        assertEquals(replacement.toString(), published["evidence"]["revisionId"].asString())
        assertTrue(published["version"].asLong() > pending["version"].asLong())
        assertEquals(0, storageProbe.reads.get())
    }

    @Test
    fun unpublishedForeignRestrictedAndMissingDocumentsHaveTheSameAbsentResult() {
        val (admin, owner) = selfFixture()
        val unpublished = UUID.randomUUID()
        begin(admin, document = unpublished)
        val other = employee(admin.browser, admin.csrf, owner.company)
        val foreignOwner = UUID.randomUUID()
        ready(admin.copy(employee = other), foreignOwner)
        val otherCompany = fixture()
        val foreignCompany = UUID.randomUUID()
        ready(otherCompany, foreignCompany)
        val restricted = UUID.randomUUID()
        val revision = UUID.randomUUID()
        assertEquals(
            200,
            start(admin, uploadInput(admin, pdf, restricted, revision, classification = "HR_ONLY"))
                .statusCode(),
        )
        assertEquals(200, upload(admin, revision, pdf).statusCode())
        assertTrue(run(admin, beginValidation(admin, revision)) is Result.Success)
        for (id in
            listOf(unpublished, foreignOwner, foreignCompany, restricted, UUID.randomUUID())) {
            failure(
                get(owner.browser, "${owner.path}/me/evidence/$id"),
                404,
                "document_evidence_not_found",
            )
        }
        failure(get(admin.browser, "${admin.path}/me/evidence/$restricted"), 403, "access_denied")
        val denied =
            reader().execute(owner.actor.copy(permissions = setOf("documents.read")), restricted)
        assertEquals("access_denied", (denied as Result.Failed).failure.code)
    }

    @ParameterizedTest
    @ValueSource(strings = ["credentials", "membership", "permission", "replacement-grant"])
    fun pendingReadCannotKeepRevokedAuthority(change: String) {
        val (admin, owner) = selfFixture()
        val id = UUID.randomUUID()
        ready(admin, id)
        val barrier = AccountLockProbe.Barrier(owner.actor.accountId, occurrence = 2)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> {
                    get(owner.browser, "${owner.path}/me/evidence/$id")
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                when (change) {
                    "credentials" ->
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                owner.actor.accountId,
                            )
                    "membership" ->
                        database()
                            .update(
                                "update company_memberships set active=false where company_id=? and account_id=?",
                                owner.company,
                                owner.actor.accountId,
                            )
                    else -> {
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=?",
                                owner.company,
                                owner.actor.accountId,
                            )
                        if (change == "replacement-grant")
                            database()
                                .update(
                                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'documents.read')",
                                    owner.company,
                                    owner.actor.accountId,
                                )
                    }
                }
                barrier.release.countDown()
                val response = pending.get(10, TimeUnit.SECONDS)
                assertEquals(
                    if (change == "credentials") 401 else 403,
                    response.statusCode(),
                    response.body(),
                )
                assertFalse(response.body().contains("fileName"))
                assertFalse(response.body().contains("sha256"))
            } finally {
                accountProbe.current.set(null)
                barrier.release.countDown()
            }
        }
    }

    @Test
    fun proofExpiryDuringAcquisitionCannotReleaseMetadataAndRenewalRecovers() {
        val (admin, owner) = selfFixture()
        val id = UUID.randomUUID()
        ready(admin, id)
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val actor = enrolled(owner, security)
        val source =
            object : DocumentRepository by documents {
                override fun publishedRecord(companyId: UUID, id: UUID) =
                    documents.publishedRecord(companyId, id).also {
                        clock.set(clock.instant().plusSeconds(2))
                    }
            }
        assertEquals(
            "mfa_required",
            (reader(source, security).execute(actor, id) as Result.Failed).failure.code,
        )
        assertTrue(
            reader(security = security).execute(actor.copy(mfaVerifiedAt = clock.instant()), id)
                is Result.Success
        )
    }

    @Test
    fun cancellationReleasesReadGuardsWithoutBlockingTheNextUpload() {
        val (admin, owner) = selfFixture()
        val id = UUID.randomUUID()
        ready(admin, id)
        val barrier = AccountLockProbe.Barrier(owner.actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { reader().execute(owner.actor, id) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
            } finally {
                accountProbe.current.set(null)
                barrier.release.countDown()
            }
        }
        begin(admin)
        assertTrue(reader().execute(owner.actor, id) is Result.Success)
    }
}
