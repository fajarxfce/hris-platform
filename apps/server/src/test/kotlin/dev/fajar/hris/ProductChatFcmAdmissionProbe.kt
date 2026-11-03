package dev.fajar.hris

import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Fixture-only observations before queue retention. Each output is a Boolean/null, never an
 * identity, bearer, key, timestamp or message. These observations do not grant admission.
 */
internal class ProductChatFcmAdmissionProbe(
    database: JdbcTemplate,
    private val company: UUID,
    private val account: UUID,
) {
    private val query = JdbcTemplate(requireNotNull(database.dataSource)).apply { queryTimeout = 3 }

    fun snapshot(): List<Map<String, Any?>> =
        query.queryForList(
            """select
            b.reserved_at<=current_timestamp as binding_reserved,
            b.published_at between b.reserved_at and current_timestamp as binding_published,
            a.active as account_active,c.active as company_active,m.active as member_active,
            exists(select 1 from membership_permissions p where p.company_id=b.company_id
                and p.account_id=b.account_id and p.permission='collaboration.chat') as chat_permission,
            s.id is not null as parent_present,s.revoked_at is null as parent_not_revoked,
            s.expires_at>current_timestamp as parent_unexpired,
            s.credential_version=a.security_version and s.credential_version>=0 as credential_current,
            s.authenticated_at<=current_timestamp as parent_authentication_not_future,
            s.authenticated_at<s.created_at as authentication_precedes_session_creation,
            s.created_at<=s.rotated_at and s.rotated_at<=current_timestamp as parent_rotated,
            d.id is not null as device_present,d.state='ACTIVE' and d.revoked_at is null as device_active,
            d.created_at between s.created_at and b.reserved_at as device_created,
            d.platform in ('ANDROID','IOS') as device_platform,
            g.id is not null as gateway_present,g.native_session_id=b.native_session_id as gateway_parent,
            g.platform=d.platform as gateway_platform,g.revoked_at is null as gateway_not_revoked,
            g.version between 0 and 999999 and g.client_build>0 as gateway_version,
            g.created_at>=d.created_at and g.rotated_at between g.created_at and current_timestamp as gateway_chronology,
            g.expires_at>current_timestamp as gateway_unexpired,
            n.session_id is not null as registration_present,n.enabled as registration_enabled,
            n.platform=d.platform as registration_platform,n.version between 0 and 9999 as registration_version,
            n.registered_at>=s.created_at and n.updated_at between n.registered_at and current_timestamp as registration_chronology,
            n.expires_at>current_timestamp as registration_unexpired,
            exists(select 1 from collaboration_direct_conversations dc
                join collaboration_direct_room_bindings rb on rb.company_id=dc.company_id
                and rb.conversation_id=dc.id where dc.company_id=b.company_id
                and b.account_id in (dc.first_account_id,dc.second_account_id)
                and rb.account_id in (dc.first_account_id,dc.second_account_id)
                and rb.matrix_server_name='matrix.example.test') as owned_room_present
            from collaboration_push_bindings b
            left join accounts a on a.id=b.account_id
            left join companies c on c.id=b.company_id
            left join company_memberships m on m.company_id=b.company_id and m.account_id=b.account_id
            left join native_sessions s on s.id=b.native_session_id and s.account_id=b.account_id
            left join collaboration_devices d on d.company_id=b.company_id and d.id=b.device_id and d.account_id=b.account_id
            left join collaboration_gateway_sessions g on g.company_id=b.company_id and g.device_id=b.device_id and g.account_id=b.account_id
            left join native_push_registrations n on n.session_id=b.native_session_id and n.account_id=b.account_id
            where b.company_id=? and b.account_id=? order by b.device_id limit 5""",
            company,
            account,
        )
}
