package dev.fajar.hris

import dev.fajar.hris.collaboration.data.repositories.DisabledCollaborationSpaceMatrixRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceMatrixRepository
import dev.fajar.hris.collaboration.domain.usecases.ClaimCollaborationSpaceProvisioning
import dev.fajar.hris.core.domain.Result
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class NativeCollaborationSpaceDisabledConfigurationTest : NativeCollaborationApiFixture() {
    @Autowired private lateinit var policy: CollaborationSpacePolicy
    @Autowired private lateinit var matrix: CollaborationSpaceMatrixRepository
    @Autowired private lateinit var claim: ClaimCollaborationSpaceProvisioning

    @Test
    fun defaultApiGraphKeepsGroupProviderAndClaimsDisabled() {
        assertFalse(policy.enabled)
        assertInstanceOf(DisabledCollaborationSpaceMatrixRepository::class.java, matrix)
        assertEquals(
            Result.Success(emptyList<CollaborationSpaceProvisioningLease>()),
            claim.execute(UUID.randomUUID(), 1),
        )
    }
}
