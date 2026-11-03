package dev.fajar.hris.collaboration.data.datasources

import dev.fajar.hris.schema.tables.CollaborationSpaceIdentities.COLLABORATION_SPACE_IDENTITIES as I
import dev.fajar.hris.schema.tables.records.CollaborationSpaceIdentitiesRecord
import dev.fajar.hris.schema.tables.records.CollaborationSpaceLegacyDevicesRecord
import java.time.OffsetDateTime
import java.util.UUID
import org.jooq.DSLContext

class PostgresCollaborationSpaceIdentityDataSource(private val sql: DSLContext) :
    CollaborationSpaceIdentityDataSource {
    override fun selection(
        spaceId: UUID,
        accountId: UUID,
        serverName: String,
    ): CollaborationSpaceIdentitySelectionRow? {
        val row =
            sql.fetchOne(
                """
                select s.space_id as s_space_id,public.current_actor_id() as s_account_id,
                    c.space_id as c_space_id,c.account_id as c_account_id,c.matrix_server_name as c_server,
                    c.matrix_user_id as c_user,c.origin as c_origin,c.legacy_company_id as c_company,
                    c.legacy_device_id as c_device,c.created_at as c_created,c.version as c_version,
                    l.space_id as l_space,l.account_id as l_account,l.legacy_company_id as l_company,
                    l.legacy_device_id as l_device,l.platform as l_platform,l.state as l_state,
                    l.device_version as l_version,l.device_created_at as l_created,l.device_revoked_at as l_revoked,
                    l.matrix_user_id as l_user,l.matrix_device_id as l_matrix_device,
                    l.credential_generation as l_generation,l.identity_observed_at as l_observed,
                    l.credential_retired_at as l_retired
                from public.collaboration_space_directory s
                left join public.collaboration_space_identities c
                    on c.space_id=s.space_id and c.account_id=public.current_actor_id()
                left join lateral (
                    select * from public.collaboration_space_legacy_devices d
                    where c.account_id is null and d.space_id=s.space_id
                        and d.account_id=public.current_actor_id()
                        and substring(d.matrix_user_id from position(':' in d.matrix_user_id)+1)=?
                    order by d.identity_observed_at,d.legacy_company_id,d.legacy_device_id limit 1
                ) l on true
                where s.space_id=? and public.current_actor_id()=?
                """
                    .trimIndent(),
                serverName,
                spaceId,
                accountId,
            ) ?: return null
        fun uuid(name: String) = row.get(name, UUID::class.java)
        fun string(name: String) = row.get(name, String::class.java)
        fun time(name: String) = row.get(name, OffsetDateTime::class.java)
        fun number(name: String) = row.get(name, Long::class.javaObjectType)
        val selected =
            if (uuid("c_account_id") == null) null
            else
                CollaborationSpaceIdentitiesRecord().apply {
                    setSpaceId(uuid("c_space_id"))
                    setAccountId(uuid("c_account_id"))
                    matrixServerName = string("c_server")
                    matrixUserId = string("c_user")
                    origin = string("c_origin")
                    legacyCompanyId = uuid("c_company")
                    legacyDeviceId = uuid("c_device")
                    createdAt = time("c_created")
                    version = number("c_version")
                }
        val preferred =
            if (uuid("l_device") == null) null
            else
                CollaborationSpaceLegacyDevicesRecord().apply {
                    setSpaceId(uuid("l_space"))
                    setAccountId(uuid("l_account"))
                    legacyCompanyId = uuid("l_company")
                    legacyDeviceId = uuid("l_device")
                    platform = string("l_platform")
                    state = string("l_state")
                    deviceVersion = number("l_version")
                    deviceCreatedAt = time("l_created")
                    deviceRevokedAt = time("l_revoked")
                    matrixUserId = string("l_user")
                    matrixDeviceId = string("l_matrix_device")
                    credentialGeneration = number("l_generation")
                    identityObservedAt = time("l_observed")
                    credentialRetiredAt = time("l_retired")
                }
        return CollaborationSpaceIdentitySelectionRow(
            uuid("s_space_id"),
            uuid("s_account_id"),
            selected,
            preferred,
        )
    }

    override fun insert(record: CollaborationSpaceIdentitiesRecord) {
        sql.insertInto(I).set(record).execute()
    }
}
