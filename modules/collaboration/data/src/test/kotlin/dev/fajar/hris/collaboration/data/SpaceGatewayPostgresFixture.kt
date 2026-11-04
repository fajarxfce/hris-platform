package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.EncryptedCollaborationSpaceGatewaySessionRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.data.crypto.*
import dev.fajar.hris.identity.data.datasources.JceIdentityTokenDataSource
import dev.fajar.hris.schema.tables.CollaborationSpaceGatewaySessions.COLLABORATION_SPACE_GATEWAY_SESSIONS as G
import dev.fajar.hris.schema.tables.records.CollaborationSpaceGatewaySessionsRecord
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.postgresql.PostgreSQLContainer

internal class SpaceGatewayPostgresFixture(
    private val postgres: PostgreSQLContainer,
    ageSeconds: Long = 5,
) {
    val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
    val owner = JdbcTemplate(source)
    val runtimeSource =
        DriverManagerDataSource(postgres.jdbcUrl, "space_gateway_fixture", "isolated-fixture-only")
    val runtime = JdbcTemplate(runtimeSource)
    val transactions = PostgresTransactionRunner(JdbcTransactionManager(runtimeSource), runtime)
    val account = UUID.randomUUID()
    val company = UUID.randomUUID()
    val parent = UUID.randomUUID()
    val device = UUID.randomUUID()
    val at = Instant.now().minusSeconds(ageSeconds).truncatedTo(ChronoUnit.SECONDS)
    val actor =
        Actor(
            account,
            null,
            emptySet(),
            at.minusSeconds(60),
            UUID.randomUUID(),
            credentialVersion = 0,
        )
    val space: UUID
    val user: String

    init {
        Flyway.configure().dataSource(source).target("120").load().migrate()
        space =
            requireNotNull(
                owner.queryForObject("select id from collaboration_spaces", UUID::class.java)
            )
        user = "@hrisg_${compact(space)}_${compact(account)}:matrix.example.test"
        owner.update(
            "insert into companies(id,code,name,timezone) values(?,'CREDENTIAL','Fictional','UTC')",
            company,
        )
        owner.update(
            "insert into accounts(id,email,display_name) values(?,?,'Fictional')",
            account,
            "$account@example.test",
        )
        owner.update(
            "insert into company_memberships(company_id,account_id) values(?,?)",
            company,
            account,
        )
        owner.update(
            "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
            company,
            account,
        )
        owner.update(
            "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,credential_version,access_expires_at,created_at,rotated_at) values(?,?,?,?::timestamptz,?::timestamptz,0,?::timestamptz,?::timestamptz,?::timestamptz)",
            parent,
            account,
            compact(parent).repeat(2),
            actor.authenticatedAt.toString(),
            at.plusSeconds(3600).toString(),
            at.plusSeconds(900).toString(),
            at.minusSeconds(60).toString(),
            at.minusSeconds(30).toString(),
        )
        owner.update(
            "insert into collaboration_space_identities(space_id,account_id,matrix_server_name,matrix_user_id,origin,created_at) values(?,?,'matrix.example.test',?,'SPACE',?::timestamptz)",
            space,
            account,
            user,
            at.toString(),
        )
        owner.update(
            "insert into collaboration_space_devices(space_id,id,account_id,matrix_user_id,matrix_device_id,origin,origin_native_session_id,platform,label,state,version,created_at,updated_at) values(?,?,?,?,?,'SPACE',?,'ANDROID','Fictional','PROVISIONING',0,?::timestamptz,?::timestamptz)",
            space,
            device,
            account,
            user,
            "HRISG_${compact(device)}",
            parent,
            at.toString(),
            at.toString(),
        )
        owner.update(
            "insert into collaboration_space_device_sessions(space_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?::timestamptz)",
            space,
            device,
            account,
            parent,
            at.toString(),
        )
    }

    fun migrate(beforeQueue: () -> Unit = {}) {
        Flyway.configure().dataSource(source).target("121").load().migrate()
        // An already-active SPACE device from before the queue migration is valid input.
        // Preserve its original provider/native coordinates through the later migrations.
        owner.update(
            "insert into collaboration_space_provider_sessions(space_id,device_id,account_id,native_session_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) values(?,?,?,?,?,?,0,'owned-fixture-envelope',?::timestamptz,?::timestamptz,?::timestamptz)",
            space,
            device,
            account,
            parent,
            user,
            "HRISG_${compact(device)}",
            at.toString(),
            at.toString(),
            at.plusSeconds(600).toString(),
        )
        owner.update(
            "update collaboration_space_devices set state='ACTIVE',version=1 where id=?",
            device,
        )
        beforeQueue()
        Flyway.configure().dataSource(source).target("125").load().migrate()
        val retained = owner.queryForList("select * from collaboration_space_devices")
        Flyway.configure().dataSource(source).target("126").load().migrate()
        check(retained == owner.queryForList("select * from collaboration_space_devices"))
        owner.execute("create role space_gateway_fixture login password 'isolated-fixture-only'")
        owner.execute("create role space_gateway_worker login password 'isolated-worker-only'")
        owner.execute("grant hris_worker_capability to space_gateway_worker")
        owner.execute("grant usage on schema public to space_gateway_fixture,space_gateway_worker")
        owner.execute(
            "grant select,insert,update,delete on all tables in schema public to space_gateway_fixture,space_gateway_worker"
        )
    }

    val operation = UUID.randomUUID()
    val session
        get() =
            CollaborationSpaceGatewaySession(
                gateway,
                space,
                account,
                device,
                parent,
                CollaborationPlatform.ANDROID,
                5,
                0,
                at,
                at,
                at.plusSeconds(3600),
                at.plusSeconds(900),
            )

    private val gateway = UUID.randomUUID()
    val workerSource =
        DriverManagerDataSource(postgres.jdbcUrl, "space_gateway_worker", "isolated-worker-only")
    val oldKey = Base64.getEncoder().encodeToString(ByteArray(32) { 11 })
    val currentKey = Base64.getEncoder().encodeToString(ByteArray(32) { 29 })
    val oldKeys = parseIdentityKeyring("old", "old:$oldKey")
    val currentKeys = parseIdentityKeyring("current", "old:$oldKey,current:$currentKey")
    val rawSource
        get() = rawSource(runtimeSource)

    val repo
        get() = repository(runtimeSource)

    fun rawSource(data: DataSource) =
        PostgresCollaborationSpaceGatewaySessionDataSource(
            DSL.using(TransactionAwareDataSourceProxy(data), SQLDialect.POSTGRES)
        )

    fun repository(data: DataSource, keys: IdentityKeyring = currentKeys) =
        EncryptedCollaborationSpaceGatewaySessionRepository(
            rawSource(data),
            JceIdentityTokenDataSource(keys),
        )

    fun repository(source: CollaborationSpaceGatewaySessionDataSource) =
        EncryptedCollaborationSpaceGatewaySessionRepository(
            source,
            JceIdentityTokenDataSource(currentKeys),
        )

    fun transactions(data: DataSource) =
        PostgresTransactionRunner(JdbcTransactionManager(data), JdbcTemplate(data))

    fun <T> tx(current: Actor = actor, body: () -> Result<T>): Result<T> =
        transactions.run(current, body)

    fun raw() =
        DSL.using(source, SQLDialect.POSTGRES)
            .selectFrom(G)
            .where(G.DEVICE_ID.eq(device))
            .fetchOne()!!

    fun <T> success(result: Result<T>): T = (result as Result.Success).value

    fun revoke(at: Instant) =
        owner.update(
            "update collaboration_space_devices set state='REVOKED',version=version+1,revoked_at=?::timestamptz,updated_at=?::timestamptz where id=?",
            at.toString(),
            at.toString(),
            device,
        )

    fun restore(): DriverManagerDataSource {
        fun command(vararg args: String) {
            val result = postgres.execInContainer(*args)
            assertEquals(0, result.exitCode, result.stderr)
        }
        command(
            "pg_dump",
            "-U",
            postgres.username,
            "-d",
            postgres.databaseName,
            "-Fc",
            "-f",
            "/tmp/group-gateway.dump",
        )
        command("createdb", "-U", postgres.username, "group_gateway_restored")
        command(
            "pg_restore",
            "-U",
            postgres.username,
            "-d",
            "group_gateway_restored",
            "--single-transaction",
            "--exit-on-error",
            "/tmp/group-gateway.dump",
        )
        return DriverManagerDataSource(
            postgres.jdbcUrl.substringBeforeLast('/') + "/group_gateway_restored",
            "space_gateway_fixture",
            "isolated-fixture-only",
        )
    }

    private fun compact(id: UUID) = id.toString().replace("-", "")
}

internal class SpaceGatewayReadbackFaultSource(
    private val source: CollaborationSpaceGatewaySessionDataSource,
    private val fault:
        (CollaborationSpaceGatewaySessionsRecord?) -> CollaborationSpaceGatewaySessionsRecord?,
) : CollaborationSpaceGatewaySessionDataSource by source {
    var written = false

    override fun insert(record: CollaborationSpaceGatewaySessionsRecord) {
        source.insert(record)
        written = true
    }

    override fun replace(
        record: CollaborationSpaceGatewaySessionsRecord,
        expectedVersion: Long,
        expectedRefreshHash: String,
    ): Boolean = source.replace(record, expectedVersion, expectedRefreshHash).also { written = it }

    override fun retire(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        expectedVersion: Long,
        at: OffsetDateTime,
    ): Boolean =
        source.retire(spaceId, accountId, deviceId, expectedVersion, at).also { written = it }

    override fun find(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
        lock: Boolean,
    ): CollaborationSpaceGatewaySessionsRecord? =
        source.find(spaceId, accountId, deviceId, lock).let { if (written) fault(it) else it }
}
