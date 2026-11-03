package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceIdentityPolicyTest {
    private val space = UUID.randomUUID()
    private val account = UUID.randomUUID()
    private val company = UUID.randomUUID()
    private val device = UUID.randomUUID()
    private val now = Instant.parse("2026-11-03T20:00:00Z")
    private val server = "matrix.example.test"
    private val legacy =
        CollaborationLegacyDevice(
            CollaborationLegacyDevicePosition(company, device),
            account,
            "@${collaborationMatrixLocalpart(company, account)}:$server",
            collaborationMatrixDeviceId(device),
            CollaborationPlatform.ANDROID,
            CollaborationDeviceState.REVOKED,
            2,
            1,
            now.minusSeconds(300),
            now.minusSeconds(240),
            now.minusSeconds(120),
            now.minusSeconds(120),
        )

    private fun choose(
        selected: CollaborationSpaceIdentity? = null,
        preferred: CollaborationLegacyDevice? = null,
    ) =
        selectCollaborationSpaceIdentity(
            space,
            account,
            server,
            CollaborationSpaceIdentityInput(space, account, selected, preferred),
            now,
        )

    private fun <T> success(result: Result<T>): T = (result as Result.Success).value

    private fun failed(result: Result<*>, code: String? = null) {
        assertInstanceOf(Result.Failed::class.java, result)
        if (code != null) assertEquals(code, (result as Result.Failed).failure.code)
    }

    @Test
    fun admittedAbsenceUsesTheStableSpaceAccountNameWithoutPretendingItIsACompany() {
        val selected = success(choose())
        assertEquals(
            "@${collaborationSpaceMatrixLocalpart(space, account)}:$server",
            selected.matrixUserId,
        )
        assertEquals(CollaborationSpaceIdentityOrigin.SPACE, selected.origin)
        assertNull(selected.legacy)
        assertEquals(selected, success(choose()))
    }

    @Test
    fun retiredLegacyUserIsPreservedWithoutResumingItsRetiredDevice() {
        val selected = success(choose(preferred = legacy))
        assertEquals(legacy.matrixUserId, selected.matrixUserId)
        assertEquals(CollaborationSpaceIdentityOrigin.LEGACY, selected.origin)
        assertEquals(legacy.position, selected.legacy)
        assertEquals(now, selected.createdAt)
        assertEquals(CollaborationDeviceState.REVOKED, legacy.state)
    }

    @Test
    fun existingSelectionIsImmutableAndCannotBeRetargetedToAnotherServerOrAccount() {
        val selected = success(choose(preferred = legacy)).copy(createdAt = now.minusSeconds(10))
        assertEquals(selected, success(choose(selected)))
        failed(
            selectCollaborationSpaceIdentity(
                space,
                account,
                "other.example.test",
                CollaborationSpaceIdentityInput(space, account, selected, null),
                now,
            ),
            "collaboration_identity_migration_required",
        )
        for (invalid in
            listOf(
                selected.copy(accountId = UUID.randomUUID()),
                selected.copy(spaceId = UUID.randomUUID()),
                selected.copy(createdAt = now.plusSeconds(1)),
                selected.copy(version = 1),
                selected.copy(legacy = null),
                selected.copy(matrixUserId = "@foreign:$server"),
            )) failed(choose(invalid))
        failed(choose(selected, legacy))
    }

    @Test
    fun unavailableOrForeignSnapshotNeverMeansCreateANewIdentity() {
        failed(
            selectCollaborationSpaceIdentity(space, account, server, null, now),
            "collaboration_access_denied",
        )
        failed(
            selectCollaborationSpaceIdentity(
                space,
                account,
                server,
                CollaborationSpaceIdentityInput(space, UUID.randomUUID(), null, null),
                now,
            ),
            "collaboration_access_denied",
        )
        for (invalid in
            listOf(
                legacy.copy(accountId = UUID.randomUUID()),
                legacy.copy(matrixUserId = "@foreign:$server"),
                legacy.copy(matrixDeviceId = "other"),
                legacy.copy(identityObservedAt = now.plusSeconds(1)),
                legacy.copy(deviceCreatedAt = now),
            )) failed(choose(preferred = invalid))
    }

    @Test
    fun serverNamesAreBoundedAndCannotContainUserInfoOrAlternateHttpPaths() {
        for (name in
            listOf(
                "",
                "a".repeat(181),
                "matrix.example.test/path",
                "user@matrix.example.test",
                "matrix.example.test?key=x",
                "matrix.example.test:0",
                "matrix.example.test:65536",
            )) assertFalse(validCollaborationSpaceServer(name), name)
        for (name in listOf(server, "matrix.example.test:8448", "[::1]:8448")) assertTrue(
            validCollaborationSpaceServer(name),
            name,
        )
    }
}
