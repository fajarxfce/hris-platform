package dev.fajar.hris

import dev.fajar.hris.leave.data.datasources.LeavePolicyDataSource
import dev.fajar.hris.leave.data.models.LeaveTypeRow
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class LeavePolicyReadProbe {
    @Volatile var afterFind: ((UUID, UUID) -> Unit)? = null
}

@TestConfiguration(proxyBeanMethods = false)
class LeavePolicyReadProbeConfiguration {
    @Bean fun leavePolicyReadProbe() = LeavePolicyReadProbe()

    @Bean
    @Primary
    fun probedLeavePolicySource(
        @Qualifier("leavePolicySource") source: LeavePolicyDataSource,
        probe: LeavePolicyReadProbe,
    ): LeavePolicyDataSource =
        object : LeavePolicyDataSource by source {
            override fun find(company: UUID, id: UUID): LeaveTypeRow? {
                val value = source.find(company, id)
                probe.afterFind?.invoke(company, id)
                return value
            }
        }
}
