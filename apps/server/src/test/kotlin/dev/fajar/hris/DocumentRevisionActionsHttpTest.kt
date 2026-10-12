package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.usecases.CancelDocumentUpload
import dev.fajar.hris.documents.domain.usecases.GetDocumentRevision
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class DocumentRevisionActionsHttpTest : DocumentAssuranceApiFixture() {
    @Autowired private lateinit var details: GetDocumentRevision
    @Autowired private lateinit var cancelUpload: CancelDocumentUpload

    @Test
    fun interruptedUploadRecoversItsOriginalCommandOnlyForTheSameAuthorizedOperator() {
        val f = fixture()
        val document = UUID.randomUUID()
        val id = begin(f, pdf, document)
        val operation = UUID.randomUUID()
        storageProbe.fail = true
        assertEquals(503, upload(f, id, pdf, key = operation).statusCode())
        val continuation = revision(f, id)["uploadContinuation"]
        assertEquals(
            setOf("operationId", "offset", "size", "sha256", "attempts", "retryAt"),
            continuation.propertyNames().toSet(),
        )
        assertEquals(operation.toString(), continuation["operationId"].asString())
        assertEquals(0, continuation["offset"].asInt())
        assertEquals(pdf.size, continuation["size"].asInt())
        assertEquals(digest(pdf), continuation["sha256"].asString())
        assertEquals(1, continuation["attempts"].asInt())
        val history = json.readTree(get(f.browser, "${f.path}/$document/revisions").body())
        assertTrue(history["items"][0]["uploadContinuation"].isNull)

        val other = UUID.randomUUID()
        val email = "$other@example.test"
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Other operator',password_hash from accounts where email='admin@example.test'",
                other,
                email,
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                f.company,
                other,
            )
        for (permission in
            listOf(
                "company.read",
                "documents.read",
                "people.profile.read",
                "documents.manage",
                "people.profile.manage",
            )) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                f.company,
                other,
                permission,
            )
        val browser = client()
        login(browser, email)
        val foreign = get(browser, "${f.path}/revisions/$id")
        assertEquals(200, foreign.statusCode())
        val foreignDetails = json.readTree(foreign.body())
        assertTrue(foreignDetails["uploadContinuation"].isNull)
        assertEquals(
            listOf("CANCEL_UPLOAD"),
            foreignDetails["availableActions"]
                .iterator()
                .asSequence()
                .map { it.asString() }
                .toList(),
        )

        val restricted =
            details.execute(
                f.actor.copy(permissions = setOf("documents.read", "people.profile.read")),
                id,
            )
        assertTrue(restricted is Result.Success)
        assertNull((restricted as Result.Success).value.uploadContinuation)
        assertTrue(restricted.value.availableActions.isEmpty())
        expireLease(id)
        storageProbe.fail = false
        assertEquals(
            200,
            upload(f, id, pdf, key = UUID.fromString(continuation["operationId"].asString()))
                .statusCode(),
        )
        assertTrue(revision(f, id)["uploadContinuation"].isNull)
        assertEquals(pdf.size, revision(f, id)["uploadedBytes"].asInt())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='documents.chunk_committed'",
                    Int::class.java,
                    id,
                ),
        )
    }

    @Test
    fun actionsFollowCommittedUploadAndInspectionState() {
        val f = fixture()
        val document = UUID.randomUUID()
        val id = begin(f, pdf, document)
        assertEquals(
            listOf("UPLOAD_CONTENT", "CANCEL_UPLOAD"),
            revision(f, id)["availableActions"]
                .iterator()
                .asSequence()
                .map { it.asString() }
                .toList(),
        )
        assertEquals(200, upload(f, id, pdf).statusCode())
        assertEquals(
            listOf("VALIDATE", "CANCEL_UPLOAD"),
            revision(f, id)["availableActions"]
                .iterator()
                .asSequence()
                .map { it.asString() }
                .toList(),
        )
        val lease = beginValidation(f, id)
        assertEquals(
            listOf("CANCEL_UPLOAD"),
            revision(f, id)["availableActions"]
                .iterator()
                .asSequence()
                .map { it.asString() }
                .toList(),
        )
        assertTrue(run(f, lease) is Result.Success)
        assertEquals("READY", revision(f, id)["status"].asString())
        assertTrue(revision(f, id)["availableActions"].isEmpty)
        val history = get(f.browser, "${f.path}/$document/revisions?limit=20")
        assertEquals(200, history.statusCode())
        assertTrue(json.readTree(history.body())["items"][0]["availableActions"].isEmpty)
    }

    @Test
    fun detailCapabilitiesNeverGainLaterOrMissingManagementGrants() {
        val f = fixture()
        val id = filled(f)
        val read = setOf("documents.read", "people.profile.read")
        for (permissions in
            listOf(read, read + "documents.manage", read + "people.profile.manage")) {
            val result = details.execute(f.actor.copy(permissions = permissions), id)
            assertTrue(result is Result.Success, result.toString())
            assertTrue((result as Result.Success).value.availableActions.isEmpty())
        }
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='documents.manage'",
                f.company,
                f.actor.accountId,
            )
        val denied = details.execute(f.actor, id)
        assertTrue(denied is Result.Success, denied.toString())
        assertTrue((denied as Result.Success).value.availableActions.isEmpty())
    }

    @Test
    fun expiredActiveUploadsRemainExplicitlyCancellable() {
        val f = fixture()
        val id = begin(f)
        clock.set(Instant.now().plusSeconds(86401))
        val actor = f.actor.copy(authenticatedAt = clock.instant())
        val result = details.execute(actor, id)
        assertTrue(result is Result.Success, result.toString())
        val review = (result as Result.Success).value
        assertEquals(DocumentRevisionStatus.EXPIRED, review.revision.status)
        assertEquals(listOf(DocumentRevisionAction.CANCEL_UPLOAD), review.availableActions)
        val cancelled =
            cancelUpload.execute(
                actor,
                UUID.randomUUID(),
                id,
                review.revision.version,
                "Close expired upload",
            )
        assertTrue(cancelled is Result.Success, cancelled.toString())
        val after = details.execute(actor, id)
        assertTrue(after is Result.Success, after.toString())
        assertTrue((after as Result.Success).value.availableActions.isEmpty())
        assertEquals(DocumentRevisionStatus.CANCELLED, after.value.revision.status)
    }
}
