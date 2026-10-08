package dev.fajar.hris.mail.data

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.mail.data.datasources.JakartaMailDataSource
import dev.fajar.hris.mail.data.di.SmtpSettings
import dev.fajar.hris.mail.data.di.createSmtpSender
import dev.fajar.hris.mail.data.repositories.SmtpMailRepository
import dev.fajar.hris.mail.domain.entities.OutboundMail
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.time.Duration
import java.util.Properties
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.mail.MailAuthenticationException

class SmtpMailTest {
    private class SmtpFixture(
        private val recipientCode: Int = 250,
        private val stall: Boolean = false,
        private val finalCode: Int = 250,
    ) : AutoCloseable {
        private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        private val release = CountDownLatch(1)
        private val executor =
            ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.SECONDS,
                SynchronousQueue(),
                Thread.ofPlatform().name("hris-smtp-fixture").factory(),
            )
        private var stopped = false
        private var connection: Socket? = null
        val message = CompletableFuture<String>()
        val delivered = java.util.concurrent.atomic.AtomicInteger()
        val connected = CountDownLatch(1)
        val port: Int = server.localPort
        private val task =
            executor.submit {
                try {
                    val socket = server.accept()
                    synchronized(this) { if (stopped) socket.close() else connection = socket }
                    socket.use {
                        if (socket.isClosed) return@submit
                        socket.soTimeout = 3000
                        connected.countDown()
                        if (stall) {
                            check(release.await(5, TimeUnit.SECONDS))
                            return@submit
                        }
                        val output = socket.getOutputStream()
                        fun reply(value: String) {
                            output.write((value + "\r\n").toByteArray(Charsets.US_ASCII))
                            output.flush()
                        }
                        val input = socket.getInputStream()
                        fun line(): String? {
                            val bytes = java.io.ByteArrayOutputStream()
                            repeat(4096) {
                                val byte = input.read()
                                if (byte < 0)
                                    return if (bytes.size() == 0) null
                                    else throw java.io.EOFException()
                                if (byte == 10)
                                    return bytes.toString(Charsets.US_ASCII).removeSuffix("\r")
                                bytes.write(byte)
                            }
                            throw java.io.IOException("Fixture line limit")
                        }
                        reply("220 fixture ESMTP")
                        var readingData = false
                        val data = StringBuilder()
                        repeat(2048) {
                            val command = line() ?: return@submit
                            if (readingData) {
                                if (command == ".") {
                                    readingData = false
                                    if (finalCode == 250) delivered.incrementAndGet()
                                    message.complete(data.toString())
                                    reply("$finalCode message response")
                                } else {
                                    require(data.length + command.length < 131072)
                                    data.append(command).append("\r\n")
                                }
                            } else
                                when {
                                    command.startsWith("EHLO") || command.startsWith("HELO") ->
                                        reply("250 fixture")
                                    command.startsWith("MAIL FROM") -> reply("250 sender accepted")
                                    command.startsWith("RCPT TO") ->
                                        reply("$recipientCode recipient response")
                                    command == "DATA" -> {
                                        readingData = true
                                        reply("354 continue")
                                    }
                                    command == "RSET" -> reply("250 reset")
                                    command == "QUIT" -> {
                                        reply("221 bye")
                                        return@submit
                                    }
                                    else -> reply("500 unsupported")
                                }
                        }
                        throw java.io.IOException("Fixture command limit")
                    }
                } catch (error: Exception) {
                    synchronized(this) { if (!stopped) message.completeExceptionally(error) }
                }
            }

        fun awaitConnectionClosed() {
            task.get(3, TimeUnit.SECONDS)
        }

        override fun close() {
            synchronized(this) {
                stopped = true
                server.close()
                connection?.close()
            }
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            task.get(5, TimeUnit.SECONDS)
        }
    }

    private fun repository(
        fixture: SmtpFixture,
        transport: String = "PLAINTEXT",
        timeout: Duration = Duration.ofSeconds(1),
    ) =
        SmtpMailRepository(
            JakartaMailDataSource(
                createSmtpSender(
                    SmtpSettings(
                        "127.0.0.1",
                        fixture.port,
                        "no-reply@example.test",
                        "",
                        "",
                        transport,
                        true,
                        timeout,
                    )
                ),
                "no-reply@example.test",
            )
        )

    private fun message() =
        OutboundMail(
            UUID.randomUUID(),
            "recipient@example.test",
            "Account setup",
            "Complete the account setup using the provided link.",
        )

    @Test
    fun smtpPreservesStableMessageIdentityAndClosesTheConnection() {
        SmtpFixture().use { fixture ->
            val message = message()
            assertTrue(repository(fixture).send(message) is Result.Success)
            val mime =
                MimeMessage(
                    Session.getInstance(Properties()),
                    ByteArrayInputStream(
                        fixture.message.get(3, TimeUnit.SECONDS).toByteArray(Charsets.US_ASCII)
                    ),
                )
            assertEquals("<${message.id}@example.test>", mime.messageID)
            assertEquals(message.subject, mime.subject)
            assertTrue(mime.content.toString().contains(message.text))
            assertEquals(1, mime.allRecipients.size)
            fixture.awaitConnectionClosed()
        }
    }

    @Test
    fun permanentRecipientRefusalAndTransientProviderFailuresStayDistinct() {
        SmtpFixture(550).use { fixture ->
            val result = repository(fixture).send(message()) as Result.Failed
            assertEquals(FailureKind.VALIDATION, result.failure.kind)
            assertEquals("mail_recipient_rejected", result.failure.code)
            assertEquals(0, fixture.delivered.get())
        }
        SmtpFixture(450).use { fixture ->
            val result = repository(fixture).send(message()) as Result.Failed
            assertEquals(FailureKind.UNAVAILABLE, result.failure.kind)
        }
        assertEquals(
            FailureKind.UNEXPECTED,
            mailFailure(MailAuthenticationException("fixture secret")).kind,
        )
    }

    @Test
    fun stalledProviderTimesOutAndTlsCannotSilentlyFallBackToPlaintext() {
        SmtpFixture(stall = true).use { fixture ->
            val started = System.nanoTime()
            val result = repository(fixture, timeout = Duration.ofMillis(200)).send(message())
            assertTrue(System.nanoTime() - started < Duration.ofSeconds(3).toNanos())
            assertTrue(fixture.connected.await(3, TimeUnit.SECONDS))
            assertEquals(FailureKind.UNAVAILABLE, (result as Result.Failed).failure.kind)
        }
        SmtpFixture().use { fixture ->
            val result = repository(fixture, transport = "STARTTLS").send(message())
            assertTrue(result is Result.Failed)
            assertEquals(0, fixture.delivered.get())
        }
    }

    @Test
    fun permanentMessageRefusalDoesNotBecomeATransientDeliveryFailure() {
        SmtpFixture(finalCode = 554).use { fixture ->
            val result = repository(fixture).send(message()) as Result.Failed
            assertEquals(FailureKind.UNEXPECTED, result.failure.kind)
            assertEquals("mail_delivery_rejected", result.failure.code)
            assertEquals(0, fixture.delivered.get())
        }
    }

    @Test
    fun envelopeAndConfigurationBoundsRejectUnsafeOrOversizedInput() {
        SmtpFixture().use { fixture ->
            val repository = repository(fixture)
            val newline =
                repository.send(message().copy(subject = "Account\r\nBcc: attacker@example.test"))
                    as Result.Failed
            assertEquals(FailureKind.VALIDATION, newline.failure.kind)
            val oversized =
                repository.send(message().copy(text = "\uD83D\uDE00".repeat(16385)))
                    as Result.Failed
            assertEquals(FailureKind.VALIDATION, oversized.failure.kind)
            val group =
                repository.send(message().copy(recipient = "group:a@example.test,b@example.test;"))
                    as Result.Failed
            assertEquals(FailureKind.VALIDATION, group.failure.kind)
            assertEquals(1, fixture.connected.count)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SmtpSettings(
                "smtp.example.test",
                25,
                "no-reply@example.test",
                "",
                "",
                "PLAINTEXT",
                true,
            )
        }
        assertFalse(
            SmtpSettings(
                    "smtp.example.test",
                    587,
                    "no-reply@example.test",
                    "fixture-user",
                    "fixture-secret",
                )
                .toString()
                .contains("fixture")
        )
        assertFalse(message().toString().contains("recipient"))
    }

    @Test
    fun safeBoundaryPreservesCancellationAndRejectsLateInterruption() {
        assertThrows(InterruptedException::class.java) {
            safeMailCall<Unit> {
                throw org.springframework.mail.MailSendException(
                    mapOf("fixture" to InterruptedException())
                )
            }
        }
        assertThrows(CancellationException::class.java) {
            safeMailCall<Unit> { throw CancellationException() }
        }
        try {
            assertThrows(InterruptedException::class.java) {
                safeMailCall {
                    Thread.currentThread().interrupt()
                    Unit
                }
            }
        } finally {
            Thread.interrupted()
        }
    }
}
