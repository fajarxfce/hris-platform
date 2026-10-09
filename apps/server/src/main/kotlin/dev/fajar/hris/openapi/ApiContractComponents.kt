package dev.fajar.hris.openapi

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.media.*
import io.swagger.v3.oas.models.security.SecurityScheme

fun apiContractComponents(): Components =
    Components()
        .addSecuritySchemes(
            "cookieSession",
            SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .`in`(SecurityScheme.In.COOKIE)
                .name("SESSION")
                .description(
                    "HttpOnly browser session. Obtain the cookie and CSRF token from /api/v1/auth/csrf before sign-in; authenticate before protected endpoints."
                ),
        )
        .addSecuritySchemes(
            "csrfToken",
            SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .`in`(SecurityScheme.In.HEADER)
                .name("X-CSRF-TOKEN")
                .description(
                    "Required on browser mutations, including sign-in. Fetch a fresh token after session authentication/elevation. Native bearer requests do not use CSRF."
                ),
        )
        .addSecuritySchemes(
            "nativeBearer",
            SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .description(
                    "Opaque, short-lived native access token; not a JWT. Refresh rotation is serialized and uses its own idempotent command."
                ),
        )
        .addSchemas(
            "ApiProblem",
            ObjectSchema()
                .description(
                    "RFC 9457 JSON failure. Translate code and field codes locally. Use safe parameters for interpolation; never show title or technical text as application copy. Unknown codes require a localized fallback."
                )
                .addProperty(
                    "type",
                    StringSchema().format("uri").example("urn:hris:problem:stale_version"),
                )
                .addProperty(
                    "title",
                    StringSchema()
                        .description("HTTP status description, not localized product copy."),
                )
                .addProperty(
                    "status",
                    IntegerSchema()
                        .minimum(java.math.BigDecimal(400))
                        .maximum(java.math.BigDecimal(599)),
                )
                .addProperty(
                    "code",
                    StringSchema().pattern("^[a-z][a-z0-9_]*$").example("stale_version"),
                )
                .addProperty("fields", MapSchema().additionalProperties(StringSchema()))
                .addProperty("parameters", MapSchema().additionalProperties(StringSchema()))
                .addProperty("correlationId", StringSchema().format("uuid"))
                .addProperty("instance", StringSchema().format("uri-reference"))
                .addProperty(
                    "retryAfterSeconds",
                    IntegerSchema()
                        .format("int64")
                        .minimum(java.math.BigDecimal.ZERO)
                        .description(
                            "Present only when a retry delay is known; matches Retry-After."
                        ),
                )
                .required(
                    listOf(
                        "type",
                        "title",
                        "status",
                        "code",
                        "fields",
                        "parameters",
                        "correlationId",
                    )
                ),
        )
        .addSchemas(
            "PasswordSignInRequest",
            ObjectSchema()
                .addProperty("email", StringSchema().format("email"))
                .addProperty("password", StringSchema().format("password").writeOnly(true))
                .required(listOf("email", "password")),
        )
        .addSchemas(
            "PasswordSignInResponse",
            ObjectSchema()
                .addProperty("mfaConfigured", BooleanSchema())
                .required(listOf("mfaConfigured")),
        )
