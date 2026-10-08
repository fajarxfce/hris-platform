package dev.fajar.hris.identity.data.datasources

import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import org.springframework.security.crypto.password.PasswordEncoder

/** Bounds Argon2 working memory; callers never join an unbounded password queue. */
class ArgonPasswordDataSource(private val encoder: PasswordEncoder, capacity: Int = 8) :
    PasswordDataSource {
    private val permits = Semaphore(capacity.also { require(it in 1..64) })

    override fun hash(password: String): String? {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        if (!permits.tryAcquire()) throw RejectedExecutionException("Password capacity reached")
        try {
            return encoder.encode(password)
        } finally {
            permits.release()
        }
    }

    override fun matches(password: String, hash: String): Boolean {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        if (!permits.tryAcquire()) throw RejectedExecutionException("Password capacity reached")
        try {
            return encoder.matches(password, hash)
        } finally {
            permits.release()
        }
    }
}
