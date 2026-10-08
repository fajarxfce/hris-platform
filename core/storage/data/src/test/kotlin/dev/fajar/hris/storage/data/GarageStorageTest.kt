package dev.fajar.hris.storage.data

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.storage.data.datasources.S3ObjectStorageDataSource
import dev.fajar.hris.storage.data.di.*
import dev.fajar.hris.storage.data.repositories.PrivateObjectStorageRepository
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

class GarageContainer : GenericContainer<GarageContainer>("dxflrs/garage:v2.4.1")

@Testcontainers
class GarageStorageTest {
    companion object {
        private const val access = "GK0123456789abcdef0123456789abcdef"
        private const val secret =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        private val config =
            """
            metadata_dir = "/tmp/meta"
            data_dir = "/tmp/data"
            db_engine = "sqlite"
            replication_factor = 1
            rpc_bind_addr = "[::]:3901"
            rpc_public_addr = "127.0.0.1:3901"
            rpc_secret = "$secret"
            [s3_api]
            s3_region = "garage"
            api_bind_addr = "[::]:3900"
            root_domain = ".s3.garage.localhost"
            [admin]
            api_bind_addr = "[::]:3903"
            admin_token = "test-fixture-only"
        """
                .trimIndent()
        @Container
        @JvmStatic
        val garage =
            GarageContainer()
                .withCopyToContainer(Transferable.of(config), "/etc/garage.toml")
                .withEnv("GARAGE_DEFAULT_ACCESS_KEY", access)
                .withEnv("GARAGE_DEFAULT_SECRET_KEY", secret)
                .withEnv("GARAGE_DEFAULT_BUCKET", "hris-tests")
                .withCommand("/garage", "server", "--single-node", "--default-bucket")
                .withExposedPorts(3900, 3903)
                .waitingFor(Wait.forHttp("/health").forPort(3903))
                .withStartupTimeout(Duration.ofSeconds(60))
    }

    private fun settings() =
        ObjectStorageSettings(
            URI("http://${garage.host}:${garage.getMappedPort(3900)}"),
            "garage",
            "hris-tests",
            access,
            secret,
            true,
        )

    @Test
    fun privateObjectsHaveExactConditionalRangesOnGarage() {
        S3ObjectStorageDataSource(createObjectStorageClient(settings()), "hris-tests").use { source
            ->
            val repo = PrivateObjectStorageRepository(source)
            val key = "fixtures/${UUID.randomUUID()}"
            val bytes = "private document chunk".toByteArray()
            val saved = repo.put(key, bytes)
            assertTrue(saved is Result.Success, saved.toString())
            val stored = (saved as Result.Success).value
            assertEquals(bytes.size.toLong(), stored.size)
            assertEquals(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                stored.sha256,
            )
            assertEquals(saved, repo.metadata(key))
            assertArrayEquals(
                bytes.copyOfRange(2, 8),
                (repo.read(key, 2, 6, stored.etag) as Result.Success).value,
            )
            // Garage does not enforce conditional PUT; document attempts use distinct keys.
            val replacement = (repo.put(key, "replacement".toByteArray()) as Result.Success).value
            assertEquals(
                FailureKind.CONFLICT,
                (repo.read(key, 0, 2, stored.etag) as Result.Failed).failure.kind,
            )
            assertEquals(
                FailureKind.CONFLICT,
                (repo.read(key, 0, 2, "\"changed\"") as Result.Failed).failure.kind,
            )
            assertEquals(
                FailureKind.VALIDATION,
                (repo.read(key, bytes.size.toLong() + 1, 1, replacement.etag) as Result.Failed)
                    .failure
                    .kind,
            )
            HttpClient.newHttpClient().use { client ->
                val response =
                    client.send(
                        HttpRequest.newBuilder(URI("${settings().endpoint}/hris-tests/$key"))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build(),
                        HttpResponse.BodyHandlers.ofString(),
                    )
                assertTrue(
                    response.statusCode() in setOf(400, 403),
                    response.statusCode().toString(),
                )
                assertFalse(response.body().contains("private document chunk"))
            }
            assertEquals(Result.Success(Unit), repo.delete(key))
            assertEquals(Result.Success(Unit), repo.delete(key))
            assertEquals(Result.Success<Any?>(null), repo.metadata(key))
        }
    }

    @Test
    fun maximumChunkSizeAndLateInvalidRangesCannotExposePartialContent() {
        S3ObjectStorageDataSource(createObjectStorageClient(settings()), "hris-tests").use { source
            ->
            val repo = PrivateObjectStorageRepository(source)
            val key = "fixtures/${UUID.randomUUID()}"
            val bytes = ByteArray(5 * 1024 * 1024) { (it % 251).toByte() }
            val stored = (repo.put(key, bytes) as Result.Success).value
            assertArrayEquals(
                bytes,
                (repo.read(key, 0, bytes.size, stored.etag) as Result.Success).value,
            )
            assertEquals(
                FailureKind.UNAVAILABLE,
                (repo.read(key, bytes.size.toLong() - 1, 2, stored.etag) as Result.Failed)
                    .failure
                    .kind,
            )
            assertEquals(
                FailureKind.VALIDATION,
                (repo.put("../escape", bytes) as Result.Failed).failure.kind,
            )
            assertEquals(
                FailureKind.VALIDATION,
                (repo.put(key, ByteArray(bytes.size + 1)) as Result.Failed).failure.kind,
            )
            assertEquals(
                FailureKind.VALIDATION,
                (repo.read(key, Long.MAX_VALUE, 2, stored.etag) as Result.Failed).failure.kind,
            )
            assertEquals(Result.Success(Unit), repo.delete(key))
        }
    }

    @Test
    fun companyInventoryUsesFiniteOrderedPagesOnGarage() {
        S3ObjectStorageDataSource(createObjectStorageClient(settings()), "hris-tests").use { source
            ->
            val repo = PrivateObjectStorageRepository(source)
            val company = UUID.randomUUID()
            val other = UUID.randomUUID()
            val keys = (1..3).map { "$company/000$it" }
            for (key in keys + "$other/foreign") assertTrue(
                repo.put(key, "fixture".toByteArray()) is Result.Success
            )
            val first = repo.list(company, null, 2)
            assertTrue(first is Result.Success, first.toString())
            val page = (first as Result.Success).value
            assertEquals(keys.take(2), page.entries.map { it.key })
            assertTrue(page.hasMore)
            assertTrue(page.entries.all { it.size == 7L && it.etag.isNotBlank() })
            val next = (repo.list(company, page.entries.last().key, 2) as Result.Success).value
            assertEquals(keys.drop(2), next.entries.map { it.key })
            assertFalse(next.hasMore)
            assertTrue(
                (repo.list(company, keys.last(), 2) as Result.Success).value.entries.isEmpty()
            )
            assertTrue(
                (repo.list(UUID.randomUUID(), null, 2) as Result.Success).value.entries.isEmpty()
            )
            for (key in keys + "$other/foreign") assertEquals(
                Result.Success(Unit),
                repo.delete(key),
            )
        }
    }
}
