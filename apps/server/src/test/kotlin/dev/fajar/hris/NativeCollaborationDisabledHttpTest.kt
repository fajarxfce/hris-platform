package dev.fajar.hris

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NativeCollaborationDisabledHttpTest : NativeCollaborationApiFixture() {
    @Test
    fun anUnconfiguredDeploymentCannotQueueProvisioning() {
        val n = native()
        failure(register(n), 503, "collaboration_unavailable")
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_devices where company_id=?",
                    Int::class.java,
                    n.fixture.company,
                ),
        )
    }
}
