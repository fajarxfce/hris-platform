package dev.fajar.hris.worker

import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceProvisioning
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceProvisioningRepository
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.worker.collaboration.CollaborationSpaceProvisioningWorker
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSpaceProvisioningContextTest {
    companion object {
        @Container
        @JvmStatic
        val postgres =
            PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))
                .withInitScript("worker-role.sql")
    }

    @Test
    fun actualWorkerClaimsOnlyWithExplicitProvisioningAndAllRequiredCapabilities() {
        val directory =
            Files.createTempDirectory(
                "space-provisioning-context-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")),
            )
        val signing = directory.resolve("matrix-signing")
        try {
            Files.createFile(
                signing,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")),
            )
            Files.writeString(signing, UUID.randomUUID().toString() + UUID.randomUUID())
            val flags =
                listOf(
                    "HRIS_COLLABORATION_SPACE_PROVISIONING_ENABLED",
                    "HRIS_COLLABORATION_ENABLED",
                    "HRIS_MATRIX_ENABLED",
                    "HRIS_COLLABORATION_SPACE_ENABLED",
                    "hris.worker.enabled",
                )
            for (disabled in listOf(null, "default") + flags) {
                val properties =
                    mutableMapOf(
                        "spring.datasource.url" to postgres.jdbcUrl,
                        "spring.datasource.username" to "hris_worker_test",
                        "spring.datasource.password" to "worker-fixture-only",
                        "spring.flyway.enabled" to "true",
                        "spring.flyway.url" to postgres.jdbcUrl,
                        "spring.flyway.user" to postgres.username,
                        "spring.flyway.password" to postgres.password,
                        "spring.main.banner-mode" to "off",
                        "HRIS_IDENTITY_ACTIVE_KEY" to "fixture",
                        "HRIS_IDENTITY_KEYS" to
                            ("fixture:" + Base64.getEncoder().encodeToString(ByteArray(32) { 17 })),
                        "HRIS_MATRIX_ORIGIN" to "http://127.0.0.1:9",
                        "HRIS_MATRIX_SERVER_NAME" to "matrix.example.test",
                        "HRIS_MATRIX_JWT_ISSUER" to "urn:hris:matrix:fixture",
                        "HRIS_MATRIX_JWT_AUDIENCE" to "matrix:fixture",
                        "HRIS_MATRIX_JWT_SECRET_FILE" to signing.toString(),
                    )
                flags.filter { it != "hris.worker.enabled" }.forEach { properties[it] = "true" }
                if (disabled == "default")
                    properties.remove(flags.first()) // Real missing-property default.
                else if (disabled != null) properties[disabled] = "false"
                val context =
                    SpringApplicationBuilder(
                            WorkerApplication::class.java,
                            CollaborationSpaceProvisioningContextProbeConfiguration::class.java,
                        )
                        .web(WebApplicationType.NONE)
                        .registerShutdownHook(false)
                        .initializers(
                            org.springframework.context.ApplicationContextInitializer<
                                org.springframework.context.ConfigurableApplicationContext
                            > { application ->
                                // Programmatic Boot startup needs the same test-component exclusion
                                // that
                                // SpringBootTest normally installs, without replacing production
                                // scanning.
                                application.beanFactory.registerSingleton(
                                    "ownedWorkerTestScanFilter",
                                    CollaborationPushContextScanFilter(),
                                )
                            }
                        )
                        .run(*properties.map { (key, value) -> "--$key=$value" }.toTypedArray())
                val tasks =
                    context.getBean(
                        "collaborationSpaceProvisioningTasks",
                        ThreadPoolTaskExecutor::class.java,
                    )
                val timers =
                    context.getBean(
                        "collaborationSpaceProvisioningTimers",
                        ThreadPoolTaskScheduler::class.java,
                    )
                val workers =
                    context.getBeansOfType(CollaborationSpaceProvisioningWorker::class.java)
                val probe = context.getBean(CollaborationSpaceProvisioningContextProbe::class.java)
                try {
                    assertEquals(2, tasks.corePoolSize)
                    assertEquals(2, tasks.maxPoolSize)
                    assertEquals(2, timers.scheduledThreadPoolExecutor.corePoolSize)
                    assertTrue(tasks.threadPoolExecutor.queue.isEmpty())
                    if (disabled == null) {
                        val worker = workers.values.single()
                        assertTrue(worker.isRunning)
                        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                        while (probe.claims.get() == 0 && System.nanoTime() < until) TimeUnit
                            .MILLISECONDS
                            .sleep(10)
                        assertTrue(probe.claims.get() > 0, "Enabled actual worker never claimed")
                        assertEquals(0, probe.failedClaims.get())
                    } else {
                        assertTrue(
                            workers.isEmpty(),
                            "Disabled capability still created a worker: $disabled",
                        )
                        TimeUnit.MILLISECONDS.sleep(100)
                        assertEquals(0, probe.claims.get())
                        assertEquals(0, probe.failedClaims.get())
                        assertTrue(timers.scheduledThreadPoolExecutor.queue.isEmpty())
                    }
                } finally {
                    context.close()
                }
                assertTrue(workers.values.none { it.isRunning })
                assertTrue(tasks.threadPoolExecutor.isTerminated)
                assertTrue(timers.scheduledThreadPoolExecutor.isTerminated)
            }
        } finally {
            Files.deleteIfExists(signing)
            Files.deleteIfExists(directory)
        }
    }
}

class CollaborationSpaceProvisioningContextProbe {
    val claims = AtomicInteger()
    val failedClaims = AtomicInteger()
}

@TestConfiguration(proxyBeanMethods = false)
class CollaborationSpaceProvisioningContextProbeConfiguration {
    @Bean
    fun collaborationSpaceProvisioningContextProbe() = CollaborationSpaceProvisioningContextProbe()

    @Bean
    @Primary
    fun observedCollaborationSpaceProvisioningQueue(
        @Qualifier("collaborationSpaceProvisioningRepository")
        delegate: CollaborationSpaceProvisioningRepository,
        probe: CollaborationSpaceProvisioningContextProbe,
    ): CollaborationSpaceProvisioningRepository =
        object : CollaborationSpaceProvisioningRepository by delegate {
            override fun claim(
                ownerId: UUID,
                limit: Int,
                leaseSeconds: Int,
            ): Result<List<CollaborationSpaceProvisioning>> {
                check(limit in 1..2 && leaseSeconds == 60)
                val result = delegate.claim(ownerId, limit, leaseSeconds)
                if (result is Result.Success) probe.claims.incrementAndGet()
                else probe.failedClaims.incrementAndGet()
                return result
            }
        }
}
