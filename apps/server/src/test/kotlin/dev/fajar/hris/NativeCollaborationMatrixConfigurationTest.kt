package dev.fajar.hris

import dev.fajar.hris.collaboration.data.matrix.MatrixHttpClientOwner
import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixPolicy
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource

@TestPropertySource(
    properties =
        [
            "HRIS_COLLABORATION_ENABLED=true",
            "HRIS_MATRIX_ENABLED=true",
            "HRIS_MATRIX_ORIGIN=http://127.0.0.1:9",
            "HRIS_MATRIX_SERVER_NAME=matrix.example.test",
            "HRIS_MATRIX_JWT_ISSUER=urn:hris:matrix:fixture",
            "HRIS_MATRIX_JWT_AUDIENCE=matrix:fixture",
        ]
)
class NativeCollaborationMatrixConfigurationTest : NativeCollaborationApiFixture() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun matrix(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_MATRIX_JWT_SECRET_FILE") {
                Path.of(
                        requireNotNull(
                                NativeCollaborationMatrixConfigurationTest::class
                                    .java
                                    .getResource("/matrix-signing.fixture")
                            )
                            .toURI()
                    )
                    .toString()
            }
        }
    }

    @Autowired private lateinit var owner: MatrixHttpClientOwner
    @Autowired private lateinit var policy: CollaborationMatrixPolicy

    @Test
    fun enabledApiGraphRegistersWithoutWaitingForItsUnavailablePrivateProvider() {
        assertTrue(policy.enabled)
        assertEquals("matrix.example.test", policy.serverName)
        assertFalse(owner.client.isTerminated)
        val n = native()
        val response = register(n)
        assertEquals(200, response.statusCode(), response.body())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_provider_sessions where company_id=?",
                    Int::class.java,
                    n.fixture.company,
                ),
        )
        assertEquals(
            "QUEUED",
            database()
                .queryForObject(
                    "select j.status from collaboration_device_provisioning p join background_jobs j on j.id=p.job_id where p.company_id=?",
                    String::class.java,
                    n.fixture.company,
                ),
        )
    }
}
