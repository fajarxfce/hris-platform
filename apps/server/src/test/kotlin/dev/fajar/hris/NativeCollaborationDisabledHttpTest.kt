package dev.fajar.hris

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NativeCollaborationDisabledHttpTest : NativeCollaborationApiFixture() {
    @Test
    fun anUnconfiguredDeploymentCannotQueueProvisioning() {
        val n = native()
        failure(register(n), 503, "collaboration_unavailable")
        val directory =
            n.fixture.browser.send(
                java.net.http.HttpRequest.newBuilder(
                        java.net.URI("http://127.0.0.1:$port/api/v1/collaboration/native-devices")
                    )
                    .timeout(java.time.Duration.ofSeconds(15))
                    .header("Authorization", "Bearer ${n.access}")
                    .GET()
                    .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString(),
            )
        failure(directory, 503, "collaboration_unavailable")
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
