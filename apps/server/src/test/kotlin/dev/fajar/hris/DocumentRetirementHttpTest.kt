package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.repositories.DocumentReferenceRepository
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate

class DocumentRetirementHttpTest : DocumentRetirementApiFixture() {
    @Autowired private lateinit var references: DocumentReferenceRepository
    @Autowired private lateinit var runtime: JdbcTemplate

    @Test
    fun retirementMovesQuotaIntoCleanupAndKeepsProofAfterPhysicalDeletion() {
        val initial = retentionFixture()
        assertEquals(200, retentionPolicy(initial, days = 1).statusCode())
        val doc = UUID.randomUUID()
        val bytes = ByteArray(1048600)
        pdf.copyInto(bytes)
        val rev = readyRevision(initial, doc, bytes)
        val proof =
            database()
                .queryForMap(
                    "select content_bytes,content_sha256,detected_media_type,scan_clean,scanner_version,validated_at from document_revisions where id=?",
                    rev,
                )
        val readyVersion = revision(initial, rev).get("version").asLong()
        assertEquals(200, retentionChange(initial, doc, "archive").statusCode())
        val f = elapsedRetention(initial)
        assertEquals(bytes.size.toLong(), reserved(f))
        val operation = UUID.randomUUID()
        storageProbe.reads.set(0)
        val retired = retire(f, rev, readyVersion, key = operation)
        assertEquals(200, retired.statusCode(), retired.body())
        assertEquals(0, storageProbe.reads.get())
        assertEquals("RETIRED", revision(f, rev).get("status").asString())
        assertTrue(published(f, doc).get("currentRevisionId").isNull)
        assertEquals(1, retention(f, doc).get("version").asLong())
        assertEquals(2, garbage(rev))
        assertEquals(bytes.size.toLong(), reserved(f))
        assertEquals(409, content(f, rev).statusCode())
        val history =
            json.readTree(get(f.browser, "${f.path}/$doc/retention/history").body()).get("items")
        assertEquals("RETIRE", history.get(1).get("action").asString())
        assertEquals(rev.toString(), history.get(1).get("retiredRevisionId").asString())
        assertEquals(
            proof,
            database()
                .queryForMap(
                    "select content_bytes,content_sha256,detected_media_type,scan_clean,scanner_version,validated_at from document_revisions where id=?",
                    rev,
                ),
        )
        assertEquals(Result.Success(0), cleanupCollector().execute(UUID.randomUUID()))
        due(rev)
        assertEquals(Result.Success(2), cleanupCollector().execute(UUID.randomUUID()))
        assertFalse(storageProbe.objects.keys.any { it.startsWith("${f.company}/$rev/") })
        assertEquals(0, garbage(rev))
        assertEquals(0L, reserved(f))
        assertEquals(retired.body(), retire(f, rev, readyVersion, key = operation).body())
        assertEquals(0, garbage(rev))
        assertEquals(
            proof,
            database()
                .queryForMap(
                    "select content_bytes,content_sha256,detected_media_type,scan_clean,scanner_version,validated_at from document_revisions where id=?",
                    rev,
                ),
        )
    }

    @Test
    fun historicalRetirementDoesNotClearANewerPublicationAndRestoreAllowsNewEvidence() {
        val initial = retentionFixture()
        assertEquals(200, retentionPolicy(initial, days = 1).statusCode())
        val doc = UUID.randomUUID()
        val old = readyRevision(initial, doc)
        val newer =
            readyRevision(initial, doc, version = published(initial, doc).get("version").asLong())
        assertEquals(200, retentionChange(initial, doc, "archive").statusCode())
        val f = elapsedRetention(initial)
        val header = published(f, doc)
        assertEquals(200, retire(f, old).statusCode())
        assertEquals(header, published(f, doc))
        assertEquals(200, content(f, newer).statusCode())
        assertEquals(200, retire(f, newer, retentionVersion = 1).statusCode())
        assertTrue(published(f, doc).get("currentRevisionId").isNull)
        assertEquals(200, retentionChange(f, doc, "restore", 2).statusCode())
        assertEquals(
            200,
            start(
                    f,
                    uploadInput(
                        f,
                        document = doc,
                        version = published(f, doc).get("version").asLong(),
                    ),
                )
                .statusCode(),
        )
        assertEquals("RETIRED", revision(f, old).get("status").asString())
        assertEquals("RETIRED", revision(f, newer).get("status").asString())
    }

    @Test
    fun indefinitePoliciesDeadlinesAndLegalHoldsPreventRetirement() {
        val initial = retentionFixture()
        val policy = UUID.randomUUID()
        assertEquals(200, retentionPolicy(initial, policy, days = null).statusCode())
        val doc = UUID.randomUUID()
        val rev = readyRevision(initial, doc)
        assertEquals(200, retentionChange(initial, doc, "archive").statusCode())
        val indefinite = retire(initial, rev)
        assertEquals(409, indefinite.statusCode(), indefinite.body())
        assertEquals(
            "document_retention_indefinite",
            json.readTree(indefinite.body()).get("code").asString(),
        )
        assertEquals(200, retentionPolicy(initial, policy, 0, 1).statusCode())
        assertEquals(200, retentionChange(initial, doc, "restore", 0).statusCode())
        assertEquals(200, retentionChange(initial, doc, "archive", 1).statusCode())
        val early = retire(initial, rev, retentionVersion = 2)
        assertEquals(409, early.statusCode(), early.body())
        assertEquals(
            "document_retention_pending",
            json.readTree(early.body()).get("code").asString(),
        )
        val f = elapsedRetention(initial)
        assertEquals(200, retentionChange(f, doc, "hold", 2).statusCode())
        val held = retire(f, rev, retentionVersion = 3)
        assertEquals(409, held.statusCode(), held.body())
        assertEquals("document_legal_hold", json.readTree(held.body()).get("code").asString())
        assertEquals(0, garbage(rev))
        assertEquals(200, retentionChange(f, doc, "release-hold", 3).statusCode())
        assertEquals(409, retire(f, rev, retentionVersion = 3).statusCode())
        assertEquals(200, retire(f, rev, retentionVersion = 4).statusCode())
    }

    @Test
    fun failedAuditOrMissingCleanupRegistrationRollsBackAllRetirementEffects() {
        for (mode in listOf("audit", "cleanup")) {
            val initial = retentionFixture()
            assertEquals(200, retentionPolicy(initial, days = 1).statusCode())
            val doc = UUID.randomUUID()
            val rev = readyRevision(initial, doc)
            assertEquals(200, retentionChange(initial, doc, "archive").statusCode())
            val f = elapsedRetention(initial)
            val version = revision(f, rev).get("version").asLong()
            val operation = UUID.randomUUID()
            if (mode == "audit") {
                database()
                    .execute(
                        """create function retirement_fault() returns trigger language plpgsql as ${'$'}${'$'} begin if new.resource_id='$rev'::uuid and new.action='documents.revision_retired' then raise exception 'Fixture' using errcode='23514';end if;return new;end ${'$'}${'$'}"""
                    )
                database()
                    .execute(
                        "create trigger retirement_fault_probe before insert on audit_entries for each row execute function retirement_fault()"
                    )
            } else {
                database()
                    .execute(
                        """create function retirement_fault() returns trigger language plpgsql as ${'$'}${'$'} begin if new.resource_id='$rev'::uuid then return null;end if;return new;end ${'$'}${'$'}"""
                    )
                database()
                    .execute(
                        "create trigger retirement_fault_probe before insert on object_cleanup_queue for each row execute function retirement_fault()"
                    )
            }
            try {
                assertNotEquals(200, retire(f, rev, version, key = operation).statusCode())
                assertEquals("READY", revision(f, rev).get("status").asString())
                assertEquals(rev.toString(), published(f, doc).get("currentRevisionId").asString())
                assertEquals(0, retention(f, doc).get("version").asLong())
                assertEquals(0, garbage(rev))
                assertEquals(
                    0,
                    database()
                        .queryForObject(
                            "select count(*) from document_retirements where company_id=?",
                            Int::class.java,
                            f.company,
                        ),
                )
                assertEquals(pdf.size.toLong(), reserved(f))
            } finally {
                val table = if (mode == "audit") "audit_entries" else "object_cleanup_queue"
                database().execute("drop trigger retirement_fault_probe on $table")
                database().execute("drop function retirement_fault()")
            }
            val retry = retire(f, rev, version, key = operation)
            assertEquals(200, retry.statusCode(), retry.body())
        }
    }

    @Test
    fun aHundredPartManifestRetiresWithoutReadingContentOrLosingQuota() {
        val initial = retentionFixture()
        assertEquals(200, retentionPolicy(initial, days = 1).statusCode())
        val doc = UUID.randomUUID()
        val rev = UUID.randomUUID()
        val part = ByteArray(1048576)
        pdf.copyInto(part)
        val sha = java.security.MessageDigest.getInstance("SHA-256")
        repeat(100) { sha.update(part) }
        val fullDigest = java.util.HexFormat.of().formatHex(sha.digest())
        val input =
            uploadInput(initial, part, doc, rev) +
                mapOf("size" to 104857600L, "sha256" to fullDigest)
        val begun = start(initial, input)
        assertEquals(200, begun.statusCode(), begun.body())
        repeat(100) { index ->
            val result = upload(initial, rev, part, index * 1048576L)
            assertEquals(200, result.statusCode(), result.body())
        }
        assertTrue(run(initial, beginValidation(initial, rev)) is Result.Success)
        assertEquals("READY", revision(initial, rev).get("status").asString())
        assertEquals(200, retentionChange(initial, doc, "archive").statusCode())
        val f = elapsedRetention(initial)
        storageProbe.reads.set(0)
        val response = retire(f, rev)
        assertEquals(200, response.statusCode(), response.body())
        assertEquals(0, storageProbe.reads.get())
        assertEquals(100, garbage(rev))
        assertEquals(104857600L, reserved(f))
    }

    @Test
    fun retirementDuringAPendingReadDiscardsTheBytesBeforeTheResponse() {
        val initial = retentionFixture()
        assertEquals(200, retentionPolicy(initial, days = 1).statusCode())
        val doc = UUID.randomUUID()
        val rev = readyRevision(initial, doc)
        assertEquals(200, retentionChange(initial, doc, "archive").statusCode())
        val f = elapsedRetention(initial)
        val version = revision(f, rev).get("version").asLong()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        storageProbe.beforeRead = {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<java.net.http.HttpResponse<ByteArray>> { content(f, rev) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val response = retire(f, rev, version)
                assertEquals(200, response.statusCode(), response.body())
            } finally {
                release.countDown()
            }
            val response = pending.get(15, TimeUnit.SECONDS)
            assertEquals(409, response.statusCode())
            assertTrue(response.body().isEmpty())
        }
    }

    @Test
    fun retiredEvidenceIsImmutableCompanyScopedAndCannotAcquireNewBusinessReferences() {
        val initial = retentionFixture()
        assertEquals(200, retentionPolicy(initial, days = 1).statusCode())
        val doc = UUID.randomUUID()
        val rev = readyRevision(initial, doc)
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update document_revisions set status='RETIRED',version=version+1 where id=?",
                    rev,
                )
        }
        assertEquals(200, retentionChange(initial, doc, "archive").statusCode())
        val f = elapsedRetention(initial)
        assertEquals(200, retire(f, rev).statusCode())
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update document_revisions set scanner_version='changed',version=version+1 where id=?",
                    rev,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database().update("delete from document_retirements where revision_id=?", rev)
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update document_revisions set status='READY',version=version+1 where id=?",
                    rev,
                )
        }
        assertTrue(
            scope.run(f.actor) {
                references.retain(
                    f.company,
                    DocumentReferenceOrigin(
                        DocumentReferenceKind.EXPENSE_DRAFT,
                        UUID.randomUUID(),
                        0,
                    ),
                    setOf(rev),
                    f.actor.accountId,
                    clock.instant(),
                )
            } is Result.Failed
        )
        val other = retentionFixture()
        assertEquals(404, get(other.browser, "${other.path}/revisions/$rev").statusCode())
        assertEquals(
            Result.Success(0),
            scope.run(other.actor) {
                Result.Success(
                    runtime.queryForObject(
                        "select count(*) from document_retirements where company_id=?",
                        Int::class.java,
                        f.company,
                    )
                )
            },
        )
    }
}
