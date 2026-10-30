package dev.fajar.hris

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DocumentBusinessMetadataHttpTest : DocumentApiFixture() {
    private fun passport() =
        mapOf(
            "category" to "PASSPORT",
            "identityNumber" to "X-FIXTURE-0001",
            "issuingCountry" to "AU",
            "issuedOn" to "2020-01-01",
            "validUntil" to "2025-01-01",
        )

    @Test
    fun initialCategoryAndBusinessValidityRemainSeparateFromTheOperationalUploadDeadline() {
        val f = fixture()
        val document = UUID.randomUUID()
        val revision = UUID.randomUUID()
        val input =
            uploadInput(f, document = document, revision = revision) + ("metadata" to passport())
        val key = UUID.randomUUID()
        val response = start(f, input, key)
        assertEquals(200, response.statusCode(), response.body())
        val read = get(f.browser, "${f.path}/$document")
        assertEquals(200, read.statusCode(), read.body())
        val header = json.readTree(read.body())
        assertTrue(header.hasNonNull("metadata"), "Declared business metadata must be returned")
        assertEquals(json.readTree(json.writeValueAsString(passport())), header["metadata"])
        assertEquals(0, header["metadataVersion"].asInt())
        val operational = revision(f, revision)
        assertEquals("UPLOADING", operational["status"].asString())
        assertTrue(
            java.time.Instant.parse(operational["expiresAt"].asString()).isAfter(clock.instant())
        )
        assertEquals(response.body(), start(f, input, key).body())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from document_revisions where company_id=? and document_id=?",
                    Int::class.java,
                    f.company,
                    document,
                ),
        )
    }

    @Test
    fun metadataCanBeCorrectedWithoutAnotherUploadWhileHistoryAndOriginalReceiptsRemainIntact() {
        val f = fixture()
        val document = UUID.randomUUID()
        val revision = begin(f, document = document)
        val originalRevision = revision(f, revision)
        val key = UUID.randomUUID()
        val input =
            mapOf(
                "expectedVersion" to 1,
                "metadata" to passport(),
                "reason" to "Reviewed identity evidence",
            )
        val response =
            command(
                f.browser,
                "${f.path}/$document/metadata",
                json.writeValueAsString(input),
                f.csrf,
                key,
                "PUT",
            )
        assertEquals(200, response.statusCode(), response.body())
        assertEquals(2, json.readTree(response.body())["version"].asInt())
        val amended = passport() + ("validUntil" to "2030-01-01")
        val newer =
            command(
                f.browser,
                "${f.path}/$document/metadata",
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to 2,
                        "metadata" to amended,
                        "reason" to "Correct reviewed date",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, newer.statusCode(), newer.body())
        assertEquals(
            response.body(),
            command(
                    f.browser,
                    "${f.path}/$document/metadata",
                    json.writeValueAsString(input),
                    f.csrf,
                    key,
                    "PUT",
                )
                .body(),
        )
        val read = get(f.browser, "${f.path}/$document")
        assertEquals(200, read.statusCode(), read.body())
        val header = json.readTree(read.body())
        assertEquals(3, header["version"].asInt())
        assertEquals(2, header["metadataVersion"].asInt())
        assertEquals("2030-01-01", header["metadata"]["validUntil"].asString())
        assertEquals(originalRevision, revision(f, revision))
        val history = get(f.browser, "${f.path}/$document/metadata-history?limit=20")
        assertEquals(200, history.statusCode(), history.body())
        val rows = json.readTree(history.body())["items"]
        assertEquals(3, rows.size())
        assertEquals("OTHER", rows[0]["metadata"]["category"].asString())
        assertEquals("2025-01-01", rows[1]["metadata"]["validUntil"].asString())
        assertEquals("2030-01-01", rows[2]["metadata"]["validUntil"].asString())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from document_revisions where company_id=? and document_id=?",
                    Int::class.java,
                    f.company,
                    document,
                ),
        )
    }
}
