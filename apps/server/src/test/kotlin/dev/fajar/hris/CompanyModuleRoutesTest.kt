package dev.fajar.hris

import dev.fajar.hris.administration.domain.entities.CompanyModule
import dev.fajar.hris.availability.requestedSyncModules
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.sync.domain.entities.SyncCollection
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class CompanyModuleRoutesTest {
    private val actor =
        Actor(
            UUID.randomUUID(),
            UUID.randomUUID(),
            setOf("documents.self.read"),
            Instant.now(),
            UUID.randomUUID(),
        )

    @Test
    fun theWholeRegisteredSelectionFitsTheBoundWithoutGrantingUnownedModules() {
        val request = MockHttpServletRequest()
        request.addParameter("collections", SyncCollection.entries.joinToString(",") { it.name })
        assertEquals(
            Result.Success(setOf(CompanyModule.DOCUMENTS)),
            requestedSyncModules(request, actor),
        )
    }

    @Test
    fun oversizedAndRepeatedSelectionsStillFailBeforeResolvingModules() {
        for (selection in
            listOf(
                "DOCUMENT_EVIDENCE".repeat(100),
                List(SyncCollection.entries.size + 1) { "DOCUMENT_EVIDENCE" }.joinToString(","),
            )) {
            val request = MockHttpServletRequest()
            request.addParameter("collections", selection)
            assertEquals(
                "invalid_sync_collections",
                (requestedSyncModules(request, actor) as Result.Failed).failure.code,
            )
        }
    }
}
