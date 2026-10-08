package dev.fajar.hris.storage.data.datasources

import dev.fajar.hris.storage.data.errors.ObjectStorageCapacityExceeded
import java.util.concurrent.Semaphore

fun <T> withObjectStorageCapacity(capacity: Semaphore, operation: () -> T): T {
    if (Thread.currentThread().isInterrupted) throw InterruptedException()
    if (!capacity.tryAcquire()) throw ObjectStorageCapacityExceeded()
    try {
        return operation()
    } finally {
        capacity.release()
    }
}
