package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.postgresql.PostgreSQLContainer

internal class SpaceGatewayMigrationFixture(
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

    fun migrate() {
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

    val gateway = UUID.randomUUID()
    val operation = UUID.randomUUID()
    val access = "a".repeat(64)
    val refresh = "b".repeat(64)
    val anonymous
        get() = actor.copy(accountId = UUID(0, 0), companyId = null)

    val workerSource =
        DriverManagerDataSource(postgres.jdbcUrl, "space_gateway_worker", "isolated-worker-only")

    fun <T> run(current: Actor = actor, body: () -> T): Result<T> =
        transactions.run(current) { safeDatabaseCall(body) }

    fun insert(session: UUID = parent, build: Int = 5) =
        runtime.update(
            "insert into collaboration_space_gateway_sessions(space_id,id,account_id,device_id,native_session_id,platform,client_build,version,access_hash,refresh_hash,created_at,rotated_at,expires_at,access_expires_at,replay_kind,bootstrap_operation_id,replay_until,replay_encrypted) values(?,?,?,?,?,'ANDROID',?,0,?,?,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz,'BOOTSTRAP',?,?::timestamptz,'fixture-replay')",
            space,
            gateway,
            account,
            device,
            session,
            build,
            access,
            refresh,
            at.toString(),
            at.toString(),
            at.plusSeconds(3600).toString(),
            at.plusSeconds(900).toString(),
            operation,
            at.plusSeconds(60).toString(),
        )

    fun change(assignment: String, version: Long = 0) =
        runtime.update(
            "update collaboration_space_gateway_sessions set $assignment where space_id=? and id=? and version=?",
            space,
            gateway,
            version,
        )

    fun rotate(extra: String = "", version: Long = 0) =
        change(
            "version=version+1,access_hash='${"c".repeat(64)}',refresh_hash='${"d".repeat(64)}',replay_kind='REFRESH',bootstrap_operation_id=null,previous_refresh_hash=refresh_hash,replay_encrypted='next-replay'$extra",
            version,
        )

    fun revoke(time: Instant) =
        runtime.update(
            "update collaboration_space_devices set state='REVOKED',version=version+1,revoked_at=?::timestamptz,updated_at=?::timestamptz where id=?",
            time.toString(),
            time.toString(),
            device,
        )

    fun retire(time: Instant, version: Long = 0) =
        change(
            "version=version+1,revoked_at='${time}'::timestamptz,access_hash=null,refresh_hash=null,replay_kind=null,bootstrap_operation_id=null,previous_refresh_hash=null,replay_until=null,replay_encrypted=null",
            version,
        )

    fun rows(current: Actor = actor) =
        run(current) { runtime.queryForList("select * from collaboration_space_gateway_sessions") }

    fun lookup(
        hash: String,
        current: Actor = anonymous,
        setting: String = "app.collaboration_space_gateway_lookup",
    ) =
        run(current) {
            runtime.queryForObject("select set_config(?,?,true)", String::class.java, setting, hash)
            runtime.queryForList("select * from collaboration_space_gateway_sessions")
        }

    fun restore(): JdbcTemplate {
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
            "/tmp/space-gateway.dump",
        )
        command("createdb", "-U", postgres.username, "space_gateway_restored")
        command(
            "pg_restore",
            "-U",
            postgres.username,
            "-d",
            "space_gateway_restored",
            "--single-transaction",
            "--exit-on-error",
            "/tmp/space-gateway.dump",
        )
        return JdbcTemplate(
            DriverManagerDataSource(
                postgres.jdbcUrl.substringBeforeLast('/') + "/space_gateway_restored",
                postgres.username,
                postgres.password,
            )
        )
    }

    private fun compact(id: UUID) = id.toString().replace("-", "")
}
