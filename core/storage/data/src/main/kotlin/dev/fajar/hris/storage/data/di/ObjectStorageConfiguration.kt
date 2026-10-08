package dev.fajar.hris.storage.data.di

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.storage.data.datasources.*
import dev.fajar.hris.storage.data.repositories.PostgresObjectCleanupRepository
import dev.fajar.hris.storage.data.repositories.PrivateObjectStorageRepository
import dev.fajar.hris.storage.domain.repositories.*
import dev.fajar.hris.storage.domain.usecases.*
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

@Configuration(proxyBeanMethods = false)
class ObjectStorageConfiguration {
    @Bean(destroyMethod = "close")
    fun objectStorageSource(environment: Environment): ObjectStorageDataSource {
        if (!environment.getProperty("HRIS_STORAGE_ENABLED", Boolean::class.java, false))
            return UnavailableObjectStorageDataSource()
        val settings =
            ObjectStorageSettings(
                parseObjectStorageEndpoint(
                    environment.getRequiredProperty("HRIS_STORAGE_ENDPOINT")
                ),
                environment.getProperty("HRIS_STORAGE_REGION", "garage"),
                environment.getRequiredProperty("HRIS_STORAGE_BUCKET"),
                environment.getRequiredProperty("HRIS_STORAGE_ACCESS_KEY"),
                environment.getRequiredProperty("HRIS_STORAGE_SECRET_KEY"),
                environment.getProperty("HRIS_STORAGE_ALLOW_PLAINTEXT", Boolean::class.java, false),
            )
        return S3ObjectStorageDataSource(
            createObjectStorageClient(settings),
            settings.bucket,
            settings.callTimeout,
        )
    }

    @Bean
    fun objectStorage(source: ObjectStorageDataSource): ObjectStorageRepository =
        PrivateObjectStorageRepository(source)

    @Bean
    fun objectCleanupSource(sql: DSLContext): ObjectCleanupDataSource =
        PostgresObjectCleanupDataSource(sql)

    @Bean
    fun objectCleanup(source: ObjectCleanupDataSource): ObjectCleanupRepository =
        PostgresObjectCleanupRepository(source)

    @Bean
    fun collectObjectGarbage(
        queue: ObjectCleanupRepository,
        storage: ObjectStorageRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = CollectObjectGarbage(queue, storage, journal, transactions, clock)

    @Bean
    fun listObjectCleanup(queue: ObjectCleanupRepository, transactions: TransactionRunner) =
        ListObjectCleanup(queue, transactions)

    @Bean
    fun retryObjectCleanup(
        queue: ObjectCleanupRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = RetryObjectCleanup(queue, operations, journal, transactions, clock)
}
