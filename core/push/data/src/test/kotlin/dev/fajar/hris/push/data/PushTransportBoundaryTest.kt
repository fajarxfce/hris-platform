package dev.fajar.hris.push.data

import dev.fajar.hris.push.data.di.readGooglePushCredentials
import dev.fajar.hris.push.data.transport.*
import java.net.http.HttpClient
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Flow
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.json.JsonMapper

class PushTransportBoundaryTest {
    @TempDir lateinit var directory: Path

    @Test
    fun streamingLimitCancelsImmediatelyAndCompletedSubscribersRejectLateData() {
        val cancelled = AtomicBoolean()
        val subscriber = BoundedPushBodySubscriber(4)
        subscriber.onSubscribe(
            object : Flow.Subscription {
                override fun request(n: Long) {}

                override fun cancel() {
                    cancelled.set(true)
                }
            }
        )
        subscriber.onNext(listOf(ByteBuffer.wrap(byteArrayOf(1, 2, 3))))
        subscriber.onNext(listOf(ByteBuffer.wrap(byteArrayOf(4, 5))))
        assertTrue(cancelled.get())
        assertTrue(subscriber.body.toCompletableFuture().isCompletedExceptionally)
        subscriber.onNext(listOf(ByteBuffer.allocate(100)))
        subscriber.onComplete()
        assertThrows(Exception::class.java) { subscriber.body.toCompletableFuture().get() }
    }

    @Test
    fun credentialConfigurationRejectsDifferentProjectsEndpointsAndUnboundedFiles() {
        val json = JsonMapper.builder().build()
        val file = directory.resolve("fixture.json")
        val client = HttpClient.newHttpClient()
        try {
            for (body in
                listOf(
                    """{"type":"service_account","project_id":"another-project","token_uri":"https://oauth2.googleapis.com/token"}""",
                    """{"type":"service_account","project_id":"test-project","token_uri":"http://169.254.169.254/metadata"}""",
                    """{"type":"authorized_user","project_id":"test-project","private_key":"private fixture value"}""",
                    "x".repeat(32769),
                )) {
                Files.writeString(file, body)
                val error =
                    assertThrows(IllegalArgumentException::class.java) {
                        readGooglePushCredentials(
                            file,
                            "test-project",
                            GooglePushAuthTransport(client),
                            json,
                        )
                    }
                assertEquals("Invalid FCM service-account configuration", error.message)
                assertNull(error.cause)
            }
        } finally {
            client.shutdownNow()
            assertTrue(client.awaitTermination(Duration.ofSeconds(5)))
        }
    }
}
