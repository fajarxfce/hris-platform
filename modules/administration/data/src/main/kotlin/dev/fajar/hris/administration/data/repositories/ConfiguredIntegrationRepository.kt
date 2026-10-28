package dev.fajar.hris.administration.data.repositories

import dev.fajar.hris.administration.domain.entities.IntegrationConfiguration
import dev.fajar.hris.administration.domain.entities.IntegrationKind
import dev.fajar.hris.administration.domain.repositories.IntegrationConfigurationRepository
import dev.fajar.hris.core.domain.Result
import java.util.Collections

/** Binds only startup flags; reads neither credentials nor external services. */
class ConfiguredIntegrationRepository(enabled: Set<IntegrationKind>) :
    IntegrationConfigurationRepository {
    private val snapshot =
        Collections.unmodifiableList(
            IntegrationKind.entries.map { IntegrationConfiguration(it, it in enabled) }
        )

    override fun read() = Result.Success(snapshot)
}
