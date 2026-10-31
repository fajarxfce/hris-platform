package dev.fajar.hris.worker

import dev.fajar.hris.administration.domain.entities.ClientPlatform
import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.data.matrix.MatrixHttpClientOwner
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationProviderSessionRepository
import dev.fajar.hris.collaboration.domain.usecases.RegisterNativeCollaborationDevice
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.usecases.LeaseJobs
import dev.fajar.hris.worker.runtime.*
import dev.fajar.hris.worker.tasks.CollaborationProvisioningTask
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.json.JsonMapper

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties =
        [
            "hris.worker.enabled=false",
            "HRIS_COLLABORATION_ENABLED=true",
            "HRIS_MATRIX_ENABLED=true",
            "HRIS_IDENTITY_ACTIVE_KEY=fixture",
        ],
)
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS
)
class CollaborationProvisioningWorkerTest {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18.6-alpine").withInitScript("worker-role.sql")
        @Container @JvmStatic val matrix = WorkerMatrixFixture()

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_IDENTITY_KEYS") {
                "fixture:" + java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 17 })
            }
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { "hris_worker_test" }
            registry.add("spring.datasource.password") { "worker-fixture-only" }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.url") { postgres.jdbcUrl }
            registry.add("spring.flyway.user") { postgres.username }
            registry.add("spring.flyway.password") { postgres.password }
            registry.add("HRIS_MATRIX_ORIGIN") { matrix.origin }
            registry.add("HRIS_MATRIX_SERVER_NAME") { "matrix.example.test" }
            registry.add("HRIS_MATRIX_JWT_ISSUER") { "urn:hris:matrix:fixture" }
            registry.add("HRIS_MATRIX_JWT_AUDIENCE") { "matrix:fixture" }
            registry.add("HRIS_MATRIX_JWT_SECRET_FILE") { matrix.signingFile.toString() }
        }
    }

    @Autowired private lateinit var register: RegisterNativeCollaborationDevice
    @Autowired private lateinit var lease: LeaseJobs
    @Autowired private lateinit var task: CollaborationProvisioningTask
    @Autowired private lateinit var batch: BatchJobExecutor
    @Autowired private lateinit var runner: JobRunExecutor
    @Autowired private lateinit var credentials: CollaborationProviderSessionRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var http: MatrixHttpClientOwner

    private fun database() =
        JdbcTemplate(
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        )

    private data class Fixture(val actor: Actor, val parent: UUID, val device: UUID, val job: UUID)

    private fun fixture(): Fixture {
        val account = UUID.randomUUID()
        val company = UUID.randomUUID()
        val parent = UUID.randomUUID()
        val sql = database()
        sql.update(
            "insert into companies(id,code,name,timezone) values(?,?,'Collaboration worker fixture','UTC')",
            company,
            company.toString().take(12),
        )
        sql.update(
            "insert into accounts(id,email,display_name) values(?,?,'Fictional worker employee')",
            account,
            "$account@example.test",
        )
        sql.update(
            "insert into company_memberships(company_id,account_id) values(?,?)",
            company,
            account,
        )
        sql.update(
            "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
            company,
            account,
        )
        val hash = parent.toString().replace("-", "").repeat(2)
        sql.update(
            """insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,access_hash,access_expires_at,credential_version,created_at,rotated_at)
            values(?,?,?,now()-interval '1 minute',now()+interval '1 day',?,now()+interval '15 minutes',0,now()-interval '1 minute',now()-interval '1 minute')""",
            parent,
            account,
            hash,
            hash,
        )
        val actor =
            Actor(
                account,
                company,
                setOf("collaboration.chat"),
                Instant.now().minusSeconds(60),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val device =
            success(
                    register.execute(
                        actor,
                        parent,
                        0,
                        UUID.randomUUID(),
                        CollaborationPlatform.ANDROID,
                        "Fictional worker device",
                        ClientVersion(ClientPlatform.ANDROID, 1),
                    )
                )
                .id
        val job =
            requireNotNull(
                sql.queryForObject(
                    "select job_id from collaboration_device_provisioning where company_id=? and device_id=?",
                    UUID::class.java,
                    company,
                    device,
                )
            )
        return Fixture(actor, parent, device, job)
    }

    private fun claim(fixture: Fixture): JobLease {
        val result =
            success(lease.execute(UUID.randomUUID(), 1, 60, setOf(JobKind.COLLABORATION_PROVISION)))
                .single()
        assertEquals(fixture.job, result.job.request.id)
        return result
    }

    private fun stored(f: Fixture) =
        success(
            transactions.run(f.actor) {
                credentials.find(requireNotNull(f.actor.companyId), f.actor.accountId, f.device)
            }
        )

    private fun status(f: Fixture) =
        database()
            .queryForMap(
                "select status,attempts,completed_items,failure_code,available_at from background_jobs where id=?",
                f.job,
            )

    private fun deviceState(f: Fixture) =
        database()
            .queryForObject(
                "select state from collaboration_devices where id=?",
                String::class.java,
                f.device,
            )

    @Test
    fun realLeaseBatchProviderAndEncryptedStoreActivateOneStableMatrixIdentity() {
        val f = fixture()
        val claimed = claim(f)
        assertEquals(Result.Success(Unit), batch.execute(claimed, task))
        assertEquals("SUCCEEDED", status(f)["status"])
        assertEquals("ACTIVE", deviceState(f))
        assertEquals(1, status(f)["completed_items"])
        val credential = requireNotNull(stored(f))
        assertEquals(
            "@${collaborationMatrixLocalpart(requireNotNull(f.actor.companyId), f.actor.accountId)}:matrix.example.test",
            credential.matrixUserId,
        )
        assertEquals(collaborationMatrixDeviceId(f.device), credential.matrixDeviceId)
        assertTrue(collaborationProviderCredentialIsCurrent(credential, Instant.now()))
        val encrypted =
            database()
                .queryForObject(
                    "select token_encrypted from collaboration_provider_sessions where device_id=?",
                    String::class.java,
                    f.device,
                )
        assertFalse(requireNotNull(encrypted).contains(credential.accessToken))
        val response =
            http.client.send(
                HttpRequest.newBuilder(URI("${matrix.origin}/_matrix/client/v3/account/whoami"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer ${credential.accessToken}")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(200, response.statusCode())
        val body = JsonMapper.builder().build().readTree(response.body())
        assertEquals(credential.matrixUserId, body.path("user_id").asString())
        assertEquals(credential.matrixDeviceId, body.path("device_id").asString())
        assertEquals("job_lease_lost", (task.advance(claimed) as Result.Failed).failure.code)
        assertEquals(0L, requireNotNull(stored(f)).generation)
        assertEquals(
            1,
            database()
                .queryForObject(
                    """select count(*) from hris_batch.batch_job_execution e join hris_batch.batch_job_execution_params p on p.job_execution_id=e.job_execution_id
            where p.parameter_name='jobId' and p.parameter_value=? and e.status='COMPLETED'""",
                    Int::class.java,
                    f.job.toString(),
                ),
        )
    }

    @Test
    fun revokedOriginAndCancellationStopTheOwnedJobWithoutProviderPublication() {
        for (cancel in listOf(false, true)) {
            val f = fixture()
            if (cancel)
                database()
                    .update(
                        "update background_jobs set cancellation_requested=true where id=?",
                        f.job,
                    )
            else database().update("update accounts set active=false where id=?", f.actor.accountId)
            runner.execute(RunningJob(claim(f)), task)
            // The runtime preserves a requested cancellation on its caller thread. The fixture
            // acknowledges that signal before using the same JUnit thread for assertions.
            assertEquals(cancel, Thread.interrupted())
            assertEquals(if (cancel) "CANCELLED" else "FAILED", status(f)["status"])
            assertEquals("PROVISIONING", deviceState(f))
            assertNull(stored(f))
            assertEquals(
                1,
                database()
                    .queryForObject(
                        "select count(*) from audit_entries where resource_id=? and action='collaboration.provisioning_stopped'",
                        Int::class.java,
                        f.job,
                    ),
            )
        }
    }

    @Test
    fun providerOutageDefersThenRecoversTheSameDeviceThroughTheActualQueue() {
        val f = fixture()
        val claimed = claim(f)
        matrix.dockerClient.pauseContainerCmd(matrix.containerId).exec()
        try {
            runner.execute(RunningJob(claimed), task)
            assertEquals("QUEUED", status(f)["status"])
            assertEquals(1, status(f)["attempts"])
            assertNull(stored(f))
            assertEquals("PROVISIONING", deviceState(f))
        } finally {
            matrix.dockerClient.unpauseContainerCmd(matrix.containerId).exec()
        }
        // Advance only this fictional queue deadline; production backoff remains unchanged.
        database()
            .update(
                "update background_jobs set available_at=now()-interval '1 second' where id=?",
                f.job,
            )
        val retried = claim(f)
        assertEquals(2, retried.job.attempts)
        runner.execute(RunningJob(retried), task)
        assertEquals("SUCCEEDED", status(f)["status"])
        assertEquals("ACTIVE", deviceState(f))
        assertEquals(
            collaborationMatrixDeviceId(f.device),
            requireNotNull(stored(f)).matrixDeviceId,
        )
    }

    private fun <T> success(result: Result<T>): T {
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }
}
