package dev.fajar.hris

import dev.fajar.hris.identity.delivery.requests.LoginRequest
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.reflect.full.primaryConstructor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.test.context.TestPropertySource
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import tools.jackson.databind.JsonNode

@TestPropertySource(properties = ["springdoc.api-docs.enabled=true"])
class ApiContractHttpTest : PeopleApiFixture() {
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private lateinit var mappings: RequestMappingHandlerMapping

    private fun contract(): JsonNode {
        val browser = client()
        login(browser)
        val response = get(browser, "/api/v1/openapi")
        assertEquals(200, response.statusCode(), response.body().take(500))
        assertTrue(response.body().length < 2_097_152, "The API description must stay bounded")
        return json.readTree(response.body())
    }

    private fun checkReferences(root: JsonNode, node: JsonNode, depth: Int = 0) {
        assertTrue(depth < 64)
        if (node.isObject) {
            node["\$ref"]?.let {
                val value = it.asString()
                assertTrue(
                    value.startsWith("#/"),
                    "Only local schema references are allowed: $value",
                )
                assertFalse(root.at(value.removePrefix("#")).isMissingNode, value)
            }
        }
        if (node.isObject || node.isArray)
            node.iterator().asSequence().forEach { checkReferences(root, it, depth + 1) }
    }

    @Test
    fun generatedContractCoversRegisteredRoutesAndHasValidLocalReferences() {
        val document = contract()
        assertTrue(document["openapi"].asString().startsWith("3.1."))
        assertEquals("/", document["servers"][0]["url"].asString())
        checkReferences(document, document)
        val options =
            ParseOptions().apply {
                setResolve(true)
                setResolveFully(false)
            }
        val validation = OpenAPIV3Parser().readContents(document.toString(), null, options)
        assertNotNull(validation.openAPI)
        assertTrue(validation.messages.isNullOrEmpty(), validation.messages.toString())
        val paths = document["paths"]
        val methods = setOf("get", "post", "put", "patch", "delete", "head", "options")
        val operationIds = mutableSetOf<String>()
        val documentedRoutes = mutableSetOf<Pair<String, String>>()
        for ((path, item) in paths.properties()) {
            assertTrue(path.startsWith("/api/v1/"), path)
            for ((method, operation) in item.properties()) {
                if (method !in methods) continue
                documentedRoutes.add(method to path)
                assertTrue(operationIds.add(operation["operationId"].asString()), "$method $path")
                val expected = Regex("\\{([^}]+)}").findAll(path).map { it.groupValues[1] }.toSet()
                val parameters = operation["parameters"].iterator().asSequence().toList()
                assertEquals(
                    expected,
                    parameters
                        .filter { it["in"].asString() == "path" }
                        .map { it["name"].asString() }
                        .toSet(),
                    path,
                )
                assertTrue(
                    parameters
                        .filter { it["in"].asString() == "path" }
                        .all { it["required"].asBoolean() },
                    path,
                )
                assertFalse(
                    parameters.any {
                        it["name"].asString() in
                            setOf(
                                "actor",
                                "credentialVersion",
                                "authenticatedAt",
                                "mfaVerifiedAt",
                                "token",
                            )
                    },
                    path,
                )
                assertEquals(
                    parameters.size,
                    parameters.map { it["in"].asString() to it["name"].asString() }.distinct().size,
                    path,
                )
                assertEquals(
                    "#/components/schemas/ApiProblem",
                    operation["responses"]["422"]["content"]["application/problem+json"]["schema"][
                            "\$ref"]
                        .asString(),
                )
            }
        }
        val registeredRoutes =
            mutableSetOf("post" to "/api/v1/auth/login", "post" to "/api/v1/auth/logout")
        for ((mapping, _) in mappings.handlerMethods) {
            for (path in mapping.patternValues) {
                if (!path.startsWith("/api/v1/") || path.startsWith("/api/v1/openapi")) continue
                for (method in mapping.methodsCondition.methods) {
                    registeredRoutes.add(method.name.lowercase() to path)
                    assertNotNull(
                        paths[path]?.get(method.name.lowercase()),
                        "Missing $method $path",
                    )
                }
            }
        }
        assertEquals(registeredRoutes, documentedRoutes)
        assertNotNull(paths["/api/v1/auth/login"]["post"])
        assertNotNull(paths["/api/v1/auth/logout"]["post"])
        assertNull(document["components"]["schemas"]["Actor"])
        assertNull(document["components"]["schemas"]["CsrfToken"])
        val output = Path.of("build/generated/openapi/hris-v1.json")
        Files.createDirectories(output.parent)
        Files.writeString(
            output,
            json.writerWithDefaultPrettyPrinter().writeValueAsString(document) + "\n",
        )
    }

    @Test
    fun mobileTransportDescribesTokensIdempotencySyncAndActualWireTypes() {
        val document = contract()
        val schemas = document["components"]["schemas"]
        val paths = document["paths"]
        val bootstrap = paths["/api/v1/companies/{companyId}/sync/bootstrap"]["get"]
        val choices =
            bootstrap["security"]
                .iterator()
                .asSequence()
                .map { it.properties().map { entry -> entry.key }.toSet() }
                .toSet()
        assertEquals(setOf(setOf("nativeBearer"), setOf("cookieSession")), choices)
        assertEquals(
            "COOKIE",
            paths["/api/v1/auth/native/exchange"]["post"]["x-hris-session-transport"].asString(),
        )
        assertEquals(0, paths["/api/v1/auth/native/refresh"]["post"]["security"].size())
        assertTrue(
            paths["/api/v1/auth/native/refresh"]["post"]["x-hris-idempotency-key"].asBoolean()
        )
        val selection =
            bootstrap["parameters"].iterator().asSequence().first {
                it["name"].asString() == "collections"
            }
        assertEquals("form", selection["style"].asString())
        assertFalse(selection["explode"].asBoolean())
        val company =
            bootstrap["parameters"].iterator().asSequence().first {
                it["name"].asString() == "companyId"
            }
        assertEquals("uuid", company["schema"]["format"].asString())
        val mutation = paths["/api/v1/companies/{companyId}/employees"]["post"]
        assertTrue(mutation["x-hris-idempotency-key"].asBoolean())
        val permissions =
            mutation["security"]
                .iterator()
                .asSequence()
                .map { it.properties().map { entry -> entry.key }.toSet() }
                .toSet()
        assertEquals(setOf(setOf("nativeBearer"), setOf("cookieSession", "csrfToken")), permissions)
        val amount = schemas["PayrollPaymentItemResponse"]["properties"]["amount"]
        assertTrue(
            amount["type"].asString() == "string" ||
                amount["type"].iterator().asSequence().any { it.asString() == "string" }
        )
        val month = schemas["PayrollPaymentItemResponse"]["properties"]["taxMonth"]
        assertEquals("2026-10", month["example"].asString())
        val cursor = schemas["SyncBootstrapResponse"]["properties"]["nextCursor"]
        assertTrue(
            cursor["type"].isArray &&
                cursor["type"].iterator().asSequence().any { it.asString() == "null" },
            cursor.toString(),
        )
        assertNull(schemas["ApiProblem"]["properties"]["code"]["enum"])
        assertEquals(
            LoginRequest::class.primaryConstructor!!.parameters.mapNotNull { it.name }.toSet(),
            schemas["PasswordSignInRequest"]["properties"].properties().map { it.key }.toSet(),
        )
        assertEquals(
            setOf("token", "headerName"),
            schemas["CsrfResponse"]["properties"].properties().map { it.key }.toSet(),
        )
        assertEquals(
            setOf("companyId", "permissions"),
            schemas["CompanyAccessResponse"]["properties"].properties().map { it.key }.toSet(),
        )
    }

    @Test
    fun negotiatedDownloadsAndRawUploadsAreNotDocumentedAsJsonDtos() {
        val document = contract()
        val paths = document["paths"]
        val payslip = paths["/api/v1/companies/{companyId}/payroll/payslips/{id}/pdf"]["get"]
        assertEquals(
            "binary",
            payslip["responses"]["200"]["content"]["application/pdf"]["schema"]["format"].asString(),
        )
        assertNotNull(payslip["responses"]["403"]["content"]["application/problem+json"])
        assertEquals(
            "binary",
            payslip["responses"]["206"]["content"]["application/pdf"]["schema"]["format"].asString(),
        )
        assertNull(payslip["responses"]["304"]["content"])
        val download =
            paths["/api/v1/companies/{companyId}/documents/revisions/{revisionId}/content"]["get"]
        for (status in listOf("200", "206")) {
            assertEquals(
                "binary",
                download["responses"][status]["content"]["application/octet-stream"]["schema"][
                        "format"]
                    .asString(),
            )
        }
        assertNull(download["responses"]["304"]["content"])
        assertNull(download["responses"]["416"]["content"])
        assertTrue(
            download["parameters"].iterator().asSequence().any {
                it["name"].asString() == "If-Range"
            }
        )
        val upload =
            paths["/api/v1/companies/{companyId}/documents/revisions/{revisionId}/chunks"]["post"]
        assertEquals(
            "binary",
            upload["requestBody"]["content"]["application/octet-stream"]["schema"]["format"]
                .asString(),
        )
        for (module in listOf("expenses", "payroll")) {
            val csv = paths["/api/v1/companies/{companyId}/$module/payments/{id}/export"]["get"]
            assertNotNull(csv["responses"]["200"]["content"]["text/csv"])
            assertNull(csv["responses"]["200"]["content"]["application/json"])
        }
    }

    @Test
    fun documentationRequiresACurrentAuthenticatedAccount() {
        val browser = client()
        assertEquals(401, get(browser, "/api/v1/openapi").statusCode())
        val account = java.util.UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Documentation fixture',password_hash from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        login(browser, "$account@example.test")
        assertEquals(200, get(browser, "/api/v1/openapi").statusCode())
        database()
            .update("update accounts set security_version=security_version+1 where id=?", account)
        assertEquals(401, get(browser, "/api/v1/openapi").statusCode())
    }

    @Test
    fun languageHeadersRetainOneCanonicalDescription() {
        val browser = client()
        login(browser)
        val original = get(browser, "/api/v1/openapi")
        assertEquals(200, original.statusCode(), original.body().take(500))
        for (language in listOf("id-ID", "en-GB", "fr-FR")) {
            val response =
                browser.send(
                    HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/v1/openapi"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Accept-Language", language)
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            assertEquals(200, response.statusCode())
            assertEquals(original.body(), response.body())
        }
    }
}
