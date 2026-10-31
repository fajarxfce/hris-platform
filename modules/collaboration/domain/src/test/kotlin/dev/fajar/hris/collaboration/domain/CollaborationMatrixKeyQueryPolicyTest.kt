package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixKeyQuery
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationMatrixKeyQueryPolicyTest {
    private val company = UUID.fromString("55555555-5555-4555-8555-555555555555")
    private val account = UUID.fromString("99999999-9999-4999-8999-999999999999")
    private val user = "@${collaborationMatrixLocalpart(company, account)}:proof.invalid"

    @Test
    fun queryDetachesCallerCollectionsAndPreservesExplicitAllDevices() {
        val devices = mutableListOf(collaborationMatrixDeviceId(UUID.randomUUID()))
        val input = mutableMapOf(user to devices)
        val captured =
            (snapshotCollaborationKeyQuery(CollaborationMatrixKeyQuery(input)) as Result.Success)
                .value
        devices.clear()
        input.clear()
        assertEquals(1, captured.devices.getValue(user).size)
        assertEquals(
            Result.Success(mapOf(user to account)),
            collaborationKeyQueryAccounts(captured, company, "proof.invalid"),
        )
        assertEquals(
            Result.Success(mapOf(user to account)),
            collaborationKeyQueryAccounts(
                CollaborationMatrixKeyQuery(mapOf(user to emptyList())),
                company,
                "proof.invalid",
            ),
        )
    }

    @Test
    fun foreignAndNoncanonicalNamesCannotReachAccountLookup() {
        for (value in
            listOf(
                user.uppercase(),
                user + ":other.invalid",
                user.replace("proof.invalid", "other.invalid"),
                user.replace("99999999", "9999999"),
                user.replace("55555555", "55555556"),
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                collaborationKeyQueryAccounts(
                    CollaborationMatrixKeyQuery(mapOf(value to emptyList())),
                    company,
                    "proof.invalid",
                ),
            )
        }
    }

    @Test
    fun queryBoundsDevicesAccountsAndDuplicateExplicitIdentifiers() {
        val device = collaborationMatrixDeviceId(UUID.randomUUID())
        for (values in
            listOf(
                listOf(device, device),
                List(6) { collaborationMatrixDeviceId(UUID.randomUUID()) },
                listOf("*"),
                listOf("HRIS_short"),
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                snapshotCollaborationKeyQuery(CollaborationMatrixKeyQuery(mapOf(user to values))),
            )
        }
        assertInstanceOf(
            Result.Failed::class.java,
            snapshotCollaborationKeyQuery(CollaborationMatrixKeyQuery(emptyMap())),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            snapshotCollaborationKeyQuery(
                CollaborationMatrixKeyQuery(
                    (1..101).associate { "@user$it:proof.invalid" to emptyList() }
                )
            ),
        )
    }
}
