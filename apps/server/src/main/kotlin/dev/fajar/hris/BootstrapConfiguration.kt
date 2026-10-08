package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.domain.usecases.BootstrapAdministrator
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

@Configuration(proxyBeanMethods = false)
class BootstrapConfiguration {
    @Bean
    fun bootstrapAdministratorRunner(
        environment: Environment,
        bootstrap: BootstrapAdministrator,
    ): ApplicationRunner = ApplicationRunner {
        val email = environment.getProperty("HRIS_BOOTSTRAP_EMAIL")
        val password = environment.getProperty("HRIS_BOOTSTRAP_PASSWORD")
        if (email.isNullOrBlank() && password.isNullOrBlank()) return@ApplicationRunner
        require(!email.isNullOrBlank() && !password.isNullOrBlank()) {
            "Bootstrap requires both email and password"
        }
        when (val result = bootstrap.execute(email, password, "Administrator")) {
            is Result.Failed -> error("Administrator bootstrap failed: ${result.failure.code}")
            is Result.Success ->
                if (result.value)
                    LoggerFactory.getLogger(javaClass).info("Initial administrator created")
        }
    }
}
