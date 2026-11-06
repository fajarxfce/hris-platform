package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/** Stops the first command after the real document lock, independently of HTTP admission. */
class DocumentLockProbe {
    class Barrier(val company: UUID) {
        val entered = CountDownLatch(1)
        val competing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
    }

    val current = AtomicReference<Barrier?>()
}

@TestConfiguration(proxyBeanMethods = false)
class DocumentLockProbeConfiguration {
    @Bean fun documentLockProbe() = DocumentLockProbe()

    @Bean
    @Primary
    fun probedDocumentLocks(
        @Qualifier("documents") delegate: DocumentRepository,
        probe: DocumentLockProbe,
    ): DocumentRepository =
        object : DocumentRepository by delegate {
            override fun lock(companyId: UUID, shared: Boolean): Result<Unit> {
                val barrier = probe.current.get()?.takeIf { it.company == companyId && !shared }
                val occurrence = barrier?.calls?.incrementAndGet()
                if (barrier != null && occurrence == 2) barrier.competing.countDown()
                val result = delegate.lock(companyId, shared)
                if (barrier != null && occurrence == 1 && result is Result.Success) {
                    barrier.entered.countDown()
                    check(barrier.release.await(15, TimeUnit.SECONDS))
                }
                return result
            }
        }
}
