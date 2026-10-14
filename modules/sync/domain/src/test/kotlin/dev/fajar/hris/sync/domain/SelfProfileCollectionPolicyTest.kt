package dev.fajar.hris.sync.domain

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.sync.domain.entities.SyncCollection
import dev.fajar.hris.sync.domain.policies.selectSyncCollections
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SelfProfileCollectionPolicyTest {
    @Test
    fun selfProfilesRequireBothKindsOfReadAuthorityUnlessTheCallerHasSelfRead() {
        val requested = setOf(SyncCollection.SELF_PROFILES)
        for (permissions in
            listOf(setOf("people.self.read"), setOf("people.read", "people.profile.read"))) {
            assertEquals(
                requested,
                (selectSyncCollections(permissions, requested) as Result.Success).value,
            )
        }
        for (permissions in
            listOf(
                setOf("people.read"),
                setOf("people.profile.read"),
                setOf("people.directory.read"),
            )) {
            assertEquals(
                "sync_access_denied",
                (selectSyncCollections(permissions, requested) as Result.Failed).failure.code,
            )
        }
    }

    @Test
    fun omittedSelectionsKeepTheEstablishedCollectionSet() {
        val before = selectSyncCollections(setOf("leave.self.manage"), null)
        val after = selectSyncCollections(setOf("leave.self.manage", "people.self.read"), null)
        assertEquals(before, after)
        assertFalse((after as Result.Success).value.contains(SyncCollection.SELF_PROFILES))
    }

    @Test
    fun aSelfProfileSelectionDoesNotAuthorizeTheCoworkerDirectoryOrDocuments() {
        val result =
            selectSyncCollections(
                setOf("people.self.read"),
                setOf(
                    SyncCollection.SELF_PROFILES,
                    SyncCollection.DIRECTORY_EMPLOYEES,
                    SyncCollection.DOCUMENT_EVIDENCE,
                ),
            )
        assertEquals(setOf(SyncCollection.SELF_PROFILES), (result as Result.Success).value)
    }
}
