package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSpaceCredentialMigrationTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun migrationPreservesDeviceAndOnlyCurrentExactParentCanPublishBoundedCredentials() {
        with(Fixture()) {
            val before = owner.queryForList("select * from collaboration_space_devices")
            migrate()
            assertEquals(before, owner.queryForList("select * from collaboration_space_devices"))
            assertTrue(success(rows()).isEmpty())
            for (current in
                listOf(
                    actor.copy(accountId = UUID.randomUUID()),
                    actor.copy(companyId = company),
                )) {
                assertInstanceOf(Result.Failed::class.java, run(current) { insert() })
                assertTrue(success(rows(current)).isEmpty())
            }
            for (invalid in
                listOf<() -> Int>(
                    { insert(session = UUID.randomUUID()) },
                    { insert(providerDevice = "HRISG_${compact(UUID.randomUUID())}") },
                    { insert(issued = at.minusSeconds(1)) },
                    { insert(expiry = at.plusSeconds(601)) },
                    { insert(expiry = at) },
                )) assertInstanceOf(Result.Failed::class.java, run { invalid() })
            owner.update("update native_sessions set credential_version=-1 where id=?", parent)
            assertInstanceOf(Result.Failed::class.java, run { insert() })
            owner.update(
                "update native_sessions set credential_version=0,access_expires_at=?::timestamptz where id=?",
                at.toString(),
                parent,
            )
            assertInstanceOf(Result.Failed::class.java, run { insert() })
            owner.update(
                "update native_sessions set access_expires_at=?::timestamptz where id=?",
                at.plusSeconds(900).toString(),
                parent,
            )
            assertEquals(1, success(run { insert() }))
            val original = success(rows())
            for (assignment in
                listOf(
                    "generation=0,token_encrypted='replacement'",
                    "generation=2,token_encrypted='replacement'",
                    "generation=1,native_session_id=gen_random_uuid()",
                    "generation=1,matrix_user_id='@foreign:matrix.example.test'",
                    "generation=1,created_at=created_at-interval '1 second'",
                    "generation=1,updated_at=updated_at-interval '1 second'",
                    "generation=1,token_expires_at='infinity'::timestamptz",
                    "generation=1,token_expires_at=updated_at+interval '11 minutes'",
                    "generation=1,token_encrypted=null,cleared_at=updated_at",
                )) assertInstanceOf(Result.Failed::class.java, run { change(assignment) })
            assertEquals(original, success(rows()))
            assertEquals(
                0,
                success(run { runtime.update("delete from collaboration_space_provider_sessions") }),
            )
            assertThrows(DataAccessException::class.java) {
                owner.update("delete from collaboration_space_provider_sessions")
            }
            assertEquals(
                0,
                runtime.queryForObject(
                    "select count(*) from collaboration_space_provider_sessions",
                    Int::class.java,
                ),
            )
        }
    }

    @Test
    fun offboardingAndParentPurgeStillAllowIrreversibleCleanupAndArchiveRetainsProvenance() {
        with(Fixture()) {
            migrate()
            success(run { insert() })
            val live = success(rows())
            owner.update(
                "update company_memberships set active=false,version=version+1 where account_id=?",
                account,
            )
            owner.update("update accounts set active=false,version=version+1 where id=?", account)
            owner.update("delete from native_sessions where id=?", parent)
            assertEquals(live, success(rows()))
            assertEquals(
                1,
                success(run { runtime.queryForList("select * from collaboration_space_devices") })
                    .size,
            )
            assertTrue(success(rows(actor.copy(accountId = UUID.randomUUID()))).isEmpty())
            assertTrue(success(rows(actor.copy(companyId = company))).isEmpty())
            assertInstanceOf(
                Result.Failed::class.java,
                run { change("generation=1,token_encrypted='replacement'") },
            )
            assertInstanceOf(
                Result.Failed::class.java,
                run {
                    runtime.update(
                        "update collaboration_space_devices set state='ACTIVE',version=1"
                    )
                },
            )
            assertInstanceOf(Result.Failed::class.java, run { clear(at) })
            success(run { revoke(at.plusSeconds(2)) })
            assertInstanceOf(Result.Failed::class.java, run { clear(at.plusSeconds(1)) })
            assertEquals(1, success(run { clear(at.plusSeconds(2)) }))
            val retired = success(rows())
            assertNull(retired.single()["token_encrypted"])
            assertEquals(1L, retired.single()["generation"])
            assertEquals(parent, retired.single()["native_session_id"])
            owner.update("update accounts set active=true,version=version+1 where id=?", account)
            owner.update(
                "update company_memberships set active=true,version=version+1 where account_id=?",
                account,
            )
            for (assignment in
                listOf(
                    "generation=2,token_encrypted='replacement',cleared_at=null",
                    "generation=2",
                    "generation=2,cleared_at=cleared_at+interval '1 second'",
                )) assertInstanceOf(Result.Failed::class.java, run { change(assignment) })
            assertInstanceOf(Result.Failed::class.java, run { insert() })
            val restored = restore()
            assertEquals(
                retired,
                restored.queryForList("select * from collaboration_space_provider_sessions"),
            )
            assertEquals(
                owner.queryForList("select * from collaboration_space_devices"),
                restored.queryForList("select * from collaboration_space_devices"),
            )
            assertEquals(
                0,
                restored.queryForObject(
                    "select count(*) from collaboration_space_device_sessions",
                    Int::class.java,
                ),
            )
            assertThrows(DataAccessException::class.java) {
                restored.update("delete from collaboration_space_provider_sessions")
            }
            assertThrows(DataAccessException::class.java) {
                restored.update("update collaboration_space_provider_sessions set generation=2")
            }
        }
    }

    @Test
    fun terminalRevisionAndGenerationRemainAvailableForRevocationAtExhaustion() {
        with(Fixture()) {
            migrate()
            success(run { insert() })
            // Isolated migration-owner boundary fixture; synthesize the state reached after
            // 999999 valid rotations without running a million writes. All constraints remain on.
            for ((table, trigger, counter) in
                listOf(
                    Triple(
                        "collaboration_space_devices",
                        "protect_collaboration_space_device",
                        "version",
                    ),
                    Triple(
                        "collaboration_space_provider_sessions",
                        "protect_collaboration_space_provider_session",
                        "generation",
                    ),
                )) {
                owner.execute("alter table $table disable trigger $trigger")
                try {
                    owner.update("update $table set $counter=999999")
                } finally {
                    owner.execute("alter table $table enable trigger $trigger")
                }
            }
            assertInstanceOf(
                Result.Failed::class.java,
                run { change("generation=1000000,token_encrypted='replacement'") },
            )
            assertInstanceOf(
                Result.Failed::class.java,
                run {
                    runtime.update(
                        "update collaboration_space_devices set state='ACTIVE',version=1000000"
                    )
                },
            )
            success(run { revoke(at) })
            success(run { clear(at) })
            assertEquals(1000000L, success(rows()).single()["generation"])
            assertEquals(
                1000000L,
                success(
                        run {
                            runtime.queryForList("select version from collaboration_space_devices")
                        }
                    )
                    .single()["version"],
            )
            assertInstanceOf(Result.Failed::class.java, run { clear(at) })
            assertThrows(DataAccessException::class.java) {
                owner.update("update collaboration_space_devices set version=1000001")
            }
        }
    }

    private inner class Fixture {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        val owner = JdbcTemplate(source)
        val runtimeSource =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "space_credential_fixture",
                "isolated-fixture-only",
            )
        val runtime = JdbcTemplate(runtimeSource)
        val transactions = PostgresTransactionRunner(JdbcTransactionManager(runtimeSource), runtime)
        val account = UUID.randomUUID()
        val company = UUID.randomUUID()
        val parent = UUID.randomUUID()
        val device = UUID.randomUUID()
        val at = Instant.now().truncatedTo(ChronoUnit.SECONDS)
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
            owner.execute(
                "create role space_credential_fixture login password 'isolated-fixture-only'"
            )
            owner.execute("grant usage on schema public to space_credential_fixture")
            owner.execute(
                "grant select,insert,update,delete on all tables in schema public to space_credential_fixture"
            )
        }

        fun <T> run(current: Actor = actor, body: () -> T): Result<T> =
            transactions.run(current) { safeDatabaseCall(body) }

        fun insert(
            session: UUID = parent,
            providerDevice: String = "HRISG_${compact(device)}",
            issued: Instant = at,
            expiry: Instant = at.plusSeconds(600),
        ) =
            runtime.update(
                "insert into collaboration_space_provider_sessions(space_id,device_id,account_id,native_session_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) values(?,?,?,?,?,?,0,'fixture-not-an-encrypted-token',?::timestamptz,?::timestamptz,?::timestamptz)",
                space,
                device,
                account,
                session,
                user,
                providerDevice,
                issued.toString(),
                issued.toString(),
                expiry.toString(),
            )

        fun change(assignment: String) =
            runtime.update(
                "update collaboration_space_provider_sessions set $assignment where device_id=?",
                device,
            )

        fun clear(time: Instant) =
            runtime.update(
                "update collaboration_space_provider_sessions set generation=generation+1,token_encrypted=null,cleared_at=?::timestamptz,updated_at=?::timestamptz where device_id=?",
                time.toString(),
                time.toString(),
                device,
            )

        fun revoke(time: Instant) =
            runtime.update(
                "update collaboration_space_devices set state='REVOKED',version=version+1,revoked_at=?::timestamptz,updated_at=?::timestamptz where id=?",
                time.toString(),
                time.toString(),
                device,
            )

        fun rows(current: Actor = actor) =
            run(current) {
                runtime.queryForList("select * from collaboration_space_provider_sessions")
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
                "/tmp/space-credential.dump",
            )
            command("createdb", "-U", postgres.username, "space_credential_restored")
            command(
                "pg_restore",
                "-U",
                postgres.username,
                "-d",
                "space_credential_restored",
                "--single-transaction",
                "--exit-on-error",
                "/tmp/space-credential.dump",
            )
            return JdbcTemplate(
                DriverManagerDataSource(
                    postgres.jdbcUrl.substringBeforeLast('/') + "/space_credential_restored",
                    postgres.username,
                    postgres.password,
                )
            )
        }
    }

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
