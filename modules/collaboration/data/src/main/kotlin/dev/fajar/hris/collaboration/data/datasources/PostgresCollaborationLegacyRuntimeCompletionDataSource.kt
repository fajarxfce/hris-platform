package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationLegacyRuntimeCompletions.COLLABORATION_LEGACY_RUNTIME_COMPLETIONS
import dev.fajar.hris.schema.tables.records.CollaborationLegacyRuntimeCompletionsRecord
import org.jooq.DSLContext

class PostgresCollaborationLegacyRuntimeCompletionDataSource(private val sql: DSLContext) :
    CollaborationLegacyRuntimeCompletionDataSource {
    override fun find(
        owner: CollaborationLegacyRuntimeCoordinatesData
    ): CollaborationLegacyRuntimeCompletionsRecord? {
        val table = COLLABORATION_LEGACY_RUNTIME_COMPLETIONS
        return sql.selectFrom(table)
            .where(
                table.LEGACY_COMPANY_ID.eq(owner.companyId)
                    .and(table.LEGACY_DEVICE_ID.eq(owner.deviceId))
                    .and(table.ACCOUNT_ID.eq(owner.accountId))
                    .and(table.NATIVE_SESSION_ID.eq(owner.nativeSessionId))
            )
            .fetchOne()
    }

    override fun complete(request: CompleteCollaborationLegacyRuntimeData): Boolean =
        requireNotNull(
            sql.fetchOne(
                    "select public.complete_collaboration_legacy_runtime(?::uuid,?::uuid,?::uuid,?::bigint,?::bigint,?::bigint,?::bigint,?::bigint,?::bigint,?::bigint,?::text,?::timestamptz) as completed",
                    request.spaceId,
                    request.deviceId,
                    request.nativeSessionId,
                    request.nativeSessionVersion,
                    request.deviceVersion,
                    request.providerGeneration,
                    request.provisioningRevision,
                    request.legacyDeviceVersion,
                    request.legacyProviderGeneration,
                    request.legacyGatewayVersion,
                    request.fingerprint,
                    request.keysObservedAt,
                )
                ?.get("completed", Boolean::class.java)
        )
}
