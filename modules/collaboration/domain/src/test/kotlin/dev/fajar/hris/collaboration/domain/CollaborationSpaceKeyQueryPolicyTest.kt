package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceKeyQueryPolicyTest {
    private val space = UUID.randomUUID()
    private val company = UUID.randomUUID()
    private val account = UUID.randomUUID()
    private val device = UUID.randomUUID()
    private val server = "matrix.example.test"
    private val at = Instant.parse("2026-11-04T12:00:00Z")
    private val group = "@${collaborationSpaceMatrixLocalpart(space, account)}:$server"
    private val legacy = "@${collaborationMatrixLocalpart(company, account)}:$server"
    private val groupDevice = "HRISG_${compact(device)}"
    private val originalDevice = collaborationMatrixDeviceId(UUID.randomUUID())

    @Test
    fun queryAndClaimSnapshotsDetachAllCallerCollections() {
        val ids = mutableListOf(groupDevice, originalDevice)
        val users = mutableMapOf(group to ids)
        val query = success(snapshotCollaborationSpaceKeyQuery(CollaborationMatrixKeyQuery(users)))
        val selections = mutableMapOf(groupDevice to "signed_curve25519")
        val claims = mutableMapOf(group to selections)
        val claim = success(snapshotCollaborationSpaceKeyClaim(CollaborationMatrixKeyClaim(claims)))
        ids.clear()
        users.clear()
        selections.clear()
        claims.clear()
        assertEquals(listOf(groupDevice, originalDevice), query.devices.getValue(group))
        assertEquals(mapOf(groupDevice to "signed_curve25519"), claim.oneTimeKeys.getValue(group))
    }

    @Test
    fun currentGroupAndSeveralRetainedAliasesKeepTheirOriginalMapEntries() {
        val otherLegacy = "@${collaborationMatrixLocalpart(UUID.randomUUID(), account)}:$server"
        val query =
            CollaborationMatrixKeyQuery(
                listOf(group, legacy, otherLegacy).associateWith { emptyList<String>() }
            )
        assertEquals(
            query.devices.keys.associateWith { account },
            success(collaborationSpaceKeyQueryAccounts(query, space, server)),
        )
    }

    @Test
    fun foreignMalformedAndZeroNamesCannotReachAccountGuards() {
        for (name in
            listOf(
                group.uppercase(),
                group + ":other",
                group.replace(server, "foreign.test"),
                "@${collaborationSpaceMatrixLocalpart(UUID.randomUUID(), account)}:$server",
                "@hrisg_${compact(space)}_${"0".repeat(32)}:$server",
                "@hris_${"0".repeat(32)}_${compact(account)}:$server",
                legacy.replace("_", "-"),
                legacy + " ",
                "@a:$server",
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                collaborationSpaceKeyQueryAccounts(
                    CollaborationMatrixKeyQuery(mapOf(name to emptyList())),
                    space,
                    server,
                ),
                name,
            )
        }
        assertInstanceOf(
            Result.Failed::class.java,
            collaborationSpaceKeyQueryAccounts(
                CollaborationMatrixKeyQuery(mapOf(group to emptyList())),
                UUID(0, 0),
                server,
            ),
        )
    }

    @Test
    fun batchesEnforceOneHundredUsersAndFiveUniqueCanonicalDeviceIds() {
        val five = (1..5).map { "HRISG_${compact(UUID.randomUUID())}" }
        val hundred =
            (1..100).associate {
                "@hrisg_${compact(space)}_${compact(UUID.randomUUID())}:$server" to five
            }
        assertInstanceOf(
            Result.Success::class.java,
            snapshotCollaborationSpaceKeyQuery(CollaborationMatrixKeyQuery(hundred)),
        )
        assertInstanceOf(
            Result.Success::class.java,
            snapshotCollaborationSpaceKeyClaim(
                CollaborationMatrixKeyClaim(
                    hundred.mapValues { five.associateWith { "signed_curve25519" } }
                )
            ),
        )
        for (ids in
            listOf(
                five + originalDevice,
                listOf(groupDevice, groupDevice),
                listOf("*"),
                listOf(groupDevice.lowercase()),
                listOf("HRISG_short"),
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                snapshotCollaborationSpaceKeyQuery(CollaborationMatrixKeyQuery(mapOf(group to ids))),
            )
        }
        for (users in listOf(emptyMap(), hundred + ("@extra:$server" to five))) {
            assertInstanceOf(
                Result.Failed::class.java,
                snapshotCollaborationSpaceKeyQuery(CollaborationMatrixKeyQuery(users)),
            )
            assertInstanceOf(
                Result.Failed::class.java,
                snapshotCollaborationSpaceKeyClaim(
                    CollaborationMatrixKeyClaim(
                        users.mapValues { it.value.associateWith { "signed_curve25519" } }
                    )
                ),
            )
        }
        for (claim in
            listOf(
                emptyMap(),
                mapOf(groupDevice to "curve25519"),
                mapOf("*" to "signed_curve25519"),
            )) assertInstanceOf(
            Result.Failed::class.java,
            snapshotCollaborationSpaceKeyClaim(CollaborationMatrixKeyClaim(mapOf(group to claim))),
        )
    }

    @Test
    fun originalAliasAndMatrixDeviceStayDistinctFromAccountAndGroupDevice() {
        val row = candidate().copy(matrixUserId = legacy, matrixDeviceId = originalDevice)
        val query =
            CollaborationMatrixKeyQuery(
                mapOf(group to emptyList(), legacy to listOf(originalDevice))
            )
        val selected = success(select(query, listOf(row)))
        assertEquals(setOf(group, legacy), selected.accounts.keys)
        assertEquals(mapOf(legacy to listOf(originalDevice)), selected.providerRequest().devices)
        assertTrue(
            success(
                    select(
                        CollaborationMatrixKeyQuery(mapOf(legacy to listOf(groupDevice))),
                        listOf(row),
                    )
                )
                .devices
                .isEmpty()
        )
        assertTrue(
            success(select(CollaborationMatrixKeyQuery(mapOf(group to emptyList())), listOf(row)))
                .devices
                .isEmpty()
        )
    }

    @Test
    fun pendingPublicationsAppearForQueriesButNeverForClaims() {
        val row = candidate().copy(publicationConfirmed = false)
        val query = CollaborationMatrixKeyQuery(mapOf(group to emptyList()))
        assertEquals(listOf(row), success(select(query, listOf(row), true)).devices.values.toList())
        assertTrue(success(select(query, listOf(row), false)).devices.isEmpty())
        assertNotEquals(
            success(select(query, listOf(row))),
            success(select(query, listOf(row.copy(publicationConfirmed = true)))),
        )
    }

    @Test
    fun scopedReadbackRejectsForeignExpiredDuplicateAndOversizedDevices() {
        val query = CollaborationMatrixKeyQuery(mapOf(group to emptyList()))
        val row = candidate()
        for (rows in
            listOf(
                listOf(row.copy(spaceId = UUID.randomUUID())),
                listOf(row.copy(accountId = UUID.randomUUID())),
                listOf(row.copy(serverName = "foreign.test")),
                listOf(row.copy(receiveUntil = at)),
                listOf(row.copy(fingerprint = "bad")),
                listOf(row, row),
                List(6) {
                    val id = UUID.randomUUID()
                    row.copy(deviceId = id, matrixDeviceId = "HRISG_${compact(id)}")
                },
            )) {
            assertEquals(
                "collaboration_recipients_changed",
                (select(query, rows) as Result.Failed).failure.code,
            )
        }
    }

    @Test
    fun maximumDeviceBatchIsKeptWholeAndOverflowIsRejected() {
        val rows =
            (1..100).flatMap {
                val owner = UUID.randomUUID()
                (1..5).map {
                    val id = UUID.randomUUID()
                    candidate()
                        .copy(
                            accountId = owner,
                            deviceId = id,
                            matrixUserId =
                                "@${collaborationSpaceMatrixLocalpart(space,owner)}:$server",
                            matrixDeviceId = "HRISG_${compact(id)}",
                        )
                }
            }
        val query =
            CollaborationMatrixKeyQuery(
                rows.map { it.matrixUserId }.distinct().associateWith { emptyList<String>() }
            )
        assertEquals(500, success(select(query, rows)).devices.size)
        assertInstanceOf(Result.Failed::class.java, select(query, rows + rows.first()))
    }

    private fun candidate() =
        CollaborationSpaceRecipientDevice(
            space,
            account,
            device,
            server,
            group,
            groupDevice,
            "a".repeat(64),
            at.plusSeconds(600),
            true,
        )

    private fun select(
        query: CollaborationMatrixKeyQuery,
        rows: List<CollaborationSpaceRecipientDevice>,
        reserved: Boolean = true,
    ) = selectCollaborationSpaceKeys(space, server, query, rows, at, reserved)

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
