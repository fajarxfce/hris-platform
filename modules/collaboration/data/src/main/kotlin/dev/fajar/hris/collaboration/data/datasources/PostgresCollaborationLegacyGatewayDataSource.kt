package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationSpaceLegacyGateways.COLLABORATION_SPACE_LEGACY_GATEWAYS as G
import dev.fajar.hris.schema.tables.records.CollaborationSpaceLegacyGatewaysRecord
import java.util.UUID
import org.jooq.DSLContext

class PostgresCollaborationLegacyGatewayDataSource(private val sql: DSLContext) :
    CollaborationLegacyGatewayDataSource {
    override fun find(
        spaceId: UUID,
        owner: CollaborationLegacyRuntimeCoordinatesData,
    ): CollaborationSpaceLegacyGatewaysRecord? =
        sql.selectFrom(G)
            .where(
                G.SPACE_ID.eq(spaceId),
                G.ACCOUNT_ID.eq(owner.accountId),
                G.LEGACY_COMPANY_ID.eq(owner.companyId),
                G.LEGACY_DEVICE_ID.eq(owner.deviceId),
            )
            .fetchOne()
    // Do not filter native_session_id here: a wrong parent must fail mapping,
    // rather than being misrepresented as an absent original gateway.
}
