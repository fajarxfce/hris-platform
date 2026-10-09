package dev.fajar.hris

import dev.fajar.hris.administration.domain.entities.EffectiveClientPolicy
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/** Legacy payment fixtures run at V53, before client policy tables exist at V61. */
@TestConfiguration(proxyBeanMethods = false)
class PreClientPolicyMigrationConfiguration {
    @Bean
    @Primary
    fun preClientPolicy(
        @Qualifier("companyClientPolicies") delegate: CompanyClientPolicyRepository
    ): CompanyClientPolicyRepository =
        object : CompanyClientPolicyRepository by delegate {
            override fun effective(companyId: UUID, at: Instant) =
                Result.Success(EffectiveClientPolicy(null, null))
        }
}
