package dev.fajar.hris.administration.delivery.controllers

import dev.fajar.hris.administration.delivery.responses.IntegrationConfigurationEntryResponse
import dev.fajar.hris.administration.delivery.responses.IntegrationConfigurationResponse
import dev.fajar.hris.administration.domain.usecases.GetIntegrationConfiguration
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/administration/integrations/configuration")
class IntegrationConfigurationController(private val review: GetIntegrationConfiguration) {
    @GetMapping
    fun get(actor: Actor): IntegrationConfigurationResponse =
        review.execute(actor).response().let {
            IntegrationConfigurationResponse(
                "API",
                it.observedAt,
                it.integrations.map { entry ->
                    IntegrationConfigurationEntryResponse(entry.kind.name, entry.enabled)
                },
            )
        }
}
