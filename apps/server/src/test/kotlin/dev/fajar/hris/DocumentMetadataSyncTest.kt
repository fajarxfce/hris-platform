package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.net.URLEncoder
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = [MOBILE_SYNC_TEST_KEYS])
class DocumentMetadataSyncTest : SelfDocumentApiFixture() {
    private fun ok(response: java.net.http.HttpResponse<String>): tools.jackson.databind.JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    private fun publish() {
        val worker = mobileSyncTestWorker(postgres.jdbcUrl, database())
        repeat(30) {
            val result = worker.maintain.execute()
            assertTrue(result is Result.Success, result.toString())
            val count = (result as Result.Success).value
            if (count.published == 0 && count.pruned == 0) return
        }
        fail<Unit>("Fixture publication exceeded its finite budget")
    }

    @Test
    fun publishedMetadataEditsInvalidateTheOwnedRecordWithoutLeakingFactsIntoTheChangeFeed() {
        val (admin, owner) = selfFixture()
        val document = UUID.randomUUID()
        val revision = ready(admin, document)
        publish()
        val path = "/api/v1/companies/${owner.company}/sync"
        val snapshot = ok(get(owner.browser, "$path/bootstrap?collections=DOCUMENT_EVIDENCE"))
        val cursor = snapshot["changesCursor"].asString()
        val before = ok(get(owner.browser, "${owner.path}/me/evidence/$document"))
        assertTrue(before["metadataSupported"].asBoolean())
        assertEquals("OTHER", before["metadata"]["category"].asString())
        val response =
            command(
                admin.browser,
                "${admin.path}/$document/metadata",
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to before["version"].asLong(),
                        "metadata" to
                            mapOf(
                                "category" to "WORK_PERMIT",
                                "identityNumber" to "PRIVATE-F-123",
                                "validUntil" to "2020-01-01",
                            ),
                        "reason" to "Record historical permit",
                    )
                ),
                admin.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        val receipt = ok(response)
        publish()
        val changes =
            ok(
                get(
                    owner.browser,
                    "$path/changes?collections=DOCUMENT_EVIDENCE&cursor=${URLEncoder.encode(cursor,Charsets.UTF_8)}",
                )
            )
        assertEquals(1, changes["items"].size())
        assertEquals("UPSERT", changes["items"][0]["operation"].asString())
        assertEquals(document.toString(), changes["items"][0]["id"].asString())
        assertEquals(receipt["version"], changes["items"][0]["version"])
        assertFalse(changes.toString().contains("PRIVATE-F-123"))
        assertFalse(changes.toString().contains("WORK_PERMIT"))
        val current = ok(get(owner.browser, "${owner.path}/me/evidence/$document"))
        assertEquals(before["evidence"], current["evidence"])
        assertEquals(revision.toString(), current["evidence"]["revisionId"].asString())
        assertEquals("PRIVATE-F-123", current["metadata"]["identityNumber"].asString())
        assertEquals("2020-01-01", current["metadata"]["validUntil"].asString())
        assertEquals(1, current["metadataVersion"].asInt())
        val projection = ok(evidence(owner))
        assertFalse(projection.toString().contains("PRIVATE-F-123"))
        assertFalse(projection["documents"]["items"][0].has("metadata"))
        assertEquals("READY", revision(admin, revision)["status"].asString())
    }
}
