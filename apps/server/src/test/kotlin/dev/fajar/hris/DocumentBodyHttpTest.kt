package dev.fajar.hris

import dev.fajar.hris.documents.domain.policies.DOCUMENT_CHUNK_BYTES
import java.io.ByteArrayInputStream
import java.net.http.HttpRequest
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DocumentBodyHttpTest : DocumentApiFixture() {
    @Test
    fun declaredAndChunkedBinaryBodiesAreRejectedBeforeStorage() {
        val f = fixture()
        val revision = begin(f)
        val large = ByteArray(DOCUMENT_CHUNK_BYTES + 1)
        for (publisher in
            listOf(
                HttpRequest.BodyPublishers.ofByteArray(large),
                HttpRequest.BodyPublishers.ofInputStream { ByteArrayInputStream(large) },
            )) {
            val response = upload(f, revision, large, publisher = publisher)
            assertEquals(413, response.statusCode(), response.body())
            assertEquals(
                "request_body_too_large",
                json.readTree(response.body()).get("code").asString(),
            )
        }
        assertEquals(0, storageProbe.calls.get())
        assertEquals(0, revision(f, revision).get("uploadedBytes").asInt())
        assertEquals(200, upload(f, revision).statusCode())
    }

    @Test
    fun concurrentUploadsUseFourSlotsAndReleaseEverySlot() {
        val f = fixture()
        val ids = (1..5).map { begin(f) }
        val entered = CountDownLatch(4)
        val release = CountDownLatch(1)
        storageProbe.beforeWrite = {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
        }
        Executors.newFixedThreadPool(4).use { pool ->
            val requests =
                ids.take(4).map { id ->
                    pool.submit<java.net.http.HttpResponse<String>> { upload(f, id) }
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val rejected = upload(f, ids.last())
                assertEquals(429, rejected.statusCode(), rejected.body())
                assertEquals(
                    "document_upload_busy",
                    json.readTree(rejected.body()).get("code").asString(),
                )
                assertEquals(4, storageProbe.calls.get())
            } finally {
                release.countDown()
            }
            requests.forEach {
                val response = it.get(10, TimeUnit.SECONDS)
                assertEquals(200, response.statusCode(), response.body())
            }
        }
        storageProbe.beforeWrite = null
        assertEquals(200, upload(f, ids.last()).statusCode())
    }

    @Test
    fun stalledBodyTimesOutWithoutStartingStorageAndTheSlotCanBeReused() {
        val f = fixture()
        val revision = begin(f)
        val cookies =
            (f.browser.cookieHandler().orElseThrow() as java.net.CookieManager)
                .cookieStore
                .cookies
                .joinToString("; ") { "${it.name}=${it.value}" }
        val request =
            "POST ${f.path}/revisions/$revision/chunks HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nCookie: $cookies\r\nX-CSRF-TOKEN: ${f.csrf}\r\nContent-Type: application/octet-stream\r\nContent-Length: 3\r\nIdempotency-Key: ${java.util.UUID.randomUUID()}\r\nUpload-Offset: 0\r\nUpload-Checksum-Sha256: ${digest(byteArrayOf(1,2,3))}\r\nConnection: close\r\n\r\n"
        java.net.Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 9000
            socket.getOutputStream().write(request.toByteArray(Charsets.ISO_8859_1))
            socket.getOutputStream().write(1)
            socket.getOutputStream().flush()
            val response = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1).readLine()
            assertTrue(response.contains("408"), response)
        }
        assertEquals(0, storageProbe.calls.get())
        assertEquals(200, upload(f, revision).statusCode())
    }
}
