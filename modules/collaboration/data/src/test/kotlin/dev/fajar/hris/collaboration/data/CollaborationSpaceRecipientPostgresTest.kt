package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceKeyPublication
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSpaceRecipientPostgresTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun actualTypedProjectionRetainsPendingIdentityAndOfflineReceivingUntilParentPurge() {
        val s = SpaceGatewayPostgresFixture(postgres).also { it.migrate() }
        Flyway.configure().dataSource(s.source).target("135").load().migrate()
        s.owner.execute(
            "grant select,insert,update on collaboration_space_key_publications to space_gateway_fixture"
        )
        s.owner.execute(
            "grant select on collaboration_space_recipient_devices to space_gateway_fixture"
        )
        val sql = DSL.using(TransactionAwareDataSourceProxy(s.runtimeSource), SQLDialect.POSTGRES)
        val keys =
            StoredCollaborationSpaceKeyRepository(PostgresCollaborationSpaceKeyDataSource(sql))
        val recipients =
            StoredCollaborationSpaceRecipientRepository(
                PostgresCollaborationSpaceRecipientDataSource(sql)
            )
        val original =
            CollaborationSpaceKeyPublication(
                s.space,
                s.account,
                s.device,
                s.parent,
                s.user,
                "HRISG_${s.device.toString().replace("-", "")}",
                "a".repeat(64),
                0,
                now(),
            )
        fun read() = success(s.tx { recipients.devices(s.space, setOf(s.account), now()) })
        assertTrue(read().isEmpty())
        success(s.tx { keys.reserve(original) })
        val pending = read().single()
        assertEquals(original.matrixUserId, pending.matrixUserId)
        assertEquals(original.matrixDeviceId, pending.matrixDeviceId)
        assertEquals(original.fingerprint, pending.fingerprint)
        assertFalse(pending.publicationConfirmed)
        success(s.tx { keys.publish(original, now()) })
        assertEquals(pending.copy(publicationConfirmed = true), read().single())
        s.owner.update(
            "update collaboration_space_provider_sessions set generation=generation+1,updated_at=statement_timestamp(),token_expires_at=statement_timestamp()+interval '1 second' where device_id=?",
            s.device,
        )
        TimeUnit.MILLISECONDS.sleep(1100)
        s.owner.update(
            "update native_sessions set access_expires_at=clock_timestamp()-interval '1 second' where id=?",
            s.parent,
        )
        assertEquals(pending.copy(publicationConfirmed = true), read().single())
        assertTrue(
            success(
                    s.tx(s.actor.copy(companyId = s.company)) {
                        recipients.devices(s.space, setOf(s.account), now())
                    }
                )
                .isEmpty()
        )
        s.owner.update("delete from native_sessions where id=?", s.parent)
        assertTrue(read().isEmpty())
        val retained = requireNotNull(success(s.tx { keys.find(s.space, s.account, s.device) }))
        assertEquals(original.copy(version = 1, publishedAt = retained.publishedAt), retained)
        assertNotNull(retained.publishedAt)
    }

    private fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS)

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
