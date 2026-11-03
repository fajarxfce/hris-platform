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
class CollaborationSpaceDeviceMigrationTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun explicitDeviceReferencesKeepOriginalKeysAcrossCompanySwitchParentPurgeAndArchiveRestore() {
        with(Fixture()) {
            val old = legacyRows()
            migrate()
            assertEquals(old, legacyRows())
            assertTrue(success(rows()).isEmpty())
            val device = UUID.randomUUID()
            assertEquals(1, success(run { insert(device) }))
            assertEquals(1, success(run { bind(device) }))
            val retained = success(rows())
            assertEquals(retained, success(rows(actor.copy(companyId = company))))
            assertEquals(retained, success(rows(actor.copy(companyId = employer))))
            assertEquals("PROVISIONING", retained.single()["state"])
            assertEquals(legacyUser, retained.single()["matrix_user_id"])
            assertEquals("HRIS_${compact(legacyDevice)}", retained.single()["matrix_device_id"])
            assertEquals(fingerprint, retained.single()["legacy_key_fingerprint"])
            assertEquals(old, legacyRows())
            sql.update("delete from native_sessions where id=?", parent)
            assertEquals(
                0,
                sql.queryForObject(
                    "select count(*) from collaboration_space_device_sessions",
                    Int::class.java,
                ),
            )
            assertEquals(retained, success(rows()))
            assertTrue(
                success(
                        run {
                            runtime.queryForList("select * from collaboration_space_legacy_parents")
                        }
                    )
                    .isEmpty()
            )
            assertEquals(
                1,
                success(
                        run {
                            runtime.queryForList("select * from collaboration_space_legacy_devices")
                        }
                    )
                    .size,
            )
            val replacement = addParent()
            assertInstanceOf(Result.Failed::class.java, run { bind(device, replacement) })
            assertInstanceOf(
                Result.Failed::class.java,
                run {
                    runtime.update(
                        "update collaboration_space_devices set origin_native_session_id=?,version=1 where id=?",
                        replacement,
                        device,
                    )
                },
            )
            assertEquals(retained, success(rows()))
            val restored = restore()
            assertEquals(
                retained,
                restored.queryForList("select * from collaboration_space_devices order by id"),
            )
            assertEquals(old, legacyRows(restored))
            assertEquals(
                0,
                restored.queryForObject(
                    "select count(*) from collaboration_space_device_sessions",
                    Int::class.java,
                ),
            )
            assertThrows(DataAccessException::class.java) {
                restored.update("delete from collaboration_space_devices")
            }
        }
    }

    @Test
    fun exactCanonicalUserParentPlatformAndReservedKeyAreRequiredWithoutExposingCredentials() {
        with(Fixture()) {
            migrate()
            val view =
                success(
                        run {
                            runtime.queryForList("select * from collaboration_space_legacy_parents")
                        }
                    )
                    .single()
            assertEquals(parent, view["native_session_id"])
            assertEquals(fingerprint, view["key_fingerprint"])
            assertEquals(1L, view["key_version"])
            assertFalse(
                view.keys.any {
                    it in
                        setOf(
                            "token_encrypted",
                            "access_hash",
                            "refresh_hash",
                            "exchange_encrypted",
                            "email",
                            "password_hash",
                        )
                }
            )
            assertTrue(
                success(
                        run(actor.copy(accountId = peer)) {
                            runtime.queryForList("select * from collaboration_space_legacy_parents")
                        }
                    )
                    .isEmpty()
            )
            assertEquals(
                true,
                sql.queryForObject(
                    "select reloptions @> array['security_barrier=true'] from pg_class where relname='collaboration_space_legacy_parents'",
                    Boolean::class.java,
                ),
            )
            assertEquals(
                false,
                sql.queryForObject(
                    "select rolcanlogin or rolbypassrls or rolsuper or rolinherit from pg_roles where rolname='hris_collaboration_legacy_reader'",
                    Boolean::class.java,
                ),
            )
            for ((table, column) in
                listOf(
                    "collaboration_provider_sessions" to "token_encrypted",
                    "native_sessions" to "refresh_hash",
                    "native_sessions" to "exchange_encrypted",
                    "accounts" to "email",
                )) assertEquals(
                false,
                sql.queryForObject(
                    "select has_column_privilege('hris_collaboration_legacy_reader',?,?,'SELECT')",
                    Boolean::class.java,
                    table,
                    column,
                ),
            )
            assertInstanceOf(
                Result.Failed::class.java,
                run { runtime.execute("set role hris_collaboration_legacy_reader") },
            )
            for (attempt in
                listOf<() -> Result<*>>(
                    { run(actor.copy(companyId = company)) { insert(UUID.randomUUID()) } },
                    { run(actor.copy(accountId = peer)) { insert(UUID.randomUUID()) } },
                    { run { insert(UUID.randomUUID(), selectedSpace = UUID.randomUUID()) } },
                    {
                        run {
                            insert(
                                UUID.randomUUID(),
                                user =
                                    "@hrisg_${compact(space)}_${compact(account)}:matrix.example.test",
                            )
                        }
                    },
                    { run { insert(UUID.randomUUID(), key = "b".repeat(64)) } },
                    { run { insert(UUID.randomUUID(), oldDevice = UUID.randomUUID()) } },
                    { run { insert(UUID.randomUUID(), session = addParent()) } },
                    { run { insert(UUID.randomUUID(), platform = "IOS") } },
                    { run { insert(UUID.randomUUID(), platform = "WEB") } },
                    {
                        run {
                            insert(
                                UUID.randomUUID(),
                                providerDevice = "HRISG_${compact(UUID.randomUUID())}",
                            )
                        }
                    },
                )) assertInstanceOf(Result.Failed::class.java, attempt())
            assertTrue(success(rows()).isEmpty())
            val device = UUID.randomUUID()
            success(run { insert(device) })
            assertInstanceOf(Result.Failed::class.java, run { bind(device, addParent()) })
            success(run { bind(device) })
            assertInstanceOf(Result.Failed::class.java, run { insert(UUID.randomUUID()) })
            assertEquals(
                0,
                success(run { runtime.update("delete from collaboration_space_devices") }),
            )
            assertEquals(
                0,
                success(run { runtime.update("delete from collaboration_space_device_sessions") }),
            )
            sql.update(
                "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
                employer,
                account,
            )
            assertTrue(success(rows()).isEmpty())
            assertTrue(
                success(
                        run {
                            runtime.queryForList("select * from collaboration_space_legacy_parents")
                        }
                    )
                    .isEmpty()
            )
            assertEquals(
                1,
                sql.queryForObject(
                    "select count(*) from collaboration_space_devices",
                    Int::class.java,
                ),
            )
        }
    }

    @Test
    fun freshDevicesHaveDistinctProviderIdsAndCannotBindRevokedStaleForeignOrDuplicateParents() {
        with(Fixture()) {
            migrate()
            fun fresh(session: UUID = parent): UUID =
                UUID.randomUUID().also {
                    success(run { insert(it, origin = "SPACE", session = session) })
                }
            val device = fresh()
            val original = success(rows()).single()
            assertEquals("HRISG_${compact(device)}", original["matrix_device_id"])
            assertNull(original["legacy_key_fingerprint"])
            val wrong = addParent(peer)
            assertInstanceOf(Result.Failed::class.java, run { bind(device, wrong) })
            sql.update(
                "update native_sessions set revoked_at=?::timestamptz where id=?",
                at.toString(),
                parent,
            )
            assertInstanceOf(Result.Failed::class.java, run { bind(device) })
            val next = addParent()
            val second = fresh(next)
            sql.update("update native_sessions set credential_version=1 where id=?", next)
            assertInstanceOf(Result.Failed::class.java, run { bind(second, next) })
            sql.update(
                "update native_sessions set credential_version=0,access_expires_at=?::timestamptz where id=?",
                at.toString(),
                next,
            )
            assertInstanceOf(Result.Failed::class.java, run { bind(second, next) })
            sql.update(
                "update native_sessions set access_expires_at=?::timestamptz where id=?",
                at.plusSeconds(900).toString(),
                next,
            )
            success(run { bind(second, next) })
            val duplicate = fresh(next)
            assertInstanceOf(Result.Failed::class.java, run { bind(duplicate, next) })
            assertInstanceOf(
                Result.Failed::class.java,
                run {
                    runtime.update(
                        "update collaboration_space_devices set matrix_device_id='HRISG_changed',version=1 where id=?",
                        second,
                    )
                },
            )
            success(
                run {
                    runtime.update(
                        "update collaboration_space_devices set state='ACTIVE',version=1,updated_at=?::timestamptz where id=?",
                        at.plusSeconds(1).toString(),
                        second,
                    )
                }
            )
            assertInstanceOf(
                Result.Failed::class.java,
                run {
                    runtime.update(
                        "update collaboration_space_devices set state='PROVISIONING',version=2 where id=?",
                        second,
                    )
                },
            )
            success(
                run {
                    runtime.update(
                        "update collaboration_space_devices set state='REVOKED',version=2,revoked_at=?::timestamptz,updated_at=?::timestamptz where id=?",
                        at.plusSeconds(2).toString(),
                        at.plusSeconds(2).toString(),
                        second,
                    )
                }
            )
            assertInstanceOf(
                Result.Failed::class.java,
                run {
                    runtime.update(
                        "update collaboration_space_devices set state='ACTIVE',version=3,revoked_at=null where id=?",
                        second,
                    )
                },
            )
            assertThrows(DataAccessException::class.java) {
                sql.update(
                    "update collaboration_space_device_sessions set native_session_id=?",
                    wrong,
                )
            }
        }
    }

    private inner class Fixture {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        val sql = JdbcTemplate(source)
        val runtimeSource =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "space_device_fixture",
                "isolated-fixture-only",
            )
        val runtime = JdbcTemplate(runtimeSource)
        val transactions = PostgresTransactionRunner(JdbcTransactionManager(runtimeSource), runtime)
        val company = UUID.randomUUID()
        val employer = UUID.randomUUID()
        val account = UUID.randomUUID()
        val peer = UUID.randomUUID()
        val legacyDevice = UUID.randomUUID()
        val at = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val actor =
            Actor(
                account,
                null,
                emptySet(),
                at.minusSeconds(120),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val space: UUID
        val parent: UUID
        val fingerprint = "a".repeat(64)
        val legacyUser = "@hris_${compact(company)}_${compact(account)}:matrix.example.test"

        init {
            Flyway.configure().dataSource(source).target("119").load().migrate()
            space =
                requireNotNull(
                    sql.queryForObject("select id from collaboration_spaces", UUID::class.java)
                )
            for ((index, id) in listOf(company, employer).withIndex()) sql.update(
                "insert into companies(id,code,name,timezone) values(?,?,?,'UTC')",
                id,
                "SPACEDEV$index",
                "Fictional company $index",
            )
            for ((index, id) in listOf(account, peer).withIndex()) {
                sql.update(
                    "insert into accounts(id,email,display_name) values(?,?,?)",
                    id,
                    "space-device-$index@example.test",
                    "Fictional employee $index",
                )
                sql.update(
                    "insert into company_memberships(company_id,account_id) values(?,?)",
                    employer,
                    id,
                )
                sql.update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                    employer,
                    id,
                )
            }
            parent = addParent()
            sql.update(
                "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional','PROVISIONING',0,?::timestamptz,?::timestamptz)",
                company,
                legacyDevice,
                account,
                at.minusSeconds(60).toString(),
                at.minusSeconds(60).toString(),
            )
            sql.update(
                "insert into collaboration_device_sessions(company_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?::timestamptz)",
                company,
                legacyDevice,
                account,
                parent,
                at.minusSeconds(30).toString(),
            )
            sql.update(
                "insert into collaboration_provider_sessions(company_id,device_id,account_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) values(?,?,?,?,?,0,'fixture-envelope-not-a-real-token',?::timestamptz,?::timestamptz,?::timestamptz)",
                company,
                legacyDevice,
                account,
                legacyUser,
                "HRIS_${compact(legacyDevice)}",
                at.minusSeconds(30).toString(),
                at.minusSeconds(30).toString(),
                at.plusSeconds(270).toString(),
            )
            sql.update(
                "update collaboration_devices set state='ACTIVE',version=1,updated_at=?::timestamptz where company_id=? and id=?",
                at.minusSeconds(30).toString(),
                company,
                legacyDevice,
            )
            sql.update(
                "insert into collaboration_device_key_publications(company_id,device_id,account_id,fingerprint,version,reserved_at) values(?,?,?,?,0,?::timestamptz)",
                company,
                legacyDevice,
                account,
                fingerprint,
                at.minusSeconds(20).toString(),
            )
            sql.update(
                "update collaboration_device_key_publications set version=1,published_at=?::timestamptz where company_id=? and device_id=?",
                at.minusSeconds(19).toString(),
                company,
                legacyDevice,
            )
            sql.update(
                "insert into collaboration_space_identities(space_id,account_id,matrix_server_name,matrix_user_id,origin,legacy_company_id,legacy_device_id,created_at) values(?,?,'matrix.example.test',?,'LEGACY',?,?,?::timestamptz)",
                space,
                account,
                legacyUser,
                company,
                legacyDevice,
                at.minusSeconds(10).toString(),
            )
        }

        fun addParent(owner: UUID = account): UUID =
            UUID.randomUUID().also {
                sql.update(
                    "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,credential_version,access_expires_at,created_at,rotated_at) values(?,?,?,?::timestamptz,?::timestamptz,0,?::timestamptz,?::timestamptz,?::timestamptz)",
                    it,
                    owner,
                    compact(it).repeat(2),
                    actor.authenticatedAt.toString(),
                    at.plusSeconds(3600).toString(),
                    at.plusSeconds(900).toString(),
                    at.minusSeconds(60).toString(),
                    at.minusSeconds(30).toString(),
                )
            }

        fun migrate() {
            Flyway.configure().dataSource(source).target("120").load().migrate()
            sql.execute("create role space_device_fixture login password 'isolated-fixture-only'")
            sql.execute("grant usage on schema public to space_device_fixture")
            sql.execute(
                "grant select,insert,update,delete on all tables in schema public to space_device_fixture"
            )
        }

        fun <T> run(current: Actor = actor, body: () -> T): Result<T> =
            transactions.run(current) { safeDatabaseCall(body) }

        fun insert(
            id: UUID,
            origin: String = "LEGACY",
            selectedSpace: UUID = space,
            user: String = legacyUser,
            oldDevice: UUID = legacyDevice,
            key: String = fingerprint,
            session: UUID = parent,
            platform: String = "ANDROID",
            providerDevice: String =
                if (origin == "LEGACY") "HRIS_${compact(oldDevice)}" else "HRISG_${compact(id)}",
        ): Int =
            runtime.update(
                "insert into collaboration_space_devices(space_id,id,account_id,matrix_user_id,matrix_device_id,origin,legacy_company_id,legacy_device_id,legacy_key_fingerprint,origin_native_session_id,platform,label,state,version,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?,?,'Fictional','PROVISIONING',0,?::timestamptz,?::timestamptz)",
                selectedSpace,
                id,
                account,
                user,
                providerDevice,
                origin,
                if (origin == "LEGACY") company else null,
                if (origin == "LEGACY") oldDevice else null,
                if (origin == "LEGACY") key else null,
                session,
                platform,
                at.toString(),
                at.toString(),
            )

        fun bind(device: UUID, session: UUID = parent): Int =
            runtime.update(
                "insert into collaboration_space_device_sessions(space_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?::timestamptz)",
                space,
                device,
                account,
                session,
                at.toString(),
            )

        fun rows(current: Actor = actor) =
            run(current) {
                runtime.queryForList("select * from collaboration_space_devices order by id")
            }

        fun legacyRows(database: JdbcTemplate = sql) =
            listOf(
                    "collaboration_devices",
                    "collaboration_provider_sessions",
                    "collaboration_device_key_publications",
                )
                .associateWith { database.queryForList("select * from $it") }

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
                "/tmp/space-device.dump",
            )
            command("createdb", "-U", postgres.username, "space_device_restored")
            command(
                "pg_restore",
                "-U",
                postgres.username,
                "-d",
                "space_device_restored",
                "--single-transaction",
                "--exit-on-error",
                "/tmp/space-device.dump",
            )
            return JdbcTemplate(
                DriverManagerDataSource(
                    postgres.jdbcUrl.substringBeforeLast('/') + "/space_device_restored",
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
