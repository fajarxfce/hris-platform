package dev.fajar.hris.worker

import dev.fajar.hris.collaboration.domain.entities.CollaborationReadinessLease
import dev.fajar.hris.collaboration.domain.repositories.CollaborationReadinessControlQueueRepository
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.worker.collaboration.CollaborationReadinessWorker
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
class CollaborationReadinessControlContextTest {
    companion object {
        @Container
        @JvmStatic
        val postgres =
            PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))
                .withInitScript("worker-role.sql")
    }

    @Test
    fun matrixControlWorkerRunsWithoutPushAndRequiresEveryOwnCapability() {
        val directory =
            Files.createTempDirectory(
                "chat-readiness-context-",
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
                    "HRIS_COLLABORATION_READINESS_CONTROL_ENABLED",
                    "HRIS_COLLABORATION_ENABLED",
                    "HRIS_MATRIX_ENABLED",
                    "hris.worker.enabled",
                )
            for (disabled in listOf<String?>(null) + flags) {
                val properties =
                    mutableMapOf(
                        "spring.datasource.url" to postgres.jdbcUrl,
                        "spring.datasource.username" to "hris_worker_test",
                        "spring.datasource.password" to "worker-fixture-only",
                        "spring.flyway.enabled" to "true",
                        "spring.flyway.target" to "116.4",
                        "spring.flyway.url" to postgres.jdbcUrl,
                        "spring.flyway.user" to postgres.username,
                        "spring.flyway.password" to postgres.password,
                        "spring.main.banner-mode" to "off",
                        "HRIS_IDENTITY_ACTIVE_KEY" to "fixture",
                        "HRIS_IDENTITY_KEYS" to
                            ("fixture:" + Base64.getEncoder().encodeToString(ByteArray(32) { 17 })),
                        "HRIS_FCM_ENABLED" to "false",
                        "HRIS_COLLABORATION_PUSH_ENABLED" to "false",
                        "HRIS_COLLABORATION_READINESS_ENABLED" to "false",
                        "HRIS_MATRIX_ORIGIN" to "http://127.0.0.1:9",
                        "HRIS_MATRIX_SERVER_NAME" to "matrix.example.test",
                        "HRIS_MATRIX_JWT_ISSUER" to "urn:hris:matrix:fixture",
                        "HRIS_MATRIX_JWT_AUDIENCE" to "matrix:fixture",
                        "HRIS_MATRIX_JWT_SECRET_FILE" to signing.toString(),
                        "HRIS_MATRIX_PUSH_GATEWAY_URL" to
                            "https://push.example.test/_matrix/push/v1/notify",
                    )
                flags.filter { it != "hris.worker.enabled" }.forEach { properties[it] = "true" }
                if (disabled == flags.first())
                    properties.remove(disabled) // Real missing-property default.
                else if (disabled != null) properties[disabled] = "false"
                val context =
                    SpringApplicationBuilder(
                            WorkerApplication::class.java,
                            CollaborationReadinessControlContextProbeConfiguration::class.java,
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
                                    CollaborationReadinessControlContextScanFilter(),
                                )
                            }
                        )
                        .run(*properties.map { (key, value) -> "--$key=$value" }.toTypedArray())
                val tasks =
                    context.getBean(
                        "collaborationReadinessControlTasks",
                        ThreadPoolTaskExecutor::class.java,
                    )
                val timers =
                    context.getBean(
                        "collaborationReadinessControlTimers",
                        ThreadPoolTaskScheduler::class.java,
                    )
                val workers = context.getBeansOfType(CollaborationReadinessWorker::class.java)
                val probe = context.getBean(CollaborationReadinessControlContextProbe::class.java)
                try {
                    assertEquals(2, tasks.maxPoolSize)
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

class CollaborationReadinessControlContextProbe {
    val claims = AtomicInteger()
    val failedClaims = AtomicInteger()
}

@TestConfiguration(proxyBeanMethods = false)
class CollaborationReadinessControlContextProbeConfiguration {
    @Bean
    fun collaborationReadinessControlContextProbe() = CollaborationReadinessControlContextProbe()

    @Bean
    @Primary
    fun observedCollaborationReadinessWakes(
        @Qualifier("collaborationReadinessControls")
        delegate: CollaborationReadinessControlQueueRepository,
        probe: CollaborationReadinessControlContextProbe,
    ): CollaborationReadinessControlQueueRepository =
        object : CollaborationReadinessControlQueueRepository by delegate {
            override fun claim(
                ownerId: UUID,
                limit: Int,
            ): Result<List<CollaborationReadinessLease>> {
                check(limit in 1..2)
                val result = delegate.claim(ownerId, limit)
                if (result is Result.Success) probe.claims.incrementAndGet()
                else probe.failedClaims.incrementAndGet()
                return result
            }
        }
}

class CollaborationReadinessControlContextScanFilter :
    org.springframework.boot.context.TypeExcludeFilter() {
    override fun match(
        metadata: org.springframework.core.type.classreading.MetadataReader,
        factory: org.springframework.core.type.classreading.MetadataReaderFactory,
    ): Boolean =
        metadata.annotationMetadata.isAnnotated(
            org.springframework.boot.test.context.TestComponent::class.java.name
        )

    override fun equals(other: Any?) = other is CollaborationReadinessControlContextScanFilter

    override fun hashCode() = javaClass.hashCode()
}
