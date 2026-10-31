package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationDeviceProvisioning.COLLABORATION_DEVICE_PROVISIONING as P
import dev.fajar.hris.schema.tables.records.CollaborationDeviceProvisioningRecord
import java.util.UUID
import org.jooq.DSLContext

class PostgresCollaborationProvisioningDataSource(private val sql: DSLContext) :
    CollaborationProvisioningDataSource {
    override fun find(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): CollaborationDeviceProvisioningRecord? =
        sql.selectFrom(P)
            .where(P.COMPANY_ID.eq(companyId), P.ACCOUNT_ID.eq(accountId), P.DEVICE_ID.eq(deviceId))
            .forShare()
            .fetchOne()

    override fun insert(record: CollaborationDeviceProvisioningRecord) {
        sql.insertInto(P).set(record).execute()
    }
}
