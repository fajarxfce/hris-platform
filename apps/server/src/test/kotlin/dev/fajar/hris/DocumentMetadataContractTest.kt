package dev.fajar.hris

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DocumentMetadataContractTest : SelfDocumentApiFixture() {
    private fun metadata(country: String = "AU", number: String = "F-123") =
        mapOf(
            "category" to "PASSPORT",
            "identityNumber" to number,
            "issuingCountry" to country,
            "issuedOn" to "2020-01-01",
            "validUntil" to "2030-01-01",
        )

    private fun save(
        f: Fixture,
        id: UUID,
        version: Long,
        value: Map<String, Any> = metadata(),
        key: UUID = UUID.randomUUID(),
        reason: String = "Reviewed evidence",
    ) =
        command(
            f.browser,
            "${f.path}/$id/metadata",
            json.writeValueAsString(
                mapOf("expectedVersion" to version, "metadata" to value, "reason" to reason)
            ),
            f.csrf,
            key,
            "PUT",
        )

    private fun ok(response: java.net.http.HttpResponse<String>): tools.jackson.databind.JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    @Test
    fun legacyReceiptAndReplacementOmissionPreserveTheMetadataAndOriginalUploadIdentity() {
        val f = fixture()
        val id = UUID.randomUUID()
        val revision = UUID.randomUUID()
        val key = UUID.randomUUID()
        val original = uploadInput(f, document = id, revision = revision)
        val receipt = start(f, original, key)
        ok(receipt)
        // This is the exact pre-metadata upload key, including its eleven original values.
        val legacy =
            dev.fajar.hris.core.domain.OperationKey(
                "documents.upload_start",
                key,
                listOf(
                    id.toString(),
                    revision.toString(),
                    f.employee.toString(),
                    "Employment evidence",
                    "PERSONAL",
                    "0",
                    "evidence.pdf",
                    "application/pdf",
                    "3",
                    digest(byteArrayOf(1, 2, 3)),
                    "Document submission",
                ),
            )
        val recovered = transactions.run(f.actor) { operations.lockAndReplay(f.actor, legacy) }
        assertTrue(recovered is dev.fajar.hris.core.domain.Result.Success, recovered.toString())
        assertNotNull((recovered as dev.fajar.hris.core.domain.Result.Success).value)
        assertEquals(
            "OTHER",
            ok(get(f.browser, "${f.path}/$id"))["metadata"]["category"].asString(),
        )
        ok(save(f, id, 1))
        assertEquals(receipt.body(), start(f, original, key).body())
        ok(cancel(f, revision))
        val replacement = uploadInput(f, document = id, version = 2)
        failure(
            start(f, replacement + ("metadata" to metadata(number = "different"))),
            409,
            "document_metadata_mismatch",
        )
        ok(start(f, replacement))
        val head = ok(get(f.browser, "${f.path}/$id"))
        assertEquals(3, head["version"].asInt())
        assertEquals(1, head["metadataVersion"].asInt())
        assertEquals("F-123", head["metadata"]["identityNumber"].asString())
        assertEquals(2, ok(get(f.browser, "${f.path}/$id/metadata-history"))["items"].size())
    }

    @Test
    fun normalizationPinsTheSameReceiptAndDifferentValuesCannotReuseItsKey() {
        val f = fixture()
        val id = UUID.randomUUID()
        begin(f, document = id)
        val key = UUID.randomUUID()
        val response = save(f, id, 1, metadata(" au ", " F-123 "), key)
        ok(response)
        assertEquals(response.body(), save(f, id, 1, metadata(), key).body())
        assertEquals(409, save(f, id, 1, metadata(number = "F-456"), key).statusCode())
        failure(save(f, id, 2), 409, "document_metadata_unchanged")
        assertEquals(2, ok(get(f.browser, "${f.path}/$id/metadata-history"))["items"].size())
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["country", "number", "control", "inverse", "early", "late", "reason", "version"]
    )
    fun invalidBusinessFactsDoNotChangeTheDocument(mode: String) {
        val f = fixture()
        val id = UUID.randomUUID()
        begin(f, document = id)
        val changed = metadata().toMutableMap<String, Any>()
        when (mode) {
            "country" -> changed["issuingCountry"] = "ZZ"
            "number" -> changed["identityNumber"] = "x".repeat(121)
            "control" -> changed["identityNumber"] = "abc\ndef"
            "inverse" -> changed["validUntil"] = "2019-12-31"
            "early" -> changed["issuedOn"] = "1899-01-01"
            "late" -> changed["validUntil"] = "2201-01-01"
        }
        val before = ok(get(f.browser, "${f.path}/$id"))
        assertEquals(
            422,
            save(
                    f,
                    id,
                    if (mode == "version") -1 else 1,
                    changed,
                    reason = if (mode == "reason") "" else "Reviewed",
                )
                .statusCode(),
        )
        assertEquals(before, ok(get(f.browser, "${f.path}/$id")))
        assertEquals(1, ok(get(f.browser, "${f.path}/$id/metadata-history"))["items"].size())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun competingCommandsHaveOneCommittedMetadataRevision(sameKey: Boolean) {
        val f = fixture()
        val id = UUID.randomUUID()
        begin(f, document = id)
        val key = UUID.randomUUID()
        val gate = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val first =
                pool.submit<java.net.http.HttpResponse<String>> {
                    check(gate.await(10, TimeUnit.SECONDS))
                    save(f, id, 1, key = key)
                }
            val second =
                pool.submit<java.net.http.HttpResponse<String>> {
                    check(gate.await(10, TimeUnit.SECONDS))
                    save(f, id, 1, key = if (sameKey) key else UUID.randomUUID())
                }
            gate.countDown()
            val results = listOf(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS))
            assertEquals(
                if (sameKey) listOf(200, 200) else listOf(200, 409),
                results.map { it.statusCode() }.sorted(),
            )
            if (sameKey) assertEquals(results[0].body(), results[1].body())
        }
        assertEquals(2, ok(get(f.browser, "${f.path}/$id/metadata-history"))["items"].size())
        assertEquals(2, ok(get(f.browser, "${f.path}/$id"))["version"].asInt())
    }

    @Test
    fun ownedHistoryRequiresReadGrantAndSelfManagementCannotReachHrOnlyOrAnotherEmployee() {
        val (admin, owner) = selfFixture()
        val id = UUID.randomUUID()
        begin(admin, document = id)
        ok(save(admin, id, 1))
        ok(get(owner.browser, "${owner.path}/$id/metadata-history"))
        failure(save(owner, id, 2, metadata(number = "F-456")), 403, "document_access_denied")
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'documents.self.upload')",
                owner.company,
                owner.actor.accountId,
            )
        ok(save(owner, id, 2, metadata(number = "F-456")))
        val other = UUID.randomUUID()
        begin(
            admin.copy(employee = employee(admin.browser, admin.csrf, admin.company)),
            document = other,
        )
        failure(
            get(owner.browser, "${owner.path}/$other/metadata-history"),
            403,
            "document_access_denied",
        )
        failure(save(owner, other, 1), 403, "document_access_denied")
        val hidden = UUID.randomUUID()
        ok(start(admin, uploadInput(admin, document = hidden, classification = "HR_ONLY")))
        failure(
            get(owner.browser, "${owner.path}/$hidden/metadata-history"),
            403,
            "document_access_denied",
        )
        failure(save(owner, hidden, 1), 403, "document_access_denied")
        val foreign = fixture()
        failure(
            get(foreign.browser, "${foreign.path}/$id/metadata-history"),
            404,
            "document_not_found",
        )
        failure(save(foreign, id, 3), 404, "document_not_found")
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='documents.self.read'",
                owner.company,
                owner.actor.accountId,
            )
        failure(
            get(owner.browser, "${owner.path}/$id/metadata-history"),
            403,
            "document_access_denied",
        )
    }

    @Test
    fun historyPagesAreBoundedAndInitialEntriesDoNotInventReviewReasons() {
        val f = fixture()
        val id = UUID.randomUUID()
        begin(f, document = id)
        ok(save(f, id, 1))
        ok(save(f, id, 2, metadata(number = "F-456")))
        val first = ok(get(f.browser, "${f.path}/$id/metadata-history?limit=1"))
        assertEquals("0", first["nextCursor"].asString())
        assertTrue(first["items"][0]["reason"].isNull)
        assertEquals(f.actor.accountId.toString(), first["items"][0]["actorId"].asString())
        val second = ok(get(f.browser, "${f.path}/$id/metadata-history?limit=1&after=0"))
        assertEquals("F-123", second["items"][0]["metadata"]["identityNumber"].asString())
        val last = ok(get(f.browser, "${f.path}/$id/metadata-history?limit=1&after=1"))
        assertTrue(last["nextCursor"].isNull)
        assertEquals("F-456", last["items"][0]["metadata"]["identityNumber"].asString())
        assertEquals(
            0,
            ok(get(f.browser, "${f.path}/$id/metadata-history?after=999"))["items"].size(),
        )
        for (query in listOf("limit=0", "limit=201", "after=-1", "after=1000")) failure(
            get(f.browser, "${f.path}/$id/metadata-history?$query"),
            422,
            "invalid_page_size",
        )
    }

    @Test
    fun archivalBlocksNewMetadataButOriginalReceiptAndHistoryRemainRecoverable() {
        val f = fixture()
        val id = UUID.randomUUID()
        ready(f, id)
        val head = ok(get(f.browser, "${f.path}/$id"))
        val version = head["version"].asLong()
        val key = UUID.randomUUID()
        val receipt = save(f, id, version, key = key)
        ok(receipt)
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'documents.retention')",
                f.company,
                f.actor.accountId,
            )
        ok(
            command(
                f.browser,
                "${f.path}/$id/retention/archive",
                json.writeValueAsString(mapOf("reason" to "Preserve reviewed evidence")),
                f.csrf,
                UUID.randomUUID(),
            )
        )
        failure(save(f, id, version + 1, metadata(number = "F-456")), 409, "document_archived")
        assertEquals(receipt.body(), save(f, id, version, key = key).body())
        assertEquals(2, ok(get(f.browser, "${f.path}/$id/metadata-history"))["items"].size())
    }
}
