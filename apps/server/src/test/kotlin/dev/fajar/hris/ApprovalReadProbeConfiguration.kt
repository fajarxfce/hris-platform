package dev.fajar.hris

import dev.fajar.hris.approvals.data.datasources.ApprovalRequestDataSource
import dev.fajar.hris.schema.tables.records.ApprovalRequestsRecord
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class ApprovalReadProbe {
    class Barrier(val company: UUID) {
        val claimed = AtomicBoolean()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)

        fun awaitOnce(id: UUID) {
            if (id != company || !claimed.compareAndSet(false, true)) return
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
    }

    val current = AtomicReference<Barrier?>()
}

@TestConfiguration(proxyBeanMethods = false)
class ApprovalReadProbeConfiguration {
    @Bean fun approvalReadProbe() = ApprovalReadProbe()

    @Bean
    @Primary
    fun probedApprovalRequests(
        @Qualifier("approvalRequests") delegate: ApprovalRequestDataSource,
        probe: ApprovalReadProbe,
    ): ApprovalRequestDataSource =
        object : ApprovalRequestDataSource by delegate {
            override fun find(companyId: UUID, id: UUID): ApprovalRequestsRecord? =
                delegate.find(companyId, id).also { probe.current.get()?.awaitOnce(companyId) }

            override fun inbox(
                companyId: UUID,
                accountId: UUID,
                includeBlocked: Boolean,
                permissionsByKind: Map<String, Set<String>>,
                at: OffsetDateTime,
                after: UUID?,
                limit: Int,
            ): List<ApprovalRequestsRecord> =
                delegate
                    .inbox(
                        companyId,
                        accountId,
                        includeBlocked,
                        permissionsByKind,
                        at,
                        after,
                        limit,
                    )
                    .also { probe.current.get()?.awaitOnce(companyId) }
        }
}
