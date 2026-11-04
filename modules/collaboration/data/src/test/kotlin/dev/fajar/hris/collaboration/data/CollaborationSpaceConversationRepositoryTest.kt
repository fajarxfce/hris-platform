package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationSpaceConversationRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import java.util.concurrent.*
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
class CollaborationSpaceConversationRepositoryTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun crossCompanyMembersAndUnsignedConversationPagesHaveAnExplicitEnd() {
        val f = Fixture()
        val peers = (0..2).map { f.peer() }
        val ids =
            listOf(
                    "00000000-0000-0000-0000-000000000001",
                    "80000000-0000-0000-0000-000000000001",
                    "ffffffff-ffff-ffff-ffff-ffffffffffff",
                )
                .map(UUID::fromString)
        val expected = peers.zip(ids).map { (peer, id) -> f.value(peer, id) }
        expected.reversed().forEach { success(f.tx { f.repo.create(it) }) }
        assertEquals(
            f.s.user,
            success(f.tx { f.repo.member(f.s.space, f.s.account) })!!.identity!!.userId,
        )
        assertNull(success(f.tx { f.repo.member(f.s.space, peers[0]) })!!.identity)
        assertNull(success(f.tx { f.repo.member(f.s.space, UUID.randomUUID()) }))
        var cursor: UUID? = null
        for (i in expected.indices) {
            val page = success(f.tx { f.repo.list(f.s.space, f.s.account, cursor, 1) })
            assertEquals(listOf(expected[i]), page.items)
            assertEquals(if (i == 2) null else ids[i].toString(), page.nextCursor)
            cursor = page.items.single().id
        }
        assertEquals(
            Page<CollaborationSpaceDirectConversation>(emptyList(), null),
            success(f.tx { f.repo.list(f.s.space, f.s.account, cursor, 1) }),
        )
        assertEquals(
            expected,
            success(f.tx { f.repo.list(f.s.space, f.s.account, null, 50) }).items,
        )
        assertEquals(expected[0], success(f.tx { f.repo.find(f.s.space, f.s.account, ids[0]) }))
        assertNull(success(f.tx { f.repo.find(f.s.space, peers[1], ids[0]) }))
        assertEquals(
            expected[0],
            success(
                f.s.tx(f.s.actor.copy(accountId = peers[0])) {
                    f.repo.find(f.s.space, peers[0], ids[0])
                }
            ),
        )
        f.s.owner.update("delete from native_sessions where id=?", f.s.parent)
        f.s.owner.update(
            "update company_memberships set active=false,version=version+1 where account_id=?",
            f.s.account,
        )
        assertEquals(
            expected,
            success(f.tx { f.repo.list(f.s.space, f.s.account, null, 50) }).items,
        )
        assertNull(success(f.tx { f.repo.member(f.s.space, peers[0]) }))
    }

    @Test
    fun concurrentOppositeOpenersUnderThePairGuardObserveTheSameImmutableIntent() {
        val f = Fixture()
        val peer = f.peer()
        val value = f.value(peer)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)
            val results =
                listOf(f.s.account, peer).map { creator ->
                    pool.submit<CollaborationSpaceDirectConversation> {
                        ready.countDown()
                        assertTrue(start.await(10, TimeUnit.SECONDS))
                        success(
                            f.s.tx(f.s.actor.copy(accountId = creator)) {
                                f.repo
                                    .lockPair(
                                        value.spaceId,
                                        value.firstAccountId,
                                        value.secondAccountId,
                                    )
                                    .flatMap {
                                        f.repo
                                            .findPair(
                                                value.spaceId,
                                                value.firstAccountId,
                                                value.secondAccountId,
                                            )
                                            .flatMap { found ->
                                                if (found != null) Result.Success(found)
                                                else {
                                                    val candidate =
                                                        value.copy(
                                                            id = UUID.randomUUID(),
                                                            createdBy = creator,
                                                        )
                                                    f.repo.create(candidate).flatMap {
                                                        Result.Success(candidate)
                                                    }
                                                }
                                            }
                                    }
                            }
                        )
                    }
                }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            val actual = results.map { it.get(20, TimeUnit.SECONDS) }
            assertEquals(actual[0], actual[1])
            assertEquals(
                listOf(actual[0]),
                success(f.tx { f.repo.list(f.s.space, f.s.account, null, 50) }).items,
            )
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun failedWriteReadbackAndCancellationRollBackInsteadOfPublishingAFalseSuccess() {
        val f = Fixture()
        val value = f.value(f.peer())
        for (mode in
            listOf(
                "missing",
                "scope",
                "id",
                "pair",
                "creator",
                "time",
                "version",
                "throw",
                "cancel",
                "interrupt",
            )) {
            var written = false
            val raw =
                object : CollaborationSpaceConversationDataSource by f.raw {
                    override fun insert(record: CollaborationSpaceDirectConversationsRecord) {
                        f.raw.insert(record)
                        written = true
                    }

                    override fun find(
                        spaceId: UUID,
                        accountId: UUID,
                        id: UUID,
                    ): CollaborationSpaceDirectConversationsRecord? {
                        val row = f.raw.find(spaceId, accountId, id)
                        if (!written) return row
                        when (mode) {
                            "missing" -> return null
                            "throw" -> error("fixture readback")
                            "cancel" -> throw CancellationException("fixture readback")
                            "interrupt" -> throw InterruptedException("fixture readback")
                        }
                        return requireNotNull(row).also {
                            when (mode) {
                                "scope" -> it.spaceId = UUID.randomUUID()
                                "id" -> it.id = UUID.randomUUID()
                                "pair" -> it.firstAccountId = it.secondAccountId
                                "creator" -> it.createdBy = UUID.randomUUID()
                                "time" -> it.createdAt = requireNotNull(it.createdAt).plusNanos(1)
                                "version" -> it.version = 1
                            }
                        }
                    }
                }
            val repo = StoredCollaborationSpaceConversationRepository(raw)
            val action = { f.tx { repo.create(value) } }
            when (mode) {
                "cancel" -> assertThrows(CancellationException::class.java) { action() }
                "interrupt" -> assertThrows(InterruptedException::class.java) { action() }
                else -> assertInstanceOf(Result.Failed::class.java, action(), mode)
            }
            assertTrue(written)
            assertNull(success(f.tx { f.repo.find(f.s.space, f.s.account, value.id) }))
        }
        success(f.tx { f.repo.create(value) })
        assertEquals(
            value,
            success(
                f.tx { f.repo.findPair(f.s.space, value.firstAccountId, value.secondAccountId) }
            ),
        )
    }

    @Test
    fun originalCompanyInventoryPagesKeepDistinctCoordinatesAndAbsentRooms() {
        val f = Fixture()
        val companies = listOf(f.s.company, f.other).sortedBy(UUID::toString)
        val positions =
            listOf(
                CollaborationLegacyConversationPosition(
                    companies[0],
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                ),
                CollaborationLegacyConversationPosition(
                    companies[0],
                    UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                ),
                CollaborationLegacyConversationPosition(
                    companies[1],
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                ),
            )
        positions.reversed().forEach { f.legacy(f.peer(), it) }
        var after: CollaborationLegacyConversationPosition? = null
        for ((index, position) in positions.withIndex()) {
            val page = success(f.tx { f.repo.legacy(f.s.space, f.s.account, after, 1) })
            val item = page.items.single()
            assertEquals(position.companyId, item.conversation.companyId)
            assertEquals(position.conversationId, item.conversation.id)
            assertNull(item.room)
            assertEquals(if (index == 2) null else position, page.next)
            assertEquals(item, success(f.tx { f.repo.legacyOne(f.s.space, f.s.account, position) }))
            after = position
        }
        val end = success(f.tx { f.repo.legacy(f.s.space, f.s.account, after, 50) })
        assertTrue(end.items.isEmpty())
        assertNull(end.next)
        assertNull(
            success(
                f.tx {
                    f.repo.legacyOne(
                        f.s.space,
                        f.s.account,
                        CollaborationLegacyConversationPosition(
                            companies[1],
                            positions[1].conversationId,
                        ),
                    )
                }
            )
        )
        assertTrue(
            success(
                    f.s.tx(f.s.actor.copy(accountId = UUID.randomUUID())) {
                        f.repo.legacy(f.s.space, f.s.account, null, 50)
                    }
                )
                .items
                .isEmpty()
        )
    }

    @Test
    fun malformedRowsIncludingOverfetchNeverProducePartialPagesOrIdentityAuthority() {
        val f = Fixture()
        val first = f.value(f.peer(), UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val second = f.value(f.peer(), UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))
        success(f.tx { f.repo.create(first) })
        success(f.tx { f.repo.create(second) })
        val position = CollaborationLegacyConversationPosition(f.s.company, UUID.randomUUID())
        f.legacy(f.peer(), position)
        for (mode in
            listOf(
                "duplicate",
                "reverse",
                "oversize",
                "overfetchScope",
                "overfetchTime",
                "overfetchOwner",
            )) {
            val repo =
                StoredCollaborationSpaceConversationRepository(
                    object : CollaborationSpaceConversationDataSource by f.raw {
                        override fun list(
                            spaceId: UUID,
                            accountId: UUID,
                            after: UUID?,
                            limit: Int,
                        ): List<CollaborationSpaceDirectConversationsRecord> {
                            val rows = f.raw.list(spaceId, accountId, after, limit)
                            return when (mode) {
                                "duplicate" -> listOf(rows[0], rows[0])
                                "reverse" -> rows.reversed()
                                "oversize" -> rows + rows
                                else ->
                                    rows.also {
                                        when (mode) {
                                            "overfetchScope" -> it[1].spaceId = UUID.randomUUID()
                                            "overfetchTime" ->
                                                it[1].createdAt =
                                                    requireNotNull(it[1].createdAt).plusNanos(1)
                                            "overfetchOwner" ->
                                                it[1].firstAccountId = it[1].secondAccountId
                                        }
                                    }
                            }
                        }
                    }
                )
            assertInstanceOf(
                Result.Failed::class.java,
                f.tx { repo.list(f.s.space, f.s.account, null, 1) },
                mode,
            )
        }
        for (mode in
            listOf(
                "duplicate",
                "space",
                "account",
                "blank",
                "partial",
                "server",
                "peerUser",
                "time",
            )) {
            val repo =
                StoredCollaborationSpaceConversationRepository(
                    object : CollaborationSpaceConversationDataSource by f.raw {
                        override fun member(
                            spaceId: UUID,
                            accountId: UUID,
                        ): List<CollaborationSpaceMembersRecord> {
                            val rows = f.raw.member(spaceId, accountId)
                            if (mode == "duplicate") return rows + rows
                            return rows.also {
                                val row = it.single()
                                when (mode) {
                                    "space" -> row.spaceId = UUID.randomUUID()
                                    "account" -> row.accountId = UUID.randomUUID()
                                    "blank" -> row.displayName = " "
                                    "partial" -> row.identitySelectedAt = null
                                    "server" -> row.matrixServerName = "other.example.test"
                                    "peerUser" ->
                                        row.matrixUserId =
                                            "@hrisg_${compact(f.s.space)}_${compact(UUID.randomUUID())}:matrix.example.test"
                                    "time" ->
                                        row.identitySelectedAt =
                                            requireNotNull(row.identitySelectedAt).plusNanos(1)
                                }
                            }
                        }
                    }
                )
            assertInstanceOf(
                Result.Failed::class.java,
                f.tx { repo.member(f.s.space, f.s.account) },
                mode,
            )
        }
        for (mode in
            listOf(
                "space",
                "company",
                "owner",
                "version",
                "partialRoom",
                "badRoom",
                "earlyRoom",
                "duplicate",
                "oversize",
                "noProgress",
            )) {
            val repo =
                StoredCollaborationSpaceConversationRepository(
                    object : CollaborationSpaceConversationDataSource by f.raw {
                        override fun legacy(
                            spaceId: UUID,
                            accountId: UUID,
                            afterCompany: UUID?,
                            afterConversation: UUID?,
                            limit: Int,
                        ): List<CollaborationSpaceLegacyConversationsRecord> {
                            val rows = f.raw.legacy(spaceId, accountId, null, null, limit)
                            if (mode == "duplicate") return rows + rows
                            if (mode == "oversize") return rows + rows + rows
                            return rows.also {
                                val row = it.single()
                                when (mode) {
                                    "space" -> row.spaceId = UUID.randomUUID()
                                    "company" -> row.legacyCompanyId = UUID(0, 0)
                                    "owner" -> row.firstAccountId = row.secondAccountId
                                    "version" -> row.version = 1
                                    "partialRoom" -> row.matrixServerName = "matrix.example.test"
                                    "badRoom",
                                    "earlyRoom" -> {
                                        row.matrixServerName = "matrix.example.test"
                                        row.matrixRoomId =
                                            if (mode == "badRoom") "!has space" else "!original"
                                        row.roomBoundAt =
                                            if (mode == "earlyRoom")
                                                requireNotNull(row.createdAt).minusSeconds(1)
                                            else row.createdAt
                                    }
                                }
                            }
                        }
                    }
                )
            assertInstanceOf(
                Result.Failed::class.java,
                f.tx {
                    repo.legacy(
                        f.s.space,
                        f.s.account,
                        if (mode == "noProgress") position else null,
                        1,
                    )
                },
                mode,
            )
        }
    }

    @Test
    fun invalidRequestsAreRejectedBeforeIoAndTechnicalAcquisitionPreservesCancellation() {
        val f = Fixture()
        var calls = 0
        var mode = "throw"
        fun fail(): Nothing {
            calls++
            when (mode) {
                "cancel" -> throw CancellationException("fixture acquisition")
                "interrupt" -> throw InterruptedException("fixture acquisition")
                else -> error("fixture acquisition")
            }
        }
        val repo =
            StoredCollaborationSpaceConversationRepository(
                object : CollaborationSpaceConversationDataSource {
                    override fun member(
                        spaceId: UUID,
                        accountId: UUID,
                    ): List<CollaborationSpaceMembersRecord> = fail()

                    override fun lockPair(spaceId: UUID, first: UUID, second: UUID): Unit = fail()

                    override fun findPair(
                        spaceId: UUID,
                        first: UUID,
                        second: UUID,
                    ): CollaborationSpaceDirectConversationsRecord? = fail()

                    override fun find(
                        spaceId: UUID,
                        accountId: UUID,
                        id: UUID,
                    ): CollaborationSpaceDirectConversationsRecord? = fail()

                    override fun list(
                        spaceId: UUID,
                        accountId: UUID,
                        after: UUID?,
                        limit: Int,
                    ): List<CollaborationSpaceDirectConversationsRecord> = fail()

                    override fun insert(record: CollaborationSpaceDirectConversationsRecord): Unit =
                        fail()

                    override fun legacy(
                        spaceId: UUID,
                        accountId: UUID,
                        afterCompany: UUID?,
                        afterConversation: UUID?,
                        limit: Int,
                    ): List<CollaborationSpaceLegacyConversationsRecord> = fail()

                    override fun legacyOne(
                        spaceId: UUID,
                        accountId: UUID,
                        companyId: UUID,
                        conversationId: UUID,
                    ): CollaborationSpaceLegacyConversationsRecord? = fail()
                }
            )
        val value = f.value(f.peer())
        val zero = UUID(0, 0)
        val invalid: List<() -> Result<*>> =
            listOf(
                { repo.member(zero, f.s.account) },
                { repo.lockPair(value.spaceId, value.secondAccountId, value.firstAccountId) },
                { repo.findPair(value.spaceId, value.firstAccountId, value.firstAccountId) },
                { repo.find(value.spaceId, zero, value.id) },
                { repo.list(value.spaceId, f.s.account, null, 0) },
                { repo.list(value.spaceId, f.s.account, null, 51) },
                { repo.list(value.spaceId, f.s.account, zero, 1) },
                { repo.create(value.copy(version = 1)) },
                { repo.create(value.copy(createdAt = value.createdAt.plusNanos(1))) },
                { repo.create(value.copy(createdBy = UUID.randomUUID())) },
                {
                    repo.legacy(
                        value.spaceId,
                        f.s.account,
                        CollaborationLegacyConversationPosition(zero, value.id),
                        1,
                    )
                },
                { repo.legacy(value.spaceId, f.s.account, null, 51) },
                {
                    repo.legacyOne(
                        value.spaceId,
                        f.s.account,
                        CollaborationLegacyConversationPosition(f.s.company, zero),
                    )
                },
            )
        invalid.forEach { assertInstanceOf(Result.Failed::class.java, it()) }
        assertEquals(0, calls)
        val valid: List<() -> Result<*>> =
            listOf(
                { repo.member(value.spaceId, f.s.account) },
                { repo.lockPair(value.spaceId, value.firstAccountId, value.secondAccountId) },
                { repo.findPair(value.spaceId, value.firstAccountId, value.secondAccountId) },
                { repo.find(value.spaceId, f.s.account, value.id) },
                { repo.list(value.spaceId, f.s.account, null, 1) },
                { repo.create(value) },
                { repo.legacy(value.spaceId, f.s.account, null, 1) },
                {
                    repo.legacyOne(
                        value.spaceId,
                        f.s.account,
                        CollaborationLegacyConversationPosition(f.s.company, value.id),
                    )
                },
            )
        for (outcome in listOf("throw", "cancel", "interrupt")) {
            mode = outcome
            valid.forEach {
                when (outcome) {
                    "cancel" -> assertThrows(CancellationException::class.java) { it() }
                    "interrupt" -> assertThrows(InterruptedException::class.java) { it() }
                    else -> assertInstanceOf(Result.Failed::class.java, it())
                }
            }
        }
        assertEquals(valid.size * 3, calls)
    }

    private inner class Fixture {
        val s = SpaceGatewayPostgresFixture(postgres)
        val other = UUID.randomUUID()

        init {
            s.migrate()
            Flyway.configure().dataSource(s.source).target("129").load().migrate()
            s.owner.execute(
                "grant select,insert,update,delete on collaboration_space_direct_conversations to space_gateway_fixture"
            )
            s.owner.execute(
                "grant select on collaboration_space_members,collaboration_space_legacy_conversations to space_gateway_fixture"
            )
            s.owner.update(
                "insert into companies(id,code,name,timezone) values(?,?,'Fictional other company','UTC')",
                other,
                compact(other),
            )
        }

        val raw =
            PostgresCollaborationSpaceConversationDataSource(
                DSL.using(TransactionAwareDataSourceProxy(s.runtimeSource), SQLDialect.POSTGRES)
            )
        val repo = StoredCollaborationSpaceConversationRepository(raw)

        fun <T> tx(body: () -> Result<T>): Result<T> = s.tx(body = body)

        fun peer(): UUID =
            UUID.randomUUID().also {
                s.owner.update(
                    "insert into accounts(id,email,display_name) values(?,?,'Fictional peer')",
                    it,
                    "$it@example.test",
                )
                s.owner.update(
                    "insert into company_memberships(company_id,account_id) values(?,?)",
                    other,
                    it,
                )
                s.owner.update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                    other,
                    it,
                )
            }

        fun value(peer: UUID, id: UUID = UUID.randomUUID()): CollaborationSpaceDirectConversation {
            val pair = listOf(s.account, peer).sortedBy(UUID::toString)
            return CollaborationSpaceDirectConversation(
                id,
                s.space,
                pair[0],
                pair[1],
                s.account,
                s.at,
            )
        }

        fun legacy(peer: UUID, position: CollaborationLegacyConversationPosition) {
            val pair = listOf(s.account, peer).sortedBy(UUID::toString)
            s.owner.update(
                "insert into collaboration_direct_conversations(company_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,?)",
                position.companyId,
                position.conversationId,
                pair[0],
                pair[1],
                s.account,
                java.sql.Timestamp.from(s.at),
            )
        }
    }

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun <T> success(value: Result<T>): T = (value as Result.Success).value
}
