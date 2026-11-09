package dev.fajar.hris.worker

import dev.fajar.hris.collaboration.data.repositories.DisabledCollaborationSpaceMatrixReadinessRepository
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationSpaceMatrixReadinessRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.FanOutCollaborationSpaceReadiness
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.worker.collaboration.CollaborationReadinessWorker
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
class CollaborationSpaceReadinessContextTest {
    companion object {
        @Container
        @JvmStatic
        val postgres =
            PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))
                .withInitScript("worker-role.sql")
    }

    @Test
    fun groupChannelsClaimIndependentlyOnlyWithTheirCapabilitiesAndCloseTheirOwnedPools() {
        val directory =
            Files.createTempDirectory(
                "space-readiness-context-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")),
            )
        val signing = directory.resolve("matrix-signing")
        val credentials = directory.resolve("fcm.json")
        try {
            for (path in listOf(signing, credentials)) Files.createFile(
                path,
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
            val common =
                setOf(
                    "HRIS_COLLABORATION_SPACE_ENABLED",
                    "HRIS_COLLABORATION_ENABLED",
                    "HRIS_MATRIX_ENABLED",
                    "hris.worker.enabled",
                )
            val matrix = common + "HRIS_COLLABORATION_READINESS_CONTROL_ENABLED"
            val push =
                common +
                    setOf(
                        "HRIS_COLLABORATION_READINESS_ENABLED",
                        "HRIS_COLLABORATION_PUSH_DELIVERY_ENABLED",
                        "HRIS_FCM_ENABLED",
                        "HRIS_COLLABORATION_PUSH_ENABLED",
                    )
            for (disabled in listOf<String?>(null) + (matrix + push)) {
                val properties =
                    mutableMapOf(
                        "spring.datasource.url" to postgres.jdbcUrl,
                        "spring.datasource.username" to "hris_worker_test",
                        "spring.datasource.password" to "worker-fixture-only",
                        "spring.flyway.enabled" to "true",
                        "spring.flyway.target" to "140",
                        "spring.flyway.url" to postgres.jdbcUrl,
                        "spring.flyway.user" to postgres.username,
                        "spring.flyway.password" to postgres.password,
                        "spring.main.banner-mode" to "off",
                        "HRIS_COLLABORATION_SPACE_MIN_ANDROID_BUILD" to "5",
                        "HRIS_COLLABORATION_SPACE_MIN_IOS_BUILD" to "5",
                        "HRIS_IDENTITY_ACTIVE_KEY" to "fixture",
                        "HRIS_IDENTITY_KEYS" to
                            "fixture:${Base64.getEncoder().encodeToString(ByteArray(32) { 17 })}",
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
                (matrix + push)
                    .filter { it != "hris.worker.enabled" }
                    .forEach { properties[it] = "true" }
                if (disabled == "HRIS_COLLABORATION_SPACE_ENABLED") properties.remove(disabled)
                else if (disabled != null) properties[disabled] = "false"
                val context =
                    SpringApplicationBuilder(
                            WorkerApplication::class.java,
                            CollaborationSpaceReadinessContextProbeConfiguration::class.java,
                        )
                        .web(WebApplicationType.NONE)
                        .registerShutdownHook(false)
                        .initializers(
                            org.springframework.context.ApplicationContextInitializer<
                                org.springframework.context.ConfigurableApplicationContext
                            > { app ->
                                app.beanFactory.registerSingleton(
                                    "ownedWorkerTestScanFilter",
                                    CollaborationSpaceReadinessContextScanFilter(),
                                )
                            }
                        )
                        .run(*properties.map { (key, value) -> "--$key=$value" }.toTypedArray())
                val probe = context.getBean(CollaborationSpaceReadinessContextProbe::class.java)
                val pools = mutableListOf<ThreadPoolTaskExecutor>()
                val timers = mutableListOf<ThreadPoolTaskScheduler>()
                val workers = mutableListOf<CollaborationReadinessWorker<*>>()
                try {
                    assertNotNull(context.getBean(FanOutCollaborationSpaceReadiness::class.java))
                    val transport =
                        context.getBean(CollaborationSpaceMatrixReadinessRepository::class.java)
                    if (disabled in setOf("HRIS_COLLABORATION_ENABLED", "HRIS_MATRIX_ENABLED"))
                        assertInstanceOf(
                            DisabledCollaborationSpaceMatrixReadinessRepository::class.java,
                            transport,
                        )
                    else
                        assertInstanceOf(
                            InternalCollaborationSpaceMatrixReadinessRepository::class.java,
                            transport,
                        )
                    for ((channel, suffix, required) in
                        listOf(
                            Triple(CollaborationSpaceReadinessChannel.MATRIX, "Control", matrix),
                            Triple(CollaborationSpaceReadinessChannel.PUSH, "Push", push),
                        )) {
                        val bean = "collaborationSpaceReadiness${suffix}"
                        val tasks =
                            context.getBean(bean + "Tasks", ThreadPoolTaskExecutor::class.java)
                        val timer =
                            context.getBean(bean + "Timers", ThreadPoolTaskScheduler::class.java)
                        pools += tasks
                        timers += timer
                        assertEquals(2, tasks.maxPoolSize)
                        assertTrue(tasks.threadPoolExecutor.queue.isEmpty())
                        if (disabled !in required) {
                            val worker =
                                context.getBean(
                                    bean + "Worker",
                                    CollaborationReadinessWorker::class.java,
                                )
                            workers += worker
                            assertTrue(worker.isRunning)
                            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                            while (
                                probe.claims.getValue(channel).get() == 0 &&
                                    System.nanoTime() < until
                            ) TimeUnit.MILLISECONDS.sleep(10)
                            assertTrue(
                                probe.claims.getValue(channel).get() > 0,
                                "Enabled $channel did not claim: $disabled",
                            )
                        } else {
                            assertFalse(
                                context.containsBean(bean + "Worker"),
                                "Disabled $channel was created: $disabled",
                            )
                            TimeUnit.MILLISECONDS.sleep(100)
                            assertEquals(0, probe.claims.getValue(channel).get())
                            assertTrue(timer.scheduledThreadPoolExecutor.queue.isEmpty())
                        }
                        assertEquals(
                            0,
                            probe.failures.getValue(channel).get(),
                            "Ordinary-role claim failed: $channel/$disabled",
                        )
                    }
                    assertNotSame(pools[0], pools[1])
                    assertNotSame(timers[0], timers[1])
                } finally {
                    context.close()
                }
                assertTrue(workers.none { it.isRunning })
                assertTrue(pools.all { it.threadPoolExecutor.isTerminated })
                assertTrue(timers.all { it.scheduledThreadPoolExecutor.isTerminated })
            }
        } finally {
            Files.deleteIfExists(credentials)
            Files.deleteIfExists(signing)
            Files.deleteIfExists(directory)
        }
    }
}

class CollaborationSpaceReadinessContextProbe {
    val claims = CollaborationSpaceReadinessChannel.entries.associateWith { AtomicInteger() }
    val failures = CollaborationSpaceReadinessChannel.entries.associateWith { AtomicInteger() }
}

@TestConfiguration(proxyBeanMethods = false)
class CollaborationSpaceReadinessContextProbeConfiguration {
    @Bean fun collaborationSpaceReadinessContextProbe() = CollaborationSpaceReadinessContextProbe()

    @Bean
    @Primary
    fun observedCollaborationSpaceReadinessQueue(
        @Qualifier("collaborationSpaceReadinessQueue")
        delegate: CollaborationSpaceReadinessQueueRepository,
        probe: CollaborationSpaceReadinessContextProbe,
    ): CollaborationSpaceReadinessQueueRepository =
        object : CollaborationSpaceReadinessQueueRepository by delegate {
            override fun claim(
                channel: CollaborationSpaceReadinessChannel,
                owner: UUID,
                limit: Int,
            ): Result<List<CollaborationSpaceReadinessLease>> {
                check(limit in 1..2)
                val result = delegate.claim(channel, owner, limit)
                if (result is Result.Success) probe.claims.getValue(channel).incrementAndGet()
                else probe.failures.getValue(channel).incrementAndGet()
                return result
            }
        }
}

class CollaborationSpaceReadinessContextScanFilter :
    org.springframework.boot.context.TypeExcludeFilter() {
    override fun match(
        metadata: org.springframework.core.type.classreading.MetadataReader,
        factory: org.springframework.core.type.classreading.MetadataReaderFactory,
    ): Boolean =
        metadata.annotationMetadata.isAnnotated(
            org.springframework.boot.test.context.TestComponent::class.java.name
        )

    override fun equals(other: Any?) = other is CollaborationSpaceReadinessContextScanFilter

    override fun hashCode() = javaClass.hashCode()
}
