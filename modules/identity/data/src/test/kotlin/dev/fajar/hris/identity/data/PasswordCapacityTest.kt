package dev.fajar.hris.identity.data

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.safePasswordCall
import dev.fajar.hris.identity.data.datasources.ArgonPasswordDataSource
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder

class PasswordCapacityTest {
    @Test
    fun saturatedVerificationHasNoQueueAndCancellationReleasesItsPermit() {
        val entered = CountDownLatch(1)
        val wait = CountDownLatch(1)
        val first = AtomicBoolean(true)
        val encoder =
            object : PasswordEncoder {
                override fun encode(rawPassword: CharSequence?): String = "encoded"

                override fun matches(
                    rawPassword: CharSequence?,
                    encodedPassword: String?,
                ): Boolean {
                    if (first.getAndSet(false)) {
                        entered.countDown()
                        check(wait.await(5, TimeUnit.SECONDS))
                    }
                    return true
                }
            }
        val source = ArgonPasswordDataSource(encoder, capacity = 1)
        Executors.newSingleThreadExecutor().use { executor ->
            val running = executor.submit<Boolean> { source.matches("test", "encoded") }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(
                Result.Failed(Failure(FailureKind.UNAVAILABLE, "password_verification_busy")),
                safePasswordCall { source.matches("test", "encoded") },
            )
            assertTrue(running.cancel(true))
        }
        assertEquals(Result.Success(true), safePasswordCall { source.matches("test", "encoded") })
    }

    @Test
    fun encoderFailureAlsoReleasesCapacity() {
        val first = AtomicBoolean(true)
        val encoder =
            object : PasswordEncoder {
                override fun encode(rawPassword: CharSequence?): String {
                    if (first.getAndSet(false)) throw IllegalStateException("private password")
                    return "encoded"
                }

                override fun matches(
                    rawPassword: CharSequence?,
                    encodedPassword: String?,
                ): Boolean = true
            }
        val source = ArgonPasswordDataSource(encoder, capacity = 1)
        assertEquals(
            Result.Failed(Failure(FailureKind.UNEXPECTED, "password_operation_failed")),
            safePasswordCall { source.hash("test") },
        )
        assertEquals(Result.Success("encoded"), safePasswordCall { source.hash("test") })
    }
}
