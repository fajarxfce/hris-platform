package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationSpaceLegacyParents.COLLABORATION_SPACE_LEGACY_PARENTS as P
import dev.fajar.hris.schema.tables.records.CollaborationSpaceLegacyParentsRecord
import java.util.UUID
import org.jooq.DSLContext

class PostgresCollaborationLegacyParentDataSource(private val sql: DSLContext) :
    CollaborationLegacyParentDataSource {
    override fun find(
        spaceId: UUID,
        accountId: UUID,
        nativeSessionId: UUID,
        companyId: UUID,
        deviceId: UUID,
    ): CollaborationSpaceLegacyParentsRecord? =
        sql.selectFrom(P)
            .where(
                P.SPACE_ID.eq(spaceId),
                P.ACCOUNT_ID.eq(accountId),
                P.NATIVE_SESSION_ID.eq(nativeSessionId),
                P.LEGACY_COMPANY_ID.eq(companyId),
                P.LEGACY_DEVICE_ID.eq(deviceId),
            )
            .limit(1)
            .fetchOne()
}
