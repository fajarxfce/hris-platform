package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationSpaceLegacyDevices.COLLABORATION_SPACE_LEGACY_DEVICES as V
import dev.fajar.hris.schema.tables.records.CollaborationSpaceLegacyDevicesRecord
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresCollaborationLegacyDeviceDataSource(private val sql: DSLContext) :
    CollaborationLegacyDeviceDataSource {
    override fun list(
        spaceId: UUID,
        accountId: UUID,
        afterCompanyId: UUID?,
        afterDeviceId: UUID?,
        limit: Int,
    ): List<CollaborationSpaceLegacyDevicesRecord> =
        sql.selectFrom(V)
            .where(
                V.SPACE_ID.eq(spaceId),
                V.ACCOUNT_ID.eq(accountId),
                if (afterCompanyId != null && afterDeviceId != null)
                    DSL.row(V.LEGACY_COMPANY_ID, V.LEGACY_DEVICE_ID)
                        .gt(afterCompanyId, afterDeviceId)
                else DSL.noCondition(),
            )
            .orderBy(V.LEGACY_COMPANY_ID, V.LEGACY_DEVICE_ID)
            .limit(limit)
            .fetch()
}
