package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.usecases.GetDocumentRetirementReview
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class DocumentRetirementReviewHttpTest : DocumentRetirementApiFixture() {
    @Autowired private lateinit var review: GetDocumentRetirementReview

    @Test
    fun reviewUsesServerTimeAndFrozenArchivePolicyWithoutReservingRetirement() {
        val initial = retentionFixture()
        val policy = UUID.randomUUID()
        val doc = UUID.randomUUID()
        val rev = readyRevision(initial, doc)
        fun read(f: Fixture) =
            get(f.browser, "${f.path}/revisions/$rev/retirement").let {
                assertEquals(200, it.statusCode(), it.body())
                json.readTree(it.body())
            }
        val before = read(initial)
        assertEquals("document_not_archived", before["blockingReason"].asString())
        assertTrue(before["retention"]["version"].isNull)
        assertEquals(200, retentionPolicy(initial, policy, days = null).statusCode())
        assertEquals(200, retentionChange(initial, doc, "archive").statusCode())
        assertEquals("document_retention_indefinite", read(initial)["blockingReason"].asString())
        assertEquals(200, retentionPolicy(initial, policy, 0, 1).statusCode())
        assertEquals("document_retention_indefinite", read(initial)["blockingReason"].asString())
        assertEquals(200, retentionChange(initial, doc, "restore", 0).statusCode())
        assertEquals(200, retentionChange(initial, doc, "archive", 1).statusCode())
        assertEquals("document_retention_pending", read(initial)["blockingReason"].asString())
        val f = elapsedRetention(initial)
        storageProbe.reads.set(0)
        val eligible = read(f)
        assertTrue(eligible["blockingReason"].isNull)
        assertEquals(clock.instant(), java.time.Instant.parse(eligible["reviewedAt"].asString()))
        assertEquals(2L, eligible["retention"]["version"].asLong())
        assertEquals(rev.toString(), eligible["revisionId"].asString())
        assertEquals(doc.toString(), eligible["retention"]["documentId"].asString())
        assertEquals(1, eligible["revisionNumber"].asInt())
        assertEquals("READY", eligible["status"].asString())
        assertTrue(eligible["published"].asBoolean())
        assertEquals(0, storageProbe.reads.get())
        assertEquals(0, garbage(rev))
        assertEquals(2L, retention(f, doc)["version"].asLong())

        assertEquals(200, retentionChange(f, doc, "hold", 2).statusCode())
        assertEquals("document_legal_hold", read(f)["blockingReason"].asString())
        val rejected = retire(f, rev, eligible["revisionVersion"].asLong(), 2)
        assertEquals(409, rejected.statusCode(), rejected.body())
        assertEquals(0, garbage(rev))
        assertEquals(200, retentionChange(f, doc, "release-hold", 3).statusCode())
        val current = read(f)
        assertEquals(200, retire(f, rev, current["revisionVersion"].asLong(), 4).statusCode())
        val retired = read(f)
        assertEquals("RETIRED", retired["status"].asString())
        assertEquals("document_revision_not_ready", retired["blockingReason"].asString())
        assertFalse(retired["published"].asBoolean())
    }

    @Test
    fun independentRetentionAccessExposesOnlyDecisionMetadataAndCannotReadContent() {
        val f = retentionFixture()
        val doc = UUID.randomUUID()
        val rev = readyRevision(f, doc)
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission not in ('company.read','documents.retention')",
                f.company,
                f.actor.accountId,
            )
        val result = get(f.browser, "${f.path}/revisions/$rev/retirement")
        assertEquals(200, result.statusCode(), result.body())
        val body = json.readTree(result.body())
        assertEquals(
            setOf(
                "revisionId",
                "revisionNumber",
                "revisionVersion",
                "status",
                "published",
                "retention",
                "reviewedAt",
                "blockingReason",
            ),
            body.properties().map { it.key }.toSet(),
        )
        assertEquals(403, get(f.browser, "${f.path}/$doc").statusCode())
        assertEquals(403, get(f.browser, "${f.path}/revisions/$rev").statusCode())
        assertEquals(403, content(f, rev).statusCode())
        assertEquals(
            404,
            get(f.browser, "${f.path}/revisions/${UUID.randomUUID()}/retirement").statusCode(),
        )
        val other = retentionFixture()
        assertEquals(
            404,
            get(other.browser, "${other.path}/revisions/$rev/retirement").statusCode(),
        )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='documents.retention'",
                f.company,
                f.actor.accountId,
            )
        assertEquals(403, get(f.browser, "${f.path}/revisions/$rev/retirement").statusCode())
    }

    @Test
    fun aNewGrantCannotExpandTheOriginalReviewAuthority() {
        val f = retentionFixture()
        val rev = readyRevision(f)
        val result =
            review.execute(
                f.actor.copy(permissions = f.actor.permissions - "documents.retention"),
                rev,
            )
        assertEquals(FailureKind.FORBIDDEN, (result as? Result.Failed)?.failure?.kind)
    }

    @Test
    fun unvalidatedAndHistoricalRevisionsKeepTheirOwnStatusAndPublication() {
        val f = retentionFixture()
        val unvalidated = begin(f)
        val pending = get(f.browser, "${f.path}/revisions/$unvalidated/retirement")
        assertEquals(200, pending.statusCode(), pending.body())
        assertEquals(
            "document_revision_not_ready",
            json.readTree(pending.body())["blockingReason"].asString(),
        )
        val doc = UUID.randomUUID()
        val old = readyRevision(f, doc)
        val current = readyRevision(f, doc, version = published(f, doc)["version"].asLong())
        for (id in listOf(old, current)) {
            val response = get(f.browser, "${f.path}/revisions/$id/retirement")
            assertEquals(200, response.statusCode(), response.body())
            assertEquals(id == current, json.readTree(response.body())["published"].asBoolean())
        }
    }
}
