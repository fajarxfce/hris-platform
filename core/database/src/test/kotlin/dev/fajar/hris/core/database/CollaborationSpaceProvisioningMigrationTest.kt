package dev.fajar.hris.core.database

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
class CollaborationSpaceProvisioningMigrationTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun requestRequiresExactCurrentNativeProofAndOwnedCancellationSurvivesOffboarding() {
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
                    { insert(nativeVersion = 1) },
                    { insert(auth = actor.authenticatedAt.minusSeconds(1)) },
                    { insert(mfa = now()) },
                    { insert(platform = "IOS") },
                    { insert(lifetime = 301) },
                    { insert(lifetime = 0) },
                )) assertInstanceOf(Result.Failed::class.java, run { invalid() })
            assertEquals(1, success(run { insert() }))
            val initial = success(rows())
            assertInstanceOf(Result.Failed::class.java, run { insert() })
            for (assignment in
                listOf(
                    "client_build=client_build+1",
                    "native_session_version=1",
                    "native_session_id=gen_random_uuid()",
                    "revision=1",
                    "attempts=1",
                    "state='RUNNING',attempts=1,lease_owner=gen_random_uuid(),lease_token=gen_random_uuid(),lease_until=clock_timestamp()+interval '30 seconds'",
                )) assertInstanceOf(
                Result.Failed::class.java,
                run { runtime.update("update collaboration_space_provisioning set $assignment") },
            )
            assertEquals(initial, success(rows()))
            assertInstanceOf(
                Result.Failed::class.java,
                run {
                    runtime.queryForList(
                        "select * from claim_collaboration_space_provisioning(?,1,30)",
                        UUID.randomUUID(),
                    )
                },
            )
            assertInstanceOf(
                Result.Failed::class.java,
                run { runtime.execute("set role hris_worker_capability") },
            )
            owner.update(
                "update company_memberships set active=false,version=version+1 where account_id=?",
                account,
            )
            owner.update("delete from native_sessions where id=?", parent)
            assertEquals(initial, success(rows()))
            assertEquals(1, success(run { cancel() }))
            val cancelled = success(rows()).single()
            assertEquals("CANCELLED", cancelled["state"])
            assertEquals(0L, cancelled["last_outcome_revision"])
            assertInstanceOf(Result.Failed::class.java, run { renew(0) })
            assertThrows(DataAccessException::class.java) {
                owner.update("delete from collaboration_space_provisioning")
            }
            assertEquals(
                0,
                success(run { runtime.update("delete from collaboration_space_provisioning") }),
            )
        }
    }

    @Test
    fun competingWorkerClaimsRespectBatchAndGlobalCapacityWithoutReceivingDeviceAuthority() {
        with(Fixture()) {
            migrate()
            // Direct schema fixtures deliberately bypass the already-tested five-device use case
            // to exercise a larger infrastructure queue with one bounded head per device.
            success(run { insert(lifetime = 300) })
            repeat(23) {
                val (nextDevice, nextParent) = addDevice()
                success(run { insert(nextDevice, nextParent, lifetime = 300) })
            }
            for ((limit, lease) in listOf(0 to 30, 9 to 30, 1 to 0, 1 to 61)) assertThrows(
                DataAccessException::class.java
            ) {
                claim(limit, lease)
            }
            val pool = Executors.newFixedThreadPool(2)
            val barrier = CyclicBarrier(2)
            val observed = mutableListOf<Map<String, Any?>>()
            try {
                val futures =
                    (1..2).map {
                        pool.submit<List<Map<String, Any?>>> {
                            barrier.await(10, TimeUnit.SECONDS)
                            claim(8, 60)
                        }
                    }
                for (future in futures) {
                    val batch = future.get(20, TimeUnit.SECONDS)
                    assertTrue(batch.size <= 8)
                    observed.addAll(batch)
                }
            } finally {
                pool.shutdownNow()
                assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
            }
            repeat(2) { observed.addAll(claim(8, 60)) }
            assertEquals(16, observed.size)
            assertEquals(16, observed.map { it["lease_token"] }.toSet().size)
            assertEquals(16, observed.map { it["device_id"] }.toSet().size)
            assertTrue(claim(8, 60).isEmpty())
            assertEquals(
                16,
                worker.queryForObject(
                    "select count(*) from collaboration_space_provisioning where state='RUNNING'",
                    Int::class.java,
                ),
            )
            assertEquals(
                0,
                worker.queryForObject(
                    "select count(*) from collaboration_space_devices",
                    Int::class.java,
                ),
            )
            assertEquals(
                0,
                runtime.queryForObject(
                    "select count(*) from collaboration_space_provisioning",
                    Int::class.java,
                ),
            )
            for (lease in observed) assertEquals(1, finish(lease))
            assertEquals(8, claim(8, 60).size)
        }
    }

    @Test
    fun expiredLeaseIsFencedAndFiniteAttemptsRetainExhaustionWithoutANinthProviderClaim() {
        with(Fixture()) {
            migrate()
            success(run { insert() })
            val old = claim(1, 1).single()
            Thread.sleep(1100)
            var lease = claim(1, 30).single()
            assertNotEquals(old["lease_token"], lease["lease_token"])
            assertEquals(2, lease["attempts"])
            assertEquals(0, finish(old))
            for (attempt in 3..8) {
                assertEquals(1, finish(lease, "QUEUED"))
                Thread.sleep(130)
                lease = claim(1, if (attempt == 8) 1 else 30).single()
                assertEquals(attempt, lease["attempts"])
            }
            assertThrows(DataAccessException::class.java) { finish(lease, "QUEUED") }
            Thread.sleep(1100)
            assertTrue(claim().isEmpty())
            assertEquals(0, finish(lease))
            val exhausted = success(rows()).single()
            assertEquals("FAILED", exhausted["state"])
            assertEquals("ATTEMPTS_EXHAUSTED", exhausted["failure_code"])
            assertEquals(8, exhausted["attempts"])
            assertNull(exhausted["lease_token"])
            assertEquals(exhausted["completed_at"], exhausted["last_outcome_at"])
            assertTrue(claim().isEmpty())
            assertEquals(exhausted, success(rows()).single())
            val extraDevices =
                (1..17)
                    .map {
                        val (extraDevice, extraParent) = addDevice()
                        success(run { insert(extraDevice, extraParent, lifetime = 1) })
                        extraDevice
                    }
                    .toSet()
            Thread.sleep(1100)
            assertTrue(claim().isEmpty())
            val firstBatch = success(rows()).filter { it["device_id"] in extraDevices }
            assertEquals(8, firstBatch.count { it["state"] == "FAILED" })
            assertEquals(9, firstBatch.count { it["state"] == "QUEUED" })
            repeat(2) { assertTrue(claim().isEmpty()) }
            val finished = success(rows()).filter { it["device_id"] in extraDevices }
            assertEquals(17, finished.size)
            assertTrue(
                finished.all { it["state"] == "FAILED" && it["failure_code"] == "REQUEST_EXPIRED" }
            )
        }
    }

    @Test
    fun retryRetainsPriorOutcomeWithFreshProofAndArchivePreservesScopeIdentityAndFences() {
        with(Fixture()) {
            migrate()
            success(run { insert() })
            val obsolete = claim().single()
            assertEquals(1, success(run { cancel() }))
            val prior = success(rows()).single()
            owner.update(
                "update native_sessions set version=version+1,rotated_at=clock_timestamp() where id=?",
                parent,
            )
            assertInstanceOf(Result.Failed::class.java, run { renew(0) })
            assertEquals(1, success(run { renew(1) }))
            val current = success(rows()).single()
            assertEquals(1L, current["revision"])
            assertEquals("QUEUED", current["state"])
            assertEquals(0, current["attempts"])
            assertEquals(prior["completed_at"], current["last_outcome_at"])
            assertEquals("CANCELLED", current["last_outcome_state"])
            assertEquals(0L, current["last_outcome_revision"])
            assertEquals(0, finish(obsolete))
            val devices = owner.queryForList("select * from collaboration_space_devices")
            val restored = restore()
            assertEquals(
                listOf(current),
                restored.queryForList("select * from collaboration_space_provisioning"),
            )
            assertEquals(
                devices,
                restored.queryForList("select * from collaboration_space_devices"),
            )
            assertThrows(DataAccessException::class.java) {
                restored.update("delete from collaboration_space_provisioning")
            }
            assertThrows(DataAccessException::class.java) {
                restored.update(
                    "update collaboration_space_provisioning set native_session_id=gen_random_uuid()"
                )
            }
            val restoredRuntime =
                DriverManagerDataSource(
                    url("space_provisioning_restored"),
                    "space_provisioning_fixture",
                    "isolated-fixture-only",
                )
            val jdbc = JdbcTemplate(restoredRuntime)
            val tx = PostgresTransactionRunner(JdbcTransactionManager(restoredRuntime), jdbc)
            assertEquals(
                listOf(current),
                success(
                    tx.run(actor) {
                        safeDatabaseCall {
                            jdbc.queryForList("select * from collaboration_space_provisioning")
                        }
                    }
                ),
            )
            assertTrue(
                success(
                        tx.run(actor.copy(accountId = UUID.randomUUID())) {
                            safeDatabaseCall {
                                jdbc.queryForList("select * from collaboration_space_provisioning")
                            }
                        }
                    )
                    .isEmpty()
            )
            assertEquals(
                0,
                jdbc.queryForObject(
                    "select count(*) from collaboration_space_provisioning",
                    Int::class.java,
                ),
            )
            assertEquals(1L, claim().single()["revision"])
        }
    }

    private inner class Fixture {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        val owner = JdbcTemplate(source)
        val runtimeSource =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "space_provisioning_fixture",
                "isolated-fixture-only",
            )
        val runtime = JdbcTemplate(runtimeSource)
        val worker =
            JdbcTemplate(
                DriverManagerDataSource(
                    postgres.jdbcUrl,
                    "space_provisioning_worker",
                    "isolated-fixture-only",
                )
            )
        val transactions = PostgresTransactionRunner(JdbcTransactionManager(runtimeSource), runtime)
        val account = UUID.randomUUID()
        val company = UUID.randomUUID()
        val parent = UUID.randomUUID()
        val device = UUID.randomUUID()
        val at = now()
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
            Flyway.configure().dataSource(source).target("121").load().migrate()
            space =
                requireNotNull(
                    owner.queryForObject("select id from collaboration_spaces", UUID::class.java)
                )
            user = "@hrisg_${compact(space)}_${compact(account)}:matrix.example.test"
            owner.update(
                "insert into companies(id,code,name,timezone) values(?,'PROVISIONING','Fictional','UTC')",
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
                "insert into collaboration_space_identities(space_id,account_id,matrix_server_name,matrix_user_id,origin,created_at) values(?,?,'matrix.example.test',?,'SPACE',?::timestamptz)",
                space,
                account,
                user,
                at.toString(),
            )
            addDevice(device, parent)
        }

        fun addDevice(
            id: UUID = UUID.randomUUID(),
            session: UUID = UUID.randomUUID(),
        ): Pair<UUID, UUID> {
            owner.update(
                "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,credential_version,access_expires_at,created_at,rotated_at) values(?,?,?,?::timestamptz,?::timestamptz,0,?::timestamptz,?::timestamptz,?::timestamptz)",
                session,
                account,
                compact(session).repeat(2),
                actor.authenticatedAt.toString(),
                at.plusSeconds(3600).toString(),
                at.plusSeconds(900).toString(),
                at.minusSeconds(60).toString(),
                at.minusSeconds(30).toString(),
            )
            owner.update(
                "insert into collaboration_space_devices(space_id,id,account_id,matrix_user_id,matrix_device_id,origin,origin_native_session_id,platform,label,state,version,created_at,updated_at) values(?,?,?,?,?,'SPACE',?,'ANDROID','Fictional','PROVISIONING',0,?::timestamptz,?::timestamptz)",
                space,
                id,
                account,
                user,
                "HRISG_${compact(id)}",
                session,
                at.toString(),
                at.toString(),
            )
            owner.update(
                "insert into collaboration_space_device_sessions(space_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?::timestamptz)",
                space,
                id,
                account,
                session,
                at.toString(),
            )
            return id to session
        }

        fun migrate() {
            Flyway.configure().dataSource(source).target("122").load().migrate()
            for (role in listOf("space_provisioning_fixture", "space_provisioning_worker")) {
                owner.execute("create role $role login password 'isolated-fixture-only'")
                owner.execute("grant usage on schema public to $role")
                owner.execute(
                    "grant select,insert,update,delete on all tables in schema public to $role"
                )
            }
            owner.execute("grant hris_worker_capability to space_provisioning_worker")
        }

        fun <T> run(current: Actor = actor, body: () -> T): Result<T> =
            transactions.run(current) { safeDatabaseCall(body) }

        fun rows(current: Actor = actor) =
            run(current) {
                runtime.queryForList(
                    "select * from collaboration_space_provisioning order by device_id"
                )
            }

        fun insert(
            id: UUID = device,
            session: UUID = parent,
            lifetime: Int = 120,
            nativeVersion: Long = 0,
            auth: Instant = actor.authenticatedAt,
            mfa: Instant? = null,
            platform: String = "ANDROID",
        ): Int {
            val requested = now()
            return runtime.update(
                "insert into collaboration_space_provisioning(space_id,device_id,account_id,native_session_id,revision,native_session_version,credential_version,authenticated_at,mfa_verified_at,correlation_id,client_platform,client_build,created_at,requested_at,expires_at,available_at,state,attempts) values(?,?,?,?,0,?,0,?::timestamptz,?::timestamptz,?,?,28,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz,'QUEUED',0)",
                space,
                id,
                account,
                session,
                nativeVersion,
                auth.toString(),
                mfa?.toString(),
                UUID.randomUUID(),
                platform,
                requested.toString(),
                requested.toString(),
                requested.plusSeconds(lifetime.toLong()).toString(),
                requested.toString(),
            )
        }

        fun claim(limit: Int = 1, seconds: Int = 30): List<Map<String, Any?>> =
            worker.queryForList(
                "select * from claim_collaboration_space_provisioning(?,?,?)",
                UUID.randomUUID(),
                limit,
                seconds,
            )

        fun finish(
            lease: Map<String, Any?>,
            state: String = "SUCCEEDED",
            failure: String? = null,
        ): Int {
            val where =
                "where space_id=? and device_id=? and revision=? and lease_owner=? and lease_token=? and state='RUNNING' and lease_until>clock_timestamp()"
            val keys =
                arrayOf(
                    lease["space_id"],
                    lease["device_id"],
                    lease["revision"],
                    lease["lease_owner"],
                    lease["lease_token"],
                )
            return if (state == "QUEUED")
                worker.update(
                    "update collaboration_space_provisioning set state='QUEUED',available_at=clock_timestamp()+interval '100 milliseconds',lease_owner=null,lease_token=null,lease_until=null $where",
                    *keys,
                )
            else
                worker.update(
                    "with stamp as materialized(select clock_timestamp() at_time) update collaboration_space_provisioning set state=?,completed_at=stamp.at_time,failure_code=?,last_outcome_revision=revision,last_outcome_state=?,last_outcome_at=stamp.at_time,last_failure_code=?,lease_owner=null,lease_token=null,lease_until=null from stamp $where",
                    state,
                    failure,
                    state,
                    failure,
                    *keys,
                )
        }

        fun cancel(): Int =
            runtime.update(
                "with stamp as materialized(select clock_timestamp() at_time) update collaboration_space_provisioning set state='CANCELLED',completed_at=stamp.at_time,last_outcome_revision=revision,last_outcome_state='CANCELLED',last_outcome_at=stamp.at_time,last_failure_code=null,lease_owner=null,lease_token=null,lease_until=null from stamp where device_id=? and state in ('QUEUED','RUNNING')",
                device,
            )

        fun renew(nativeVersion: Long): Int {
            val requested = now()
            return runtime.update(
                "update collaboration_space_provisioning set revision=revision+1,native_session_version=?,correlation_id=?,requested_at=?::timestamptz,expires_at=?::timestamptz,available_at=?::timestamptz,state='QUEUED',attempts=0,completed_at=null,failure_code=null,lease_owner=null,lease_token=null,lease_until=null where device_id=?",
                nativeVersion,
                UUID.randomUUID(),
                requested.toString(),
                requested.plusSeconds(120).toString(),
                requested.toString(),
                device,
            )
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
                "/tmp/space-provisioning.dump",
            )
            command("createdb", "-U", postgres.username, "space_provisioning_restored")
            command(
                "pg_restore",
                "-U",
                postgres.username,
                "-d",
                "space_provisioning_restored",
                "--single-transaction",
                "--exit-on-error",
                "/tmp/space-provisioning.dump",
            )
            return JdbcTemplate(
                DriverManagerDataSource(
                    url("space_provisioning_restored"),
                    postgres.username,
                    postgres.password,
                )
            )
        }
    }

    private fun url(database: String) = postgres.jdbcUrl.substringBeforeLast('/') + "/" + database

    private fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS)

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
