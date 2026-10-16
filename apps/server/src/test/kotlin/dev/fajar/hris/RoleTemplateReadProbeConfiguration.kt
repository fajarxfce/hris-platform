package dev.fajar.hris

import dev.fajar.hris.identity.data.datasources.RoleTemplateDataSource
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class RoleTemplateReadProbe {
    @Volatile var beforeLock: ((UUID, Boolean) -> Unit)? = null
    @Volatile var afterFind: ((UUID, UUID) -> Unit)? = null
    @Volatile var afterHistory: ((UUID, UUID) -> Unit)? = null
    @Volatile var afterList: ((UUID) -> Unit)? = null
}

@TestConfiguration(proxyBeanMethods = false)
class RoleTemplateReadProbeConfiguration {
    @Bean fun roleTemplateReadProbe() = RoleTemplateReadProbe()

    @Bean
    @Primary
    fun probedRoleTemplateSource(
        @Qualifier("roleTemplateSource") source: RoleTemplateDataSource,
        probe: RoleTemplateReadProbe,
    ): RoleTemplateDataSource =
        object : RoleTemplateDataSource by source {
            override fun lock(companyId: UUID, shared: Boolean) {
                probe.beforeLock?.invoke(companyId, shared)
                source.lock(companyId, shared)
            }

            override fun find(companyId: UUID, id: UUID): CompanyRoleTemplatesRecord? {
                val value = source.find(companyId, id)
                probe.afterFind?.invoke(companyId, id)
                return value
            }

            override fun history(
                companyId: UUID,
                id: UUID,
                after: Long?,
                limit: Int,
            ): List<RoleTemplateRevisionsRecord> {
                val value = source.history(companyId, id, after, limit)
                probe.afterHistory?.invoke(companyId, id)
                return value
            }

            override fun list(
                companyId: UUID,
                after: UUID?,
                limit: Int,
            ): List<CompanyRoleTemplatesRecord> {
                val value = source.list(companyId, after, limit)
                probe.afterList?.invoke(companyId)
                return value
            }
        }
}
