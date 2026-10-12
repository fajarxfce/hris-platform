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
    fun actionsFollowCommittedUploadAndInspectionState() {
        val f = fixture()
        val document = UUID.randomUUID()
        val id = begin(f, pdf, document)
        assertEquals(
            listOf("CANCEL_UPLOAD"),
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
