package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class CompanyLockObservation {
    data class Attempt(val company: UUID, val entered: CountDownLatch = CountDownLatch(1))

    val current = AtomicReference<Attempt?>()
}

@TestConfiguration(proxyBeanMethods = false)
class CompanyLockObservationConfiguration {
    @Bean fun companyLockObservation() = CompanyLockObservation()

    @Bean
    @Primary
    fun observedCompanies(
        @Qualifier("companies") delegate: CompanyRepository,
        observation: CompanyLockObservation,
    ): CompanyRepository =
        object : CompanyRepository by delegate {
            override fun lock(id: UUID, shared: Boolean): Result<Unit> {
                observation.current
                    .get()
                    ?.takeIf { it.company == id && shared }
                    ?.entered
                    ?.countDown()
                return delegate.lock(id, shared)
            }
        }
}
