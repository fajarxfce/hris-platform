package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.JobStep
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DocumentDownloadHttpTest : DocumentValidationApiFixture() {
    private fun ready(f: Fixture, bytes: ByteArray = pdf): UUID {
        val id = filled(f, bytes)
        assertEquals(Result.Success(JobStep(1, true)), run(f, beginValidation(f, id)))
        storageProbe.reads.set(0)
        return id
    }

    private fun download(
        f: Fixture,
        id: UUID,
        headers: Map<String, String> = emptyMap(),
        method: String = "GET",
    ): HttpResponse<ByteArray> {
        val request =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port${f.path}/revisions/$id/content"))
                .timeout(Duration.ofSeconds(10))
        headers.forEach { (name, value) -> request.header(name, value) }
        return f.browser.send(
            request.method(method, HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )
    }

    @Test
    fun fullAndCrossChunkRangesMatchTheImmutableRevisionExactly() {
        val f = fixture()
        val bytes = ByteArray(1048600) { (it % 127).toByte() }
        pdf.copyInto(bytes)
        val id = ready(f, bytes)
        val full = download(f, id)
        assertEquals(200, full.statusCode())
        assertArrayEquals(bytes, full.body())
        assertEquals("application/pdf", full.headers().firstValue("Content-Type").orElseThrow())
        assertEquals("private, no-store", full.headers().firstValue("Cache-Control").orElseThrow())
        assertTrue(
            full.headers().firstValue("Content-Disposition").orElseThrow().startsWith("attachment;")
        )
        assertEquals("nosniff", full.headers().firstValue("X-Content-Type-Options").orElseThrow())
        for ((range, expected) in
            listOf(
                "bytes=0-4" to bytes.copyOfRange(0, 5),
                "bytes=1048570-1048585" to bytes.copyOfRange(1048570, 1048586),
                "bytes=-7" to bytes.takeLast(7).toByteArray(),
                "bytes=1048590-" to bytes.copyOfRange(1048590, bytes.size),
            )) {
            val part = download(f, id, mapOf("Range" to range))
            assertEquals(206, part.statusCode())
            assertArrayEquals(expected, part.body())
            assertTrue(part.headers().firstValue("Content-Range").isPresent)
        }
    }

    @Test
    fun conditionalRequestsAndHeadAvoidStorageReadsAndRecheckAuthorization() {
        val f = fixture()
        val id = ready(f)
        val head = download(f, id, method = "HEAD")
        assertEquals(200, head.statusCode())
        assertTrue(head.body().isEmpty())
        assertEquals(0, storageProbe.reads.get())
        val etag = head.headers().firstValue("ETag").orElseThrow()
        for (value in listOf(etag, "W/$etag", "*")) assertEquals(
            304,
            download(f, id, mapOf("If-None-Match" to value)).statusCode(),
        )
        assertEquals(0, storageProbe.reads.get())
        assertEquals(
            206,
            download(f, id, mapOf("If-Range" to etag, "Range" to "bytes=0-2")).statusCode(),
        )
        val changed =
            download(f, id, mapOf("If-Range" to "\"another-version\"", "Range" to "bytes=0-2"))
        assertEquals(200, changed.statusCode())
        assertArrayEquals(pdf, changed.body())
        val ignored = download(f, id, mapOf("Range" to "bytes=0-2"), "HEAD")
        assertEquals(200, ignored.statusCode())
        assertEquals(
            pdf.size.toString(),
            ignored.headers().firstValue("Content-Length").orElseThrow(),
        )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='documents.read'",
                f.company,
                f.actor.accountId,
            )
        assertEquals(403, download(f, id, mapOf("If-None-Match" to etag)).statusCode())
    }

    @Test
    fun malformedUnsatisfiableAndMultipartRangesReturnAnEmpty416() {
        val f = fixture()
        val id = ready(f)
        for (value in
            listOf(
                "bytes=999999-",
                "bytes=4-2",
                "bytes=-0",
                "bytes=0-1,3-4",
                "bytes=99999999999999999999-",
                "bytes=--",
                "items=0-1",
                "bytes=0--2",
            )) {
            val response = download(f, id, mapOf("Range" to value))
            assertEquals(416, response.statusCode(), value)
            assertTrue(response.body().isEmpty())
            assertEquals(
                "bytes */${pdf.size}",
                response.headers().firstValue("Content-Range").orElseThrow(),
            )
        }
        assertEquals(0, storageProbe.reads.get())
    }

    @Test
    fun foreignUnreadyAndRejectedRevisionsCannotBeDownloaded() {
        val f = fixture()
        val id = filled(f)
        assertEquals(409, download(f, id).statusCode())
        scan.clean = false
        run(f, beginValidation(f, id))
        storageProbe.reads.set(0)
        assertEquals(409, download(f, id).statusCode())
        val other = fixture()
        assertEquals(404, download(other, id).statusCode())
        assertEquals(0, storageProbe.reads.get())
    }

    @Test
    fun corruptionStopsTheTransferWithoutReturningTheChangedBytes() {
        val f = fixture()
        val id = ready(f)
        val key =
            database()
                .queryForObject(
                    "select object_key from document_upload_chunks where revision_id=?",
                    String::class.java,
                    id,
                )!!
        val value = storageProbe.objects.getValue(key)
        storageProbe.objects[key] = value.copy(bytes = ByteArray(value.bytes.size))
        val response = download(f, id)
        assertEquals(409, response.statusCode())
        assertTrue(response.body().isEmpty())
    }

    @Test
    fun revocationDuringAReadRejectsItsResultBeforeAnyResponseBytes() {
        val f = fixture()
        val id = ready(f)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        storageProbe.beforeRead = {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
        }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<ByteArray>> { download(f, id) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='documents.read'",
                        f.company,
                        f.actor.accountId,
                    )
            } finally {
                release.countDown()
            }
            val response = pending.get(10, TimeUnit.SECONDS)
            assertEquals(403, response.statusCode())
            assertTrue(response.body().isEmpty())
        }
    }
}
