package dev.fajar.hris.worker

import dev.fajar.hris.collaboration.domain.entities.CollaborationPushHead
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpacePushHead
import dev.fajar.hris.collaboration.domain.repositories.CollaborationPushQueueRepository
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpacePushQueueRepository
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.worker.push.CollaborationPushWorker
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyPairGenerator
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
import tools.jackson.databind.json.JsonMapper

@Testcontainers
class CollaborationPushContextTest {
    companion object {
        @Container
        @JvmStatic
        val postgres =
            PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))
                .withInitScript("worker-role.sql")
    }

    @Test
    fun actualWorkerOnlyClaimsWithExplicitDeliveryAndAllRequiredCapabilities() {
        val directory =
            Files.createTempDirectory(
                "chat-push-context-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")),
            )
        val signing = directory.resolve("matrix-signing")
        val credentials = directory.resolve("fcm.json")
        try {
            for (file in listOf(signing, credentials)) Files.createFile(
                file,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")),
            )
            Files.writeString(signing, UUID.randomUUID().toString() + UUID.randomUUID())
            val pair =
                KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val pem =
                "-----BEGIN PRIVATE KEY-----\n" +
                    Base64.getMimeEncoder(64, byteArrayOf(10))
                        .encodeToString(pair.private.encoded) +
                    "\n-----END PRIVATE KEY-----\n"
            Files.writeString(
                credentials,
                JsonMapper.builder()
                    .build()
                    .writeValueAsString(
                        mapOf(
                            "type" to "service_account",
                            "project_id" to "worker-push-fixture",
                            "client_email" to "fixture@worker-push-fixture.iam.gserviceaccount.com",
                            "client_id" to "123456789012345678901",
                            "private_key_id" to "fixture",
                            "private_key" to pem,
                            "token_uri" to "https://oauth2.googleapis.com/token",
                        )
                    ),
            )
            val flags =
                listOf(
                    "HRIS_COLLABORATION_PUSH_DELIVERY_ENABLED",
                    "HRIS_FCM_ENABLED",
                    "HRIS_COLLABORATION_ENABLED",
                    "HRIS_MATRIX_ENABLED",
                    "HRIS_COLLABORATION_PUSH_ENABLED",
                    "HRIS_COLLABORATION_SPACE_ENABLED",
                    "hris.worker.enabled",
                )
            for (disabled in listOf<String?>(null) + flags) {
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
                        "HRIS_MATRIX_PUSH_GATEWAY_URL" to
                            "https://push.example.test/_matrix/push/v1/notify",
                        "HRIS_FCM_PROJECT_ID" to "worker-push-fixture",
                        "HRIS_FCM_CREDENTIALS_FILE" to credentials.toString(),
                    )
                flags.filter { it != "hris.worker.enabled" }.forEach { properties[it] = "true" }
                if (disabled == flags.first())
                    properties.remove(disabled) // Real missing-property default.
                else if (disabled != null) properties[disabled] = "false"
                val context =
                    SpringApplicationBuilder(
                            WorkerApplication::class.java,
                            CollaborationPushContextProbeConfiguration::class.java,
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
                    context.getBean("collaborationPushTasks", ThreadPoolTaskExecutor::class.java)
                val timers =
                    context.getBean("collaborationPushTimers", ThreadPoolTaskScheduler::class.java)
                val groupTasks =
                    context.getBean(
                        "collaborationSpacePushTasks",
                        ThreadPoolTaskExecutor::class.java,
                    )
                val groupTimers =
                    context.getBean(
                        "collaborationSpacePushTimers",
                        ThreadPoolTaskScheduler::class.java,
                    )
                val workers = context.getBeansOfType(CollaborationPushWorker::class.java)
                val probe = context.getBean(CollaborationPushContextProbe::class.java)
                try {
                    assertEquals(4, tasks.maxPoolSize)
                    assertTrue(tasks.threadPoolExecutor.queue.isEmpty())
                    if (disabled == null || disabled == "HRIS_COLLABORATION_SPACE_ENABLED") {
                        assertEquals(if (disabled == null) 2 else 1, workers.size)
                        assertTrue(workers.values.all { it.isRunning })
                        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                        while (
                            (probe.claims.get() == 0 ||
                                (disabled == null && probe.spaceClaims.get() == 0)) &&
                                System.nanoTime() < until
                        ) TimeUnit.MILLISECONDS.sleep(10)
                        assertTrue(probe.claims.get() > 0, "Enabled actual worker never claimed")
                        assertEquals(0, probe.failedClaims.get())
                        if (disabled == null) assertTrue(probe.spaceClaims.get() > 0)
                        else assertEquals(0, probe.spaceClaims.get())
                        assertEquals(0, probe.failedSpaceClaims.get())
                    } else {
                        assertTrue(
                            workers.isEmpty(),
                            "Disabled capability still created a worker: $disabled",
                        )
                        TimeUnit.MILLISECONDS.sleep(100)
                        assertEquals(0, probe.claims.get())
                        assertEquals(0, probe.spaceClaims.get())
                        assertEquals(0, probe.failedSpaceClaims.get())
                        assertEquals(0, probe.failedClaims.get())
                        assertTrue(timers.scheduledThreadPoolExecutor.queue.isEmpty())
                        assertTrue(groupTimers.scheduledThreadPoolExecutor.queue.isEmpty())
                    }
                } finally {
                    context.close()
                }
                assertTrue(workers.values.none { it.isRunning })
                assertEquals(4, groupTasks.maxPoolSize)
                assertTrue(groupTasks.threadPoolExecutor.isTerminated)
                assertTrue(groupTimers.scheduledThreadPoolExecutor.isTerminated)
                assertTrue(tasks.threadPoolExecutor.isTerminated)
                assertTrue(timers.scheduledThreadPoolExecutor.isTerminated)
            }
        } finally {
            Files.deleteIfExists(credentials)
            Files.deleteIfExists(signing)
            Files.deleteIfExists(directory)
        }
    }
}

class CollaborationPushContextProbe {
    val claims = AtomicInteger()
    val failedClaims = AtomicInteger()
    val spaceClaims = AtomicInteger()
    val failedSpaceClaims = AtomicInteger()
}

@TestConfiguration(proxyBeanMethods = false)
class CollaborationPushContextProbeConfiguration {
    @Bean fun collaborationPushContextProbe() = CollaborationPushContextProbe()

    @Bean
    @Primary
    fun observedCollaborationSpacePushQueue(
        @Qualifier("collaborationSpacePushQueue") delegate: CollaborationSpacePushQueueRepository,
        probe: CollaborationPushContextProbe,
    ): CollaborationSpacePushQueueRepository =
        object : CollaborationSpacePushQueueRepository by delegate {
            override fun claim(
                ownerId: UUID,
                limit: Int,
                leaseSeconds: Int,
                maximumAttempts: Int,
            ): Result<List<CollaborationSpacePushHead>> {
                check(limit in 1..4 && leaseSeconds == 60 && maximumAttempts == 8)
                val result = delegate.claim(ownerId, limit, leaseSeconds, maximumAttempts)
                if (result is Result.Success) probe.spaceClaims.incrementAndGet()
                else probe.failedSpaceClaims.incrementAndGet()
                return result
            }
        }

    @Bean
    @Primary
    fun observedCollaborationPushQueue(
        @Qualifier("collaborationPushQueue") delegate: CollaborationPushQueueRepository,
        probe: CollaborationPushContextProbe,
    ): CollaborationPushQueueRepository =
        object : CollaborationPushQueueRepository by delegate {
            override fun claim(
                ownerId: UUID,
                limit: Int,
                leaseSeconds: Int,
                maximumAttempts: Int,
            ): Result<List<CollaborationPushHead>> {
                check(limit in 1..4 && leaseSeconds == 60 && maximumAttempts == 8)
                val result = delegate.claim(ownerId, limit, leaseSeconds, maximumAttempts)
                if (result is Result.Success) probe.claims.incrementAndGet()
                else probe.failedClaims.incrementAndGet()
                return result
            }
        }
}

class CollaborationPushContextScanFilter : org.springframework.boot.context.TypeExcludeFilter() {
    override fun match(
        metadata: org.springframework.core.type.classreading.MetadataReader,
        factory: org.springframework.core.type.classreading.MetadataReaderFactory,
    ): Boolean =
        metadata.annotationMetadata.isAnnotated(
            org.springframework.boot.test.context.TestComponent::class.java.name
        )

    override fun equals(other: Any?) = other is CollaborationPushContextScanFilter

    override fun hashCode() = javaClass.hashCode()
}
