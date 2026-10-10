package dev.fajar.hris

import dev.fajar.hris.organization.data.datasources.OrganizationDataSource
import dev.fajar.hris.schema.tables.records.OrganizationUnitsRecord
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class OrganizationReadProbe {
    data class Barrier(
        val company: UUID,
        val id: UUID,
        val entered: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
        val exited: CountDownLatch = CountDownLatch(1),
    )

    val current = AtomicReference<Barrier?>()
}

@TestConfiguration(proxyBeanMethods = false)
class OrganizationReadProbeConfiguration {
    @Bean fun organizationReadProbe() = OrganizationReadProbe()

    @Bean
    @Primary
    fun observedOrganizationSource(
        @Qualifier("organizationSource") delegate: OrganizationDataSource,
        probe: OrganizationReadProbe,
    ): OrganizationDataSource =
        object : OrganizationDataSource by delegate {
            override fun find(companyId: UUID, id: UUID): OrganizationUnitsRecord? {
                val row = delegate.find(companyId, id)
                val barrier = probe.current.get()
                if (
                    barrier != null &&
                        barrier.company == companyId &&
                        barrier.id == id &&
                        probe.current.compareAndSet(barrier, null)
                ) {
                    barrier.entered.countDown()
                    try {
                        check(barrier.release.await(8, TimeUnit.SECONDS))
                    } finally {
                        barrier.exited.countDown()
                    }
                }
                return row
            }
        }
}
