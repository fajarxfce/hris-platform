package dev.fajar.hris

import dev.fajar.hris.leave.data.datasources.LeaveAccrualScheduleDataSource
import dev.fajar.hris.leave.data.models.LeaveAccrualSchedulePeriodRow
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class LeaveAccrualScheduleProbe {
    @Volatile var omitPeriod = false
    @Volatile var earlyPeriod = false
    @Volatile var afterRead: (() -> Unit)? = null

    fun clear() {
        omitPeriod = false
        earlyPeriod = false
        afterRead = null
    }
}

@TestConfiguration(proxyBeanMethods = false)
class LeaveAccrualScheduleProbeConfiguration {
    @Bean fun leaveAccrualScheduleProbe() = LeaveAccrualScheduleProbe()

    @Bean
    @Primary
    fun probedLeaveAccrualScheduleSource(
        @Qualifier("leaveAccrualScheduleSource") source: LeaveAccrualScheduleDataSource,
        probe: LeaveAccrualScheduleProbe,
    ): LeaveAccrualScheduleDataSource =
        object : LeaveAccrualScheduleDataSource by source {
            override fun insert(
                schedule: LeaveAccrualSchedulesRecord,
                entries: List<LeaveAccrualScheduleEntriesRecord>,
            ) {
                if (probe.earlyPeriod)
                    entries.first().scheduledFor = entries.first().scheduledFor.minusSeconds(1)
                source.insert(schedule, if (probe.omitPeriod) entries.dropLast(1) else entries)
            }

            override fun list(
                company: UUID,
                after: UUID?,
                limit: Int,
            ): List<LeaveAccrualSchedulesRecord> =
                source.list(company, after, limit).also { probe.afterRead?.invoke() }

            override fun periods(company: UUID, id: UUID): List<LeaveAccrualSchedulePeriodRow> =
                source.periods(company, id).also { probe.afterRead?.invoke() }
        }
}
