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

    override fun replace(
        record: CollaborationDeviceProvisioningRecord,
        expectedVersion: Long,
    ): Boolean =
        sql.update(P)
            .set(P.JOB_ID, record.jobId)
            .set(P.VERSION, record.version)
            .set(P.UPDATED_AT, record.updatedAt)
            .where(
                P.COMPANY_ID.eq(record.companyId),
                P.ACCOUNT_ID.eq(record.accountId),
                P.DEVICE_ID.eq(record.deviceId),
                P.VERSION.eq(expectedVersion),
            )
            .execute() == 1
}
