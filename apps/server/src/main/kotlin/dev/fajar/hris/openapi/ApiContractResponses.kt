package dev.fajar.hris.openapi

import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.media.*
import io.swagger.v3.oas.models.responses.ApiResponse

/** Reusable transport responses keep each operation small without dropping its error contract. */
fun apiProblemResponses(): Map<String, ApiResponse> =
    mapOf(
            "400" to "Malformed request or unsupported transport value.",
            "401" to "Authentication is missing, expired, or revoked.",
            "403" to "Current access or authentication assurance is insufficient.",
            "404" to "The resource is absent or outside the permitted scope.",
            "408" to "Reading the request body exceeded its finite time budget.",
            "409" to
                "Version, idempotency, sync scope, or business state conflicts. Resolve by code; do not blindly retry.",
            "413" to "The request body exceeds the bounded transport size.",
            "422" to "Domain validation failed. Translate code and field codes locally.",
            "429" to
                "Admission limit reached. Respect Retry-After when supplied and use a finite retry budget.",
            "500" to "Unexpected failure. Use the correlation ID for diagnosis.",
            "503" to "A required dependency or configured capability is unavailable.",
        )
        .mapValues { (status, description) ->
            ApiResponse()
                .description(description)
                .content(
                    Content()
                        .addMediaType(
                            "application/problem+json",
                            MediaType()
                                .schema(Schema<Any>().`$ref`("#/components/schemas/ApiProblem")),
                        )
                )
                .addHeaderObject("X-Request-ID", Header().schema(StringSchema().format("uuid")))
                .also { response ->
                    if (status in setOf("429", "503"))
                        response.addHeaderObject(
                            "Retry-After",
                            Header()
                                .schema(StringSchema().pattern("^[0-9]+$"))
                                .description(
                                    "Optional delay in seconds. This does not grant unlimited retries."
                                ),
                        )
                }
        }
