package dev.fajar.hris.documents.data

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.data.datasources.*
import dev.fajar.hris.documents.data.repositories.InspectedDocumentRepository
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

class ClamAvTestContainer : GenericContainer<ClamAvTestContainer>("clamav/clamav:1.5.4_base")

@Testcontainers
class ClamAvInspectionTest {
    companion object {
        private val config =
            """
            Foreground yes
            TCPSocket 3310
            TCPAddr 0.0.0.0
            DatabaseDirectory /tmp
            TemporaryDirectory /tmp
            PidFile /tmp/clamd.pid
            MaxThreads 2
            ReadTimeout 5
            CommandReadTimeout 5
            StreamMaxLength 105M
            MaxFileSize 105M
            MaxScanSize 110M
            MaxRecursion 10
            MaxFiles 1000
            MaxScanTime 70000
            AlertExceedsMax yes
            """
                .trimIndent()
        @Container
        @JvmStatic
        val scanner =
            ClamAvTestContainer()
                .withCreateContainerCmdModifier { it.withEntrypoint("clamd") }
                .withCommand("--foreground=true", "--config-file=/tmp/hris-clamd.conf")
                .withCopyToContainer(Transferable.of(config), "/tmp/hris-clamd.conf")
                .withCopyToContainer(
                    Transferable.of("44d88612fea8a8f36de82e1278abb02f:68:HRIS.EICAR.Fixture\n"),
                    "/tmp/hris-fixture.hdb",
                )
                .withExposedPorts(3310)
                .waitingFor(Wait.forListeningPorts(3310))
                .withStartupTimeout(Duration.ofSeconds(60))
    }

    private fun source() =
        ClamAvDocumentScanDataSource(
            ClamAvSettings(InetSocketAddress(scanner.host, scanner.getMappedPort(3310)))
        )

    @Test
    fun aRealEngineScansBoundedPartsAndReportsTheirObservedDigestAndMediaType() {
        val image =
            java.util.Base64.getDecoder()
                .decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jGJkAAAAASUVORK5CYII="
                )
        val content = image + ByteArray(1048576 + 17)
        val storage =
            InspectionStorageFixture(
                listOf(content.copyOfRange(0, 1048576), content.copyOfRange(1048576, content.size))
            )
        val result =
            InspectedDocumentRepository(storage, source(), TikaDocumentMediaTypeDataSource())
                .inspect(storage.manifest)
        assertTrue(result is Result.Success, result.toString())
        val report = (result as Result.Success).value
        assertTrue(report.clean)
        assertEquals("image/png", report.mediaType)
        assertEquals(content.size.toLong(), report.size)
        assertEquals(
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)),
            report.sha256,
        )
        assertTrue(report.engineVersion.startsWith("ClamAV 1.5.4"), report.engineVersion)
    }

    @Test
    fun theHarmlessEicarFixtureIsReportedAsInfectedWithoutHidingTheVerdict() {
        // Public, harmless antivirus test string; the container loads only the owned fixture
        // signature.
        val bytes =
            """X5O!P%@AP[4\PZX54(P^)7CC)7}${'$'}EICAR-STANDARD-ANTIVIRUS-TEST-FILE!${'$'}H+H*"""
                .toByteArray()
        assertEquals(68, bytes.size)
        val storage = InspectionStorageFixture(listOf(bytes))
        val result =
            InspectedDocumentRepository(storage, source(), TikaDocumentMediaTypeDataSource())
                .inspect(storage.manifest)
        assertTrue(result is Result.Success, result.toString())
        assertFalse((result as Result.Success).value.clean)
    }
}
