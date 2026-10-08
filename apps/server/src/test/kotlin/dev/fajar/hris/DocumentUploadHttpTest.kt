package dev.fajar.hris

import dev.fajar.hris.documents.domain.policies.DOCUMENT_CHUNK_BYTES
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException

class DocumentUploadHttpTest : DocumentApiFixture() {
    @Test
    fun chunksResumeAtCommittedOffsetAndReplayTheOriginalVersion() {
        val f = fixture()
        val bytes = ByteArray(DOCUMENT_CHUNK_BYTES + 17) { (it % 251).toByte() }
        val document = UUID.randomUUID()
        val revision = begin(f, bytes, document)
        val first = bytes.copyOfRange(0, DOCUMENT_CHUNK_BYTES)
        val key = UUID.randomUUID()
        val response = upload(f, revision, first, key = key)
        assertEquals(200, response.statusCode(), response.body())
        assertEquals(1, json.readTree(response.body()).get("version").asInt())
        assertEquals(409, upload(f, revision, first).statusCode())
        assertEquals(422, upload(f, revision, first, hash = "0".repeat(64)).statusCode())
        val completed =
            upload(
                f,
                revision,
                bytes.copyOfRange(DOCUMENT_CHUNK_BYTES, bytes.size),
                DOCUMENT_CHUNK_BYTES.toLong(),
            )
        assertEquals(200, completed.statusCode(), completed.body())
        assertEquals(response.body(), upload(f, revision, first, key = key).body())
        assertEquals(409, upload(f, revision, byteArrayOf(9), key = key).statusCode())
        assertEquals(2, storageProbe.calls.get())
        val status = revision(f, revision)
        assertEquals(bytes.size.toLong(), status.get("uploadedBytes").asLong())
        assertEquals("UPLOADING", status.get("status").asString())
        assertFalse(status.toString().contains("objectKey"))
        val history = get(f.browser, "${f.path}/$document/revisions?limit=1")
        assertEquals(200, history.statusCode(), history.body())
        assertEquals(1, json.readTree(history.body()).get("items").size())
        assertEquals(
            422,
            get(f.browser, "${f.path}?employmentId=${f.employee}&limit=201").statusCode(),
        )
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from object_cleanup_queue where resource_id=?",
                    Int::class.java,
                    revision,
                ),
        )
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update document_upload_chunks set sha256=? where revision_id=?",
                    "0".repeat(64),
                    revision,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database().update("delete from document_upload_attempts where revision_id=?", revision)
        }
    }

    @Test
    fun competingWritersHaveOneActiveLeaseAndOneCommit() {
        val f = fixture()
        val revision = begin(f)
        val key = UUID.randomUUID()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        storageProbe.beforeWrite = {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val first =
                pool.submit<java.net.http.HttpResponse<String>> { upload(f, revision, key = key) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val other = upload(f, revision, key = key)
                assertEquals(409, other.statusCode(), other.body())
                assertEquals(
                    "document_chunk_in_progress",
                    json.readTree(other.body()).get("code").asString(),
                )
                assertEquals(1, storageProbe.calls.get())
            } finally {
                release.countDown()
            }
            assertEquals(200, first.get(10, TimeUnit.SECONDS).statusCode())
        }
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='documents.chunk_committed'",
                    Int::class.java,
                    revision,
                ),
        )
    }

    @Test
    fun anExpiredWriterCannotReplaceTheAcceptedAttempt() {
        val f = fixture()
        val revision = begin(f)
        val key = UUID.randomUUID()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val firstKey = java.util.concurrent.atomic.AtomicReference<String>()
        storageProbe.beforeWrite = { path ->
            if (firstKey.compareAndSet(null, path)) {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val old =
                pool.submit<java.net.http.HttpResponse<String>> { upload(f, revision, key = key) }
            val accepted: java.net.http.HttpResponse<String>
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                expireLease(revision)
                accepted = upload(f, revision, key = key)
                assertEquals(200, accepted.statusCode(), accepted.body())
                val current =
                    database()
                        .queryForObject(
                            "select object_key from document_upload_chunks where revision_id=?",
                            String::class.java,
                            revision,
                        )
                assertNotEquals(firstKey.get(), current)
                assertTrue(storageProbe.objects.containsKey(current))
            } finally {
                release.countDown()
            }
            assertEquals(accepted.body(), old.get(10, TimeUnit.SECONDS).body())
        }
        assertEquals(2, storageProbe.objects.size)
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from object_cleanup_queue where resource_id=?",
                    Int::class.java,
                    revision,
                ),
        )
        assertEquals(1, revision(f, revision).get("version").asInt())
    }

    @Test
    fun cancellationAndAccessRevocationRejectPendingResults() {
        for (revoke in listOf(false, true)) {
            val f = fixture()
            val document = UUID.randomUUID()
            val revision = begin(f, document = document)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            storageProbe.beforeWrite = {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
            Executors.newSingleThreadExecutor().use { pool ->
                val writing =
                    pool.submit<java.net.http.HttpResponse<String>> { upload(f, revision) }
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    if (revoke)
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission='documents.manage'",
                                f.company,
                                f.actor.accountId,
                            )
                    else {
                        val cancelled = cancel(f, revision)
                        assertEquals(200, cancelled.statusCode(), cancelled.body())
                    }
                } finally {
                    release.countDown()
                }
                val late = writing.get(10, TimeUnit.SECONDS)
                assertEquals(if (revoke) 403 else 409, late.statusCode(), late.body())
            }
            assertEquals(0, revision(f, revision).get("uploadedBytes").asInt())
            assertEquals(
                1,
                database()
                    .queryForObject(
                        "select count(*) from object_cleanup_queue where resource_id=?",
                        Int::class.java,
                        revision,
                    ),
            )
            if (!revoke) {
                storageProbe.beforeWrite = null
                val replacement = begin(f, document = document, version = 1)
                assertEquals(200, upload(f, replacement).statusCode())
                assertEquals("CANCELLED", revision(f, revision).get("status").asString())
            }
        }
    }

    @Test
    fun failedAuditRollsBackProgressButKeepsTheTemporaryKeyRecoverable() {
        val f = fixture()
        val revision = begin(f)
        val key = UUID.randomUUID()
        database()
            .execute(
                """create function fail_document_audit() returns trigger language plpgsql as ${'$'}${'$'} begin if new.resource_id='$revision'::uuid and new.action='documents.chunk_committed' then raise exception 'Fixture failure' using errcode='23514';end if;return new;end ${'$'}${'$'}"""
            )
        database()
            .execute(
                "create trigger document_audit_probe before insert on audit_entries for each row execute function fail_document_audit()"
            )
        try {
            val failed = upload(f, revision, key = key)
            assertTrue(failed.statusCode() >= 400, failed.body())
            assertEquals(0, revision(f, revision).get("uploadedBytes").asInt())
            assertEquals(
                "PENDING",
                database()
                    .queryForObject(
                        "select status from document_upload_chunks where revision_id=?",
                        String::class.java,
                        revision,
                    ),
            )
            assertEquals(1, storageProbe.objects.size)
            assertEquals(
                1,
                database()
                    .queryForObject(
                        "select count(*) from object_cleanup_queue where resource_id=?",
                        Int::class.java,
                        revision,
                    ),
            )
        } finally {
            database().execute("drop trigger document_audit_probe on audit_entries")
            database().execute("drop function fail_document_audit()")
        }
        expireLease(revision)
        val recovered = upload(f, revision, key = key)
        assertEquals(200, recovered.statusCode(), recovered.body())
        assertEquals(2, storageProbe.objects.size)
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='documents.chunk_committed'",
                    Int::class.java,
                    revision,
                ),
        )
        assertEquals(recovered.body(), upload(f, revision, key = key).body())
    }

    @Test
    fun failedWritesStopAtEightAttemptsAndRequireAnExplicitNewRevision() {
        val f = fixture()
        val document = UUID.randomUUID()
        val revision = begin(f, document = document)
        val key = UUID.randomUUID()
        storageProbe.fail = true
        repeat(8) {
            val failure = upload(f, revision, key = key)
            assertEquals(503, failure.statusCode(), failure.body())
            if (it < 7) expireLease(revision)
        }
        expireLease(revision)
        val exhausted = upload(f, revision, key = key)
        assertEquals(409, exhausted.statusCode(), exhausted.body())
        assertEquals(
            "document_chunk_attempts_exhausted",
            json.readTree(exhausted.body()).get("code").asString(),
        )
        assertEquals(8, storageProbe.calls.get())
        assertEquals(
            8,
            database()
                .queryForObject(
                    "select count(distinct object_key) from document_upload_attempts where revision_id=?",
                    Int::class.java,
                    revision,
                ),
        )
        assertEquals(200, cancel(f, revision).statusCode())
        storageProbe.fail = false
        val replacement = begin(f, document = document, version = 1)
        assertEquals(200, upload(f, replacement).statusCode())
    }
}
