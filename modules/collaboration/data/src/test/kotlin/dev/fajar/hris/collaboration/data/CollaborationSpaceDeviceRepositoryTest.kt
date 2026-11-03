package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.PostgresCollaborationSpaceDeviceDataSource
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationSpaceDeviceRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollaborationSpaceDeviceRepositoryTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:18.6-alpine")
    }

    private lateinit var owner: JdbcTemplate
    private lateinit var runtime: DriverManagerDataSource
    private lateinit var space: UUID

    @BeforeAll
    fun database() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(source).load().migrate()
        owner = JdbcTemplate(source)
        space =
            requireNotNull(
                owner.queryForObject("select id from collaboration_spaces", UUID::class.java)
            )
        owner.execute("create role space_registry_fixture login password 'isolated-fixture-only'")
        owner.execute("grant usage on schema public to space_registry_fixture")
        owner.execute("grant select on all tables in schema public to space_registry_fixture")
        owner.execute(
            "grant insert on collaboration_space_devices,collaboration_space_device_sessions to space_registry_fixture"
        )
        owner.execute("grant update on collaboration_space_devices to space_registry_fixture")
        runtime =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "space_registry_fixture",
                "isolated-fixture-only",
            )
    }

    @Test
    fun quotaCountsEachLiveParentOnceAcrossLegacyCompaniesAndGroupDevicesInOneEligibleSnapshot() {
        with(Fixture()) {
            val parents = (0..7).map { parent() }
            for (index in 0..5) {
                legacy(parents[index], companies[0])
                legacy(parents[index], companies[1])
            }
            for (index in listOf(0, 1, 2, 6, 7)) success(
                transactions.run(actor) { repository.register(device(parents[index])) }
            )
            fun count(exclude: UUID = UUID.randomUUID()) =
                success(
                    transactions.run(actor) {
                        repository.otherActiveParents(space, account, exclude, at)
                    }
                )
            assertEquals(6, count()) // Saturated; do not scan/count every old company as a device.
            for (index in 0..3) owner.update(
                "update native_sessions set revoked_at=?::timestamptz where id=?",
                at.toString(),
                parents[index],
            )
            assertEquals(4, count())
            assertEquals(3, count(parents[7]))
            owner.update(
                "update native_sessions set access_expires_at=?::timestamptz where id=?",
                at.minusSeconds(1).toString(),
                parents[4],
            )
            assertEquals(4, count()) // A refresh-capable parent continues occupying its slot.
            owner.update(
                "update native_sessions set expires_at=?::timestamptz where id=?",
                at.toString(),
                parents[4],
            )
            assertEquals(3, count())
            owner.update("update native_sessions set credential_version=-1 where id=?", parents[5])
            assertEquals(2, count())
            owner.update(
                "update company_memberships set active=false,version=version+1 where account_id=?",
                account,
            )
            val unavailable =
                transactions.run(actor) {
                    repository.otherActiveParents(space, account, UUID.randomUUID(), at)
                }
            assertEquals("collaboration_access_denied", (unavailable as Result.Failed).failure.code)
            owner.update(
                "update company_memberships set active=true,version=version+1 where account_id=?",
                account,
            )
            assertEquals(2, count())
            assertInstanceOf(Result.Failed::class.java, unavailable)
            assertInstanceOf(
                Result.Failed::class.java,
                transactions.run(actor) {
                    repository.otherActiveParents(UUID.randomUUID(), account, parents[0], at)
                },
            )
            val peer = Fixture()
            assertInstanceOf(
                Result.Failed::class.java,
                transactions.run(peer.actor) {
                    repository.otherActiveParents(space, account, parents[0], at)
                },
            )
            owner.update(
                "update accounts set security_version=security_version+1 where id=?",
                account,
            )
            assertEquals(0, count())
        }
    }

    @Test
    fun bindingFailureRollsBackTheInsertedIdentityAndParentPurgeCannotReassignIt() {
        with(Fixture()) {
            val parent = parent()
            val device = device(parent)
            success(transactions.run(actor) { repository.register(device) })
            fun read(current: Actor = actor, requestedParent: UUID = parent) =
                success(
                    transactions.run(current) {
                        repository.findBound(space, account, requestedParent)
                    }
                )
            assertEquals(device, read())
            assertEquals(device, read(actor.copy(companyId = companies[1])))
            assertNull(read(requestedParent = UUID.randomUUID()))
            val peer = Fixture()
            assertNull(read(peer.actor))
            val duplicate = device(parent)
            assertInstanceOf(
                Result.Failed::class.java,
                transactions.run(actor) { repository.register(duplicate) },
            )
            assertEquals(
                0,
                owner.queryForObject(
                    "select count(*) from collaboration_space_devices where id=?",
                    Int::class.java,
                    duplicate.id,
                ),
            )
            assertEquals(device, read())
            val denied = device(parent())
            assertInstanceOf(
                Result.Failed::class.java,
                transactions.run(actor.copy(companyId = companies[0])) {
                    repository.register(denied)
                },
            )
            assertEquals(
                0,
                owner.queryForObject(
                    "select count(*) from collaboration_space_devices where id=?",
                    Int::class.java,
                    denied.id,
                ),
            )
            owner.update("delete from native_sessions where id=?", parent)
            assertNull(read())
            assertEquals(
                1,
                owner.queryForObject(
                    "select count(*) from collaboration_space_devices where id=? and origin_native_session_id=?",
                    Int::class.java,
                    device.id,
                    parent,
                ),
            )
            val replacement = device.copy(originNativeSessionId = parent())
            assertInstanceOf(
                Result.Failed::class.java,
                transactions.run(actor) { repository.register(replacement) },
            )
            assertEquals(
                1,
                owner.queryForObject(
                    "select count(*) from collaboration_space_devices where id=? and origin_native_session_id=?",
                    Int::class.java,
                    device.id,
                    parent,
                ),
            )
        }
    }

    @Test
    fun competingDeviceTransitionsFenceLateActivationAndOwnedCleanupSurvivesOffboarding() {
        with(Fixture()) {
            val parent = parent()
            val initial = device(parent)
            success(transactions.run(actor) { repository.register(initial) })
            fun read(current: Actor = actor, lock: Boolean = false) =
                success(
                    transactions.run(current) {
                        repository.metadata(space, account, initial.id, lock)
                    }
                )
            assertEquals(initial, read(lock = true))
            assertEquals(initial, read(actor.copy(companyId = companies[0])))
            assertNull(read(actor.copy(accountId = UUID.randomUUID())))
            val barrier = java.util.concurrent.CyclicBarrier(2)
            val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
            try {
                val candidates =
                    listOf(
                        initial.copy(
                            state = CollaborationDeviceState.ACTIVE,
                            version = 1,
                            updatedAt = at.plusSeconds(1),
                        ),
                        initial.copy(
                            state = CollaborationDeviceState.REVOKED,
                            version = 1,
                            updatedAt = at.plusSeconds(1),
                            revokedAt = at.plusSeconds(1),
                        ),
                    )
                val futures =
                    candidates.map { candidate ->
                        pool.submit<Pair<CollaborationSpaceDevice, Result<Unit>>> {
                            barrier.await(10, java.util.concurrent.TimeUnit.SECONDS)
                            candidate to
                                transactions.run(actor) { repository.replace(candidate, 0) }
                        }
                    }
                val results = futures.map { it.get(20, java.util.concurrent.TimeUnit.SECONDS) }
                assertEquals(1, results.count { it.second is Result.Success })
                assertEquals(1, results.count { it.second is Result.Failed })
                val winner = results.single { it.second is Result.Success }.first
                assertEquals(winner, read())
                if (winner.state != CollaborationDeviceState.REVOKED) {
                    success(
                        transactions.run(actor) {
                            repository.replace(
                                winner.copy(
                                    state = CollaborationDeviceState.REVOKED,
                                    version = 2,
                                    revokedAt = at.plusSeconds(2),
                                    updatedAt = at.plusSeconds(2),
                                ),
                                1,
                            )
                        }
                    )
                }
            } finally {
                pool.shutdownNow()
                assertTrue(pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
            }
            val revoked = requireNotNull(read())
            assertEquals(CollaborationDeviceState.REVOKED, revoked.state)
            assertInstanceOf(
                Result.Failed::class.java,
                transactions.run(actor) {
                    repository.replace(
                        revoked.copy(
                            state = CollaborationDeviceState.ACTIVE,
                            revokedAt = null,
                            version = revoked.version + 1,
                        ),
                        revoked.version,
                    )
                },
            )
            assertEquals(revoked, read())
            owner.update("delete from native_sessions where id=?", parent)
            owner.update(
                "update company_memberships set active=false,version=version+1 where account_id=?",
                account,
            )
            assertEquals(revoked, read(lock = true))
            assertNull(
                success(transactions.run(actor) { repository.findBound(space, account, parent) })
            )
            assertNull(read(actor.copy(accountId = UUID.randomUUID())))
        }
        with(Fixture()) {
            val parent = parent()
            val initial = device(parent)
            success(transactions.run(actor) { repository.register(initial) })
            owner.update("delete from native_sessions where id=?", parent)
            owner.update(
                "update company_memberships set active=false,version=version+1 where account_id=?",
                account,
            )
            val revoked =
                initial.copy(state = CollaborationDeviceState.REVOKED, version = 1, revokedAt = at)
            assertInstanceOf(
                Result.Failed::class.java,
                transactions.run(actor.copy(companyId = companies[0])) {
                    repository.replace(revoked, 0)
                },
            )
            assertInstanceOf(
                Result.Failed::class.java,
                transactions.run(actor.copy(accountId = UUID.randomUUID())) {
                    repository.replace(revoked, 0)
                },
            )
            assertInstanceOf(
                Result.Failed::class.java,
                transactions.run(actor) {
                    repository.replace(
                        initial.copy(state = CollaborationDeviceState.ACTIVE, version = 1),
                        0,
                    )
                },
            )
            success(transactions.run(actor) { repository.replace(revoked, 0) })
            assertEquals(
                revoked,
                success(
                    transactions.run(actor) {
                        repository.metadata(space, account, initial.id, lock = true)
                    }
                ),
            )
        }
    }

    @Test
    fun devicePublicationRollsBackOnFailureOrCancellationAndCannotChangeImmutableProviderCoordinates() {
        with(Fixture()) {
            val initial = device(parent())
            success(transactions.run(actor) { repository.register(initial) })
            val active = initial.copy(state = CollaborationDeviceState.ACTIVE, version = 1)
            fun read() =
                success(transactions.run(actor) { repository.metadata(space, account, initial.id) })
            assertInstanceOf(
                Result.Failed::class.java,
                transactions.run(actor) {
                    success(repository.replace(active, 0))
                    Result.Failed(
                        dev.fajar.hris.core.domain.Failure(
                            dev.fajar.hris.core.domain.FailureKind.CONFLICT,
                            "fixture_failure",
                        )
                    )
                },
            )
            assertEquals(initial, read())
            for (error in
                listOf(
                    java.util.concurrent.CancellationException("fixture"),
                    InterruptedException("fixture"),
                )) {
                assertThrows(error.javaClass) {
                    transactions.run<Unit>(actor) {
                        success(repository.replace(active, 0))
                        throw error
                    }
                }
                assertEquals(initial, read())
            }
            for (changed in
                listOf(
                    active.copy(originNativeSessionId = parent()),
                    active.copy(createdAt = at.minusSeconds(1)),
                    active.copy(platform = CollaborationPlatform.IOS),
                )) {
                assertInstanceOf(
                    Result.Failed::class.java,
                    transactions.run(actor) { repository.replace(changed, 0) },
                )
                assertEquals(initial, read())
            }
            success(transactions.run(actor) { repository.replace(active, 0) })
            assertInstanceOf(
                Result.Failed::class.java,
                transactions.run(actor) { repository.replace(active, 0) },
            )
            assertEquals(active, read())
        }
    }

    private inner class Fixture {
        val account = UUID.randomUUID()
        val companies = listOf(UUID.randomUUID(), UUID.randomUUID())
        val at = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val actor =
            Actor(
                account,
                null,
                emptySet(),
                at.minusSeconds(120),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val transactions =
            PostgresTransactionRunner(JdbcTransactionManager(runtime), JdbcTemplate(runtime))
        val repository =
            StoredCollaborationSpaceDeviceRepository(
                PostgresCollaborationSpaceDeviceDataSource(
                    DSL.using(TransactionAwareDataSourceProxy(runtime), SQLDialect.POSTGRES)
                )
            )
        val user = "@hrisg_${compact(space)}_${compact(account)}:matrix.example.test"

        init {
            owner.update(
                "insert into accounts(id,email,display_name) values(?,?,?)",
                account,
                "$account@example.test",
                "Fictional employee",
            )
            for (company in companies) {
                owner.update(
                    "insert into companies(id,code,name,timezone) values(?,?,?,'UTC')",
                    company,
                    compact(company).take(20),
                    "Fictional company",
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
            }
            owner.update(
                "insert into collaboration_space_identities(space_id,account_id,matrix_server_name,matrix_user_id,origin,created_at) values(?,?,'matrix.example.test',?,'SPACE',?::timestamptz)",
                space,
                account,
                user,
                at.minusSeconds(1).toString(),
            )
        }

        fun parent(): UUID =
            UUID.randomUUID().also {
                owner.update(
                    "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,credential_version,access_expires_at,created_at,rotated_at) values(?,?,?,?::timestamptz,?::timestamptz,0,?::timestamptz,?::timestamptz,?::timestamptz)",
                    it,
                    account,
                    compact(it).repeat(2),
                    actor.authenticatedAt.toString(),
                    at.plusSeconds(3600).toString(),
                    at.plusSeconds(900).toString(),
                    at.minusSeconds(60).toString(),
                    at.minusSeconds(30).toString(),
                )
            }

        fun legacy(parent: UUID, company: UUID) {
            val id = UUID.randomUUID()
            owner.update(
                "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional','PROVISIONING',0,?::timestamptz,?::timestamptz)",
                company,
                id,
                account,
                at.toString(),
                at.toString(),
            )
            owner.update(
                "insert into collaboration_device_sessions(company_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?::timestamptz)",
                company,
                id,
                account,
                parent,
                at.toString(),
            )
        }

        fun device(parent: UUID): CollaborationSpaceDevice {
            val id = UUID.randomUUID()
            return CollaborationSpaceDevice(
                id,
                space,
                account,
                user,
                "HRISG_${compact(id)}",
                CollaborationSpaceDeviceOrigin.SPACE,
                null,
                null,
                parent,
                CollaborationPlatform.ANDROID,
                "Fictional",
                CollaborationDeviceState.PROVISIONING,
                0,
                at,
                at,
                null,
            )
        }
    }

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
