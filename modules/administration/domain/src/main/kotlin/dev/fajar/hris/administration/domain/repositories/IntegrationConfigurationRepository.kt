package dev.fajar.hris.administration.domain.repositories

import dev.fajar.hris.administration.domain.entities.IntegrationConfiguration
import dev.fajar.hris.core.domain.Result

fun interface IntegrationConfigurationRepository {
    /** Exactly one entry for each supported integration, containing no provider identifiers. */
    fun read(): Result<List<IntegrationConfiguration>>
}
