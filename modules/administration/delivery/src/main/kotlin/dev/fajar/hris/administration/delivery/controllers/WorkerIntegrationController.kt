package dev.fajar.hris.administration.delivery.controllers

import dev.fajar.hris.administration.delivery.responses.toResponse
import dev.fajar.hris.administration.domain.usecases.GetWorkerIntegrationRegistry
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/administration/integrations/workers")
class WorkerIntegrationController(private val review: GetWorkerIntegrationRegistry) {
    @GetMapping fun get(actor: Actor) = review.execute(actor).response().toResponse()
}
