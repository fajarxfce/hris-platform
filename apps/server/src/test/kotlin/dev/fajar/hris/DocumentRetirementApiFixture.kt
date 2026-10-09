package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.storage.domain.repositories.ObjectCleanupRepository
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired

abstract class DocumentRetirementApiFixture : DocumentRetentionApiFixture() {
    @Autowired protected lateinit var documents: DocumentRepository
    @Autowired protected lateinit var cleanup: ObjectCleanupRepository
    @Autowired protected lateinit var scope: TransactionRunner

    protected fun readyRevision(
        f: Fixture,
        documentId: UUID = UUID.randomUUID(),
        bytes: ByteArray = pdf,
        version: Long = 0,
    ): UUID {
        val rev = filled(f, bytes, documentId, version)
        assertTrue(run(f, beginValidation(f, rev)) is Result.Success)
        assertEquals("READY", revision(f, rev).get("status").asString())
        return rev
    }

    protected fun retire(
        f: Fixture,
        id: UUID,
        revisionVersion: Long = revision(f, id).get("version").asLong(),
        retentionVersion: Long = 0,
        key: UUID = UUID.randomUUID(),
        reason: String = "Retention expired",
    ) =
        command(
            f.browser,
            "${f.path}/revisions/$id/retire",
            json.writeValueAsString(
                mapOf(
                    "expectedRevisionVersion" to revisionVersion,
                    "expectedRetentionVersion" to retentionVersion,
                    "reason" to reason,
                )
            ),
            f.csrf,
            key,
        )

    protected fun elapsedRetention(f: Fixture): Fixture {
        clock.set(clock.instant().plusSeconds(86401))
        return f.copy(
            csrf = login(f.browser),
            actor = f.actor.copy(authenticatedAt = clock.instant()),
        )
    }

    protected fun published(f: Fixture, id: UUID) =
        json.readTree(get(f.browser, "${f.path}/$id").body())

    protected fun reserved(f: Fixture): Long {
        val result =
            scope.run(f.actor) {
                documents.capacity(f.company, f.actor.accountId).flatMap { usage ->
                    cleanup.allocatedBytes(f.company).map {
                        it + usage.readyBytes + usage.unfilledBytes
                    }
                }
            }
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    protected fun content(f: Fixture, id: UUID): HttpResponse<ByteArray> =
        f.browser.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port${f.path}/revisions/$id/content"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )

    protected fun cleanupCollector() =
        documentCleanupWorker(postgres.jdbcUrl, database(), storageProbe, json)

    protected fun due(revision: UUID) {
        database()
            .update(
                "update object_cleanup_queue set eligible_at=clock_timestamp()-interval '1 second',version=version+1 where resource_id=?",
                revision,
            )
    }
}
