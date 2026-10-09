package dev.fajar.hris

import dev.fajar.hris.administration.data.datasources.CompanyClientPolicyDataSource
import dev.fajar.hris.schema.tables.records.CompanyClientPolicyRevisionsRecord
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class ClientPolicyProbe {
    data class Barrier(
        val company: UUID,
        val shared: Boolean,
        val entered: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
    )

    val beforeLock = AtomicReference<Barrier?>()
    val afterEffectiveRead = AtomicReference<Barrier?>()
    val afterLatestRead = AtomicReference<Barrier?>()

    fun clear() {
        beforeLock.getAndSet(null)?.release?.countDown()
        afterEffectiveRead.getAndSet(null)?.release?.countDown()
        afterLatestRead.getAndSet(null)?.release?.countDown()
    }
}

@TestConfiguration(proxyBeanMethods = false)
class ClientPolicyProbeConfiguration {
    @Bean fun clientPolicyProbe() = ClientPolicyProbe()

    @Bean
    @Primary
    fun probedClientPolicySource(
        @Qualifier("companyClientPolicySource") delegate: CompanyClientPolicyDataSource,
        probe: ClientPolicyProbe,
    ): CompanyClientPolicyDataSource =
        object : CompanyClientPolicyDataSource by delegate {
            override fun find(
                companyId: UUID,
                version: Long?,
            ): CompanyClientPolicyRevisionsRecord? {
                val row = delegate.find(companyId, version)
                probe.afterLatestRead
                    .get()
                    ?.takeIf { it.company == companyId && version == null }
                    ?.let {
                        it.entered.countDown()
                        check(it.release.await(5, TimeUnit.SECONDS))
                    }
                return row
            }

            override fun lock(companyId: UUID, shared: Boolean) {
                probe.beforeLock
                    .get()
                    ?.takeIf { it.company == companyId && it.shared == shared }
                    ?.let {
                        it.entered.countDown()
                        check(it.release.await(5, TimeUnit.SECONDS))
                    }
                delegate.lock(companyId, shared)
            }

            override fun findEffective(
                companyId: UUID,
                at: OffsetDateTime,
            ): CompanyClientPolicyRevisionsRecord? {
                val row = delegate.findEffective(companyId, at)
                probe.afterEffectiveRead
                    .get()
                    ?.takeIf { it.company == companyId }
                    ?.let {
                        it.entered.countDown()
                        check(it.release.await(5, TimeUnit.SECONDS))
                    }
                return row
            }
        }
}
