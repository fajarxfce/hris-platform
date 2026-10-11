package dev.fajar.hris.openapi

import io.swagger.v3.oas.models.*
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.media.*
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityRequirement
import java.math.BigDecimal

/** Completes transport behavior implemented by security filters and shared HTTP adapters. */
fun completeApiContract(api: OpenAPI) {
    // Springdoc removes unused definitions before invoking customizers. Add the
    // filter/problem schemas together with the operations that reference them.
    val shared = apiContractComponents()
    shared.schemas.forEach { (name, schema) -> api.components.addSchemas(name, schema) }
    shared.securitySchemes.forEach { (name, scheme) ->
        api.components.addSecuritySchemes(name, scheme)
    }
    api.path(
        "/api/v1/auth/login",
        PathItem()
            .post(
                Operation()
                    .tags(listOf("identity"))
                    .summary("Sign in with a password")
                    .requestBody(
                        RequestBody()
                            .required(true)
                            .content(
                                Content()
                                    .addMediaType(
                                        "application/json",
                                        MediaType()
                                            .schema(
                                                Schema<Any>()
                                                    .`$ref`(
                                                        "#/components/schemas/PasswordSignInRequest"
                                                    )
                                            ),
                                    )
                            )
                    )
                    .responses(
                        ApiResponses()
                            .addApiResponse(
                                "200",
                                ApiResponse()
                                    .description(
                                        "Browser session established; MFA may still be required."
                                    )
                                    .content(
                                        Content()
                                            .addMediaType(
                                                "application/json",
                                                MediaType()
                                                    .schema(
                                                        Schema<Any>()
                                                            .`$ref`(
                                                                "#/components/schemas/PasswordSignInResponse"
                                                            )
                                                    ),
                                            )
                                    ),
                            )
                    )
            ),
    )
    api.path(
        "/api/v1/auth/logout",
        PathItem()
            .post(
                Operation()
                    .tags(listOf("identity"))
                    .summary("End the browser session")
                    .responses(
                        ApiResponses()
                            .addApiResponse(
                                "204",
                                ApiResponse().description("Browser session invalidated."),
                            )
                    )
            ),
    )
    val publicPaths =
        setOf(
            "/api/v1/auth/login",
            "/api/v1/auth/logout",
            "/api/v1/auth/csrf",
            "/api/v1/auth/providers",
            "/api/v1/auth/password-recovery",
            "/api/v1/auth/password-recovery/confirm",
            "/api/v1/auth/invitations/accept",
            "/api/v1/auth/native/refresh",
            "/api/v1/auth/native/login",
        )
    val browserOnly = setOf("/api/v1/auth/login", "/api/v1/auth/logout", "/api/v1/auth/csrf")
    val errorDescriptions =
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
    for ((path, item) in api.paths) {
        for ((method, operation) in item.readOperationsMap()) {
            operation.operationId =
                method.name.lowercase() +
                    "_" +
                    path.removePrefix("/api/v1/").split('/').joinToString("_") {
                        if (it.startsWith('{')) "by_" + it.removeSurrounding("{", "}")
                        else it.replace('-', '_')
                    }
            val mutation =
                method !in
                    setOf(
                        PathItem.HttpMethod.GET,
                        PathItem.HttpMethod.HEAD,
                        PathItem.HttpMethod.OPTIONS,
                    )
            val transport =
                operation.extensions?.get("x-hris-session-transport")?.toString()
                    ?: "COOKIE_OR_NATIVE"
            val cookie = SecurityRequirement().addList("cookieSession")
            if (mutation) cookie.addList("csrfToken")
            operation.security =
                when {
                    path in setOf("/api/v1/auth/native/refresh", "/api/v1/auth/native/login") ->
                        emptyList()
                    path in publicPaths && !mutation -> emptyList()
                    path in browserOnly || transport == "COOKIE" -> listOf(cookie)
                    transport == "NATIVE" -> listOf(SecurityRequirement().addList("nativeBearer"))
                    else -> listOf(SecurityRequirement().addList("nativeBearer"), cookie)
                }
            operation.addExtension("x-hris-authentication-required", path !in publicPaths)
            val companyAdmission =
                ("{companyId}" in path &&
                    operation.extensions?.get("x-hris-company-admission") == true) ||
                    operation.extensions?.get("x-hris-group-admission") == true
            operation.addExtension("x-hris-company-admission", companyAdmission)
            if (companyAdmission) {
                operation.addParametersItem(
                    Parameter()
                        .name("X-HRIS-Client-Platform")
                        .`in`("header")
                        .required(false)
                        .schema(StringSchema().pattern("^(ANDROID|IOS|WEB)$"))
                        .description(
                            "Send with X-HRIS-Client-Build. Native sessions use ANDROID/IOS; browser sessions use WEB. Company client policy can require these headers."
                        )
                )
                operation.addParametersItem(
                    Parameter()
                        .name("X-HRIS-Client-Build")
                        .`in`("header")
                        .required(false)
                        .schema(
                            IntegerSchema().minimum(BigDecimal.ZERO).maximum(BigDecimal(999999999))
                        )
                        .description(
                            "Monotonic client build number, paired with X-HRIS-Client-Platform. Version gates never replace permission or payload validation."
                        )
                )
            }
            if (path in browserOnly) operation.addExtension("x-hris-session-transport", "COOKIE")
            if (
                "{companyId}" in path &&
                    operation.parameters.orEmpty().none {
                        it.name == "companyId" && it.`in` == "path"
                    }
            ) {
                operation.addParametersItem(
                    Parameter()
                        .name("companyId")
                        .`in`("path")
                        .required(true)
                        .schema(StringSchema().format("uuid"))
                        .description(
                            "Company scope; authorization is checked independently of client selection."
                        )
                )
            }
            operation.addParametersItem(
                Parameter()
                    .name("X-Request-ID")
                    .`in`("header")
                    .required(false)
                    .schema(StringSchema().format("uuid"))
                    .description(
                        "Optional correlation UUID; returned in the response header and JSON problems."
                    )
            )
            for (parameter in operation.parameters.orEmpty()) {
                when (parameter.name) {
                    "Idempotency-Key" -> {
                        parameter.description =
                            "Persist one UUID with the original account/company, route, payload, and observed version. Reuse only for the same intent after a lost response. Replay returns the original receipt after current authorization."
                        operation.addExtension("x-hris-idempotency-key", true)
                    }
                    "limit" -> {
                        parameter.schema?.minimum = BigDecimal.ONE
                        parameter.schema?.maximum = BigDecimal(200)
                    }
                    "companies" ->
                        if (path == "/api/v1/reports/headcount") {
                            parameter.schema =
                                ArraySchema()
                                    .items(StringSchema().format("uuid"))
                                    .minItems(1)
                                    .maxItems(32)
                                    .uniqueItems(true)
                            parameter.style = Parameter.StyleEnum.FORM
                            parameter.explode = false
                            parameter.description =
                                "One to 32 distinct company IDs. Every company requires current reports.read and people.read permission and passes client availability checks; the entire request fails when any selected scope is unavailable."
                        }
                    "collections" ->
                        if ("/sync/" in path) {
                            parameter.style = Parameter.StyleEnum.FORM
                            parameter.explode = false
                            parameter.description =
                                "Comma-separated supported collections. Persist this selection with the partition/cursor and send it on every page. Effective collections are intersected with current self-service permissions."
                        }
                    "ids" ->
                        if (path.endsWith("/communications/audience-references")) {
                            parameter.schema =
                                ArraySchema()
                                    .items(StringSchema().format("uuid"))
                                    .minItems(1)
                                    .maxItems(50)
                                    .uniqueItems(true)
                            parameter.style = Parameter.StyleEnum.FORM
                            parameter.explode = true
                            parameter.description =
                                "Resolve up to 50 selected references, including inactive definitions. Cannot be combined with query or after. Missing or foreign IDs are omitted. This lookup does not authorize saving or publication."
                        }
                    "query" ->
                        if (path.endsWith("/communications/audience-references")) {
                            parameter.schema?.maxLength = 120
                            parameter.description =
                                "Literal case-insensitive name or code search within the selected company. Employee results expose only an employment ID, employee number, name, and resource version."
                        }
                    "cursor" ->
                        if ("/sync/" in path) {
                            parameter.description =
                                "Opaque, encrypted, finite cursor bound to the account/company/permission/collection scope. A reset code requires a new bootstrap; never edit or decode the value."
                        }
                }
            }
            val responses = operation.responses ?: ApiResponses().also { operation.responses = it }
            for ((status, description) in errorDescriptions) {
                responses.putIfAbsent(
                    status,
                    ApiResponse()
                        .description(description)
                        .content(
                            Content()
                                .addMediaType(
                                    "application/problem+json",
                                    MediaType()
                                        .schema(
                                            Schema<Any>().`$ref`("#/components/schemas/ApiProblem")
                                        ),
                                )
                        ),
                )
            }
            describeBinaryTransport(path, method, operation)
            for ((status, response) in operation.responses) {
                response.addHeaderObject(
                    "X-Request-ID",
                    Header().schema(StringSchema().format("uuid")),
                )
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
    }
    for ((name, schema) in api.components.schemas) {
        if (name.endsWith("Request")) schema.additionalProperties = false
    }
}

/** Raw content is negotiated by the HTTP writer rather than returned as a controller DTO. */
fun describeBinaryTransport(path: String, method: PathItem.HttpMethod, operation: Operation) {
    if (
        method == PathItem.HttpMethod.POST &&
            path.endsWith("/documents/revisions/{revisionId}/chunks")
    ) {
        operation.requestBody =
            RequestBody()
                .required(true)
                .content(
                    Content()
                        .addMediaType(
                            "application/octet-stream",
                            MediaType().schema(BinarySchema()),
                        )
                )
    }
    if (method != PathItem.HttpMethod.GET) return
    val payslipPdf = path.endsWith("/payroll/payslips/{id}/pdf")
    if (path.endsWith("/payments/{id}/export")) {
        operation.responses["200"] =
            ApiResponse()
                .description("UTF-8 CSV with the currently authorized payment instructions.")
                .content(Content().addMediaType("text/csv", MediaType().schema(StringSchema())))
                .addHeaderObject("Content-Disposition", Header().schema(StringSchema()))
        return
    }
    if (!payslipPdf && !path.endsWith("/{revisionId}/content")) return
    for ((name, description) in
        mapOf(
            "Range" to "Single byte range; multiple/unsatisfiable ranges return 416.",
            "If-Range" to "Continue the range only when this strong ETag still matches.",
            "If-None-Match" to "Matching authorized content returns 304 with no body.",
        )) operation.addParametersItem(
        Parameter()
            .name(name)
            .`in`("header")
            .required(false)
            .description(description)
            .schema(StringSchema())
    )
    for (status in listOf("200", "206")) {
        operation.responses[status] =
            ApiResponse()
                .description(
                    if (status == "200") "Authorized complete content."
                    else "Authorized byte range."
                )
                .content(
                    Content()
                        .addMediaType(
                            if (payslipPdf) "application/pdf" else "application/octet-stream",
                            MediaType().schema(BinarySchema()),
                        )
                )
                .addHeaderObject("ETag", Header().schema(StringSchema()))
                .addHeaderObject("Accept-Ranges", Header().schema(StringSchema().example("bytes")))
                .addHeaderObject("Content-Range", Header().schema(StringSchema()))
                .addHeaderObject("Content-Disposition", Header().schema(StringSchema()))
    }
    operation.responses["304"] =
        ApiResponse()
            .description("Authorized content has not changed; no response body.")
            .addHeaderObject("ETag", Header().schema(StringSchema()))
    operation.responses["416"] =
        ApiResponse()
            .description("Unsupported or unsatisfiable byte range; no response body.")
            .addHeaderObject("Content-Range", Header().schema(StringSchema()))
    operation.description =
        if (payslipPdf)
            "Authorized finalized payslip with ID/EN labels, generated eagerly within a 1 MiB/16-page bound and four concurrent renders. Current authorization also gates conditional requests and content reads. HEAD returns metadata only. The bounded shared download transport supports Range/ETag; admission or late access failure may return an empty error body."
        else
            "The actual Content-Type follows the validated document. HEAD returns authorized metadata without bytes. Admission failure can return an empty 429/503 response. A failure after streaming starts cannot become a JSON problem; discard incomplete bytes and resume using Range/ETag."
}
