package dev.fajar.hris.worker.di

import javax.sql.DataSource
import org.springframework.batch.core.configuration.support.JdbcDefaultBatchConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.transaction.PlatformTransactionManager

@Configuration(proxyBeanMethods = false)
class BatchConfiguration(
    private val data: DataSource,
    private val manager: PlatformTransactionManager,
) : JdbcDefaultBatchConfiguration() {
    override fun getDataSource(): DataSource = data

    override fun getTransactionManager(): PlatformTransactionManager = manager

    override fun getTablePrefix(): String = "hris_batch.BATCH_"

    override fun getTaskExecutor() = SyncTaskExecutor()
}
