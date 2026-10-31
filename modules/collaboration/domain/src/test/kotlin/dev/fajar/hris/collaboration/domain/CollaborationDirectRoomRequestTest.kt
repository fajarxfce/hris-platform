package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.policies.nativeCollaborationDirectRoomRequest
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.jobs.domain.entities.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationDirectRoomRequestTest {
    private fun request(): JobRequest {
        val values =
            mapOf(
                "conversationId" to UUID.randomUUID().toString(),
                "deviceId" to UUID.randomUUID().toString(),
                "nativeSessionId" to UUID.randomUUID().toString(),
                "platform" to "ANDROID",
                "clientBuild" to "5",
            )
        return JobRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            JobKind.COLLABORATION_DIRECT_PROVISION,
            UUID.randomUUID(),
            values,
            Instant.now(),
            0,
            UUID.randomUUID(),
            Instant.now(),
            1,
        )
    }

    @Test
    fun acceptsOnlyExplicitNativeRoomJobWithCanonicalIdentityAndBoundedClient() {
        val request = request()
        val parsed = nativeCollaborationDirectRoomRequest(request)
        assertInstanceOf(Result.Success::class.java, parsed)
        val value = (parsed as Result.Success).value
        assertEquals(request.values["conversationId"], value.conversationId.toString())
        assertEquals(request.values["nativeSessionId"], value.parentId.toString())
        assertEquals(5, value.client.build)
        for (values in
            listOf(
                request.values - "clientBuild",
                request.values + ("conversationId" to "1-1-1-1-1"),
                request.values + ("roomId" to "!untrusted"),
                request.values + ("platform" to "WEB"),
                request.values + ("clientBuild" to "05"),
                request.values + ("clientBuild" to "2147483648"),
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                nativeCollaborationDirectRoomRequest(request.copy(values = values)),
            )
        }
        assertInstanceOf(
            Result.Failed::class.java,
            nativeCollaborationDirectRoomRequest(
                request.copy(kind = JobKind.COLLABORATION_PROVISION)
            ),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            nativeCollaborationDirectRoomRequest(request.copy(totalItems = 2)),
        )
    }
}
