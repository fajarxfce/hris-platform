package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(DocumentStorageProbeConfiguration::class)
abstract class DocumentApiFixture : PeopleApiFixture() {
    @Autowired protected lateinit var storageProbe: DocumentStorageProbe

    protected data class Fixture(
        val browser: HttpClient,
        val csrf: String,
        val actor: Actor,
        val employee: UUID,
    ) {
        val company
            get() = requireNotNull(actor.companyId)

        val path
            get() = "/api/v1/companies/$company/documents"
    }

    protected fun fixture(): Fixture {
        clock.set(Instant.now())
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        val actor =
            database()
                .queryForObject(
                    "select id from accounts where email='admin@example.test'",
                    UUID::class.java,
                )!!
        val employee = employee(browser, csrf, company)
        return Fixture(
            browser,
            csrf,
            Actor(
                actor,
                company,
                PermissionCatalog.companyAdministrator,
                Instant.now(),
                UUID.randomUUID(),
            ),
            employee,
        )
    }

    protected fun digest(bytes: ByteArray) =
        java.util.HexFormat.of()
            .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))

    protected fun uploadInput(
        f: Fixture,
        bytes: ByteArray = byteArrayOf(1, 2, 3),
        document: UUID = UUID.randomUUID(),
        revision: UUID = UUID.randomUUID(),
        version: Long = 0,
        classification: String = "PERSONAL",
    ) =
        mapOf(
            "documentId" to document,
            "revisionId" to revision,
            "employmentId" to f.employee,
            "title" to "Employment evidence",
            "classification" to classification,
            "expectedDocumentVersion" to version,
            "fileName" to "evidence.pdf",
            "mediaType" to "application/pdf",
            "size" to bytes.size.toLong(),
            "sha256" to digest(bytes),
            "reason" to "Document submission",
        )

    protected fun start(f: Fixture, input: Map<String, Any>, key: UUID = UUID.randomUUID()) =
        command(f.browser, "${f.path}/uploads", json.writeValueAsString(input), f.csrf, key)

    protected fun begin(
        f: Fixture,
        bytes: ByteArray = byteArrayOf(1, 2, 3),
        document: UUID = UUID.randomUUID(),
        revision: UUID = UUID.randomUUID(),
        version: Long = 0,
    ): UUID {
        val response = start(f, uploadInput(f, bytes, document, revision, version))
        assertEquals(200, response.statusCode(), response.body())
        return revision
    }

    protected fun upload(
        f: Fixture,
        revision: UUID,
        bytes: ByteArray = byteArrayOf(1, 2, 3),
        offset: Long = 0,
        key: UUID = UUID.randomUUID(),
        hash: String = digest(bytes),
        publisher: HttpRequest.BodyPublisher = HttpRequest.BodyPublishers.ofByteArray(bytes),
    ): HttpResponse<String> =
        f.browser.send(
            HttpRequest.newBuilder(
                    URI("http://127.0.0.1:$port${f.path}/revisions/$revision/chunks")
                )
                .timeout(java.time.Duration.ofSeconds(20))
                .expectContinue(true)
                .header("Content-Type", "application/octet-stream")
                .header("X-CSRF-TOKEN", f.csrf)
                .header("Idempotency-Key", key.toString())
                .header("Upload-Offset", offset.toString())
                .header("Upload-Checksum-Sha256", hash)
                .POST(publisher)
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    protected fun revision(f: Fixture, id: UUID): tools.jackson.databind.JsonNode {
        val response = get(f.browser, "${f.path}/revisions/$id")
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    protected fun cancel(f: Fixture, id: UUID, version: Long = 0, key: UUID = UUID.randomUUID()) =
        command(
            f.browser,
            "${f.path}/revisions/$id/cancel",
            json.writeValueAsString(
                mapOf("expectedVersion" to version, "reason" to "Replacement required")
            ),
            f.csrf,
            key,
        )

    protected fun expireLease(id: UUID) {
        database()
            .update(
                "update document_upload_chunks set lease_until=clock_timestamp()-interval '1 second' where revision_id=? and status='PENDING'",
                id,
            )
    }

    @AfterEach
    fun resetDocumentProbe() {
        storageProbe.beforeWrite = null
        storageProbe.beforeRead = null
        storageProbe.reads.set(0)
        storageProbe.fail = false
        storageProbe.objects.clear()
        storageProbe.calls.set(0)
        clock.set(Instant.now())
    }
}
