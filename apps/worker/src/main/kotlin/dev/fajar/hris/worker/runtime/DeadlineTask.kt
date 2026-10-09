package dev.fajar.hris.worker.runtime

import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicReference

/** FutureTask binds cancellation to its own runner, never to a reused executor thread. */
class DeadlineTask(operation: () -> Unit) : FutureTask<Unit>(Callable { operation() }) {
    private val deadline = AtomicReference<ScheduledFuture<*>?>()

    fun watch(timer: ScheduledFuture<*>) {
        check(deadline.compareAndSet(null, timer))
        if (isDone) timer.cancel(false)
    }

    override fun done() {
        deadline.get()?.cancel(false)
    }
}
