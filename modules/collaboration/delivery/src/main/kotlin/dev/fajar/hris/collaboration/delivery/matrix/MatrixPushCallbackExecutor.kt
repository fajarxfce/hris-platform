package dev.fajar.hris.collaboration.delivery.matrix

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.*

/** Fixed capacity without a waiting queue. Caller timeout cancels only its owned FutureTask. */
class MatrixPushCallbackExecutor(
    private val operation:
        (CollaborationPushHint, UUID, Instant) -> Result<CollaborationPushReceipt>,
    private val clock: Clock,
    private val timeout: Duration = Duration.ofSeconds(10),
) : AutoCloseable {
    private val lifecycle = Any()
    private var closed = false
    private val active = mutableSetOf<FutureTask<Result<CollaborationPushReceipt>>>()
    private val executor =
        ThreadPoolExecutor(
                4,
                4,
                30,
                TimeUnit.SECONDS,
                SynchronousQueue(),
                Thread.ofPlatform().name("hris-chat-callback-", 0).factory(),
                ThreadPoolExecutor.AbortPolicy(),
            )
            .apply { allowCoreThreadTimeOut(true) }

    init {
        require(timeout >= Duration.ofMillis(10) && timeout <= Duration.ofSeconds(10))
    }

    fun accept(hint: CollaborationPushHint, correlationId: UUID): Result<CollaborationPushReceipt> {
        val started = System.nanoTime()
        val deadline = clock.instant().plus(timeout)
        val task = FutureTask(Callable { operation(hint, correlationId, deadline) })
        synchronized(lifecycle) {
            if (closed) return unavailable()
            active.add(task)
            try {
                executor.execute(task)
            } catch (_: RejectedExecutionException) {
                active.remove(task)
                task.cancel(false)
                return unavailable()
            }
        }
        try {
            val remaining = timeout.toNanos() - (System.nanoTime() - started)
            if (remaining <= 0) return unavailable()
            val result = task.get(remaining, TimeUnit.NANOSECONDS)
            return if (System.nanoTime() - started < timeout.toNanos()) result else unavailable()
        } catch (_: TimeoutException) {
            return unavailable()
        } catch (_: CancellationException) {
            // Lifecycle cancellation of this owned future, rather than a domain cancellation.
            return unavailable()
        } catch (error: ExecutionException) {
            throw (error.cause ?: error)
        } finally {
            if (!task.isDone) task.cancel(true)
            synchronized(lifecycle) { active.remove(task) }
        }
    }

    override fun close() {
        val pending =
            synchronized(lifecycle) {
                if (closed) return
                closed = true
                active.toList()
            }
        pending.forEach { it.cancel(true) }
        executor.shutdownNow()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS))
                org.slf4j.LoggerFactory.getLogger(javaClass)
                    .warn("Chat callback shutdown reached its time limit")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun unavailable() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_push_unavailable"))
}
