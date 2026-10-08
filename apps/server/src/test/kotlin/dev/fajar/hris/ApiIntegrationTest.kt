package dev.fajar.hris

import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.ObjectMapper

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS
)
abstract class ApiIntegrationTest {
    companion object {
        @Container
        @JvmStatic
        val container =
            PostgreSQLContainer("postgres:18.6-alpine").withInitScript("runtime-role.sql")

        @DynamicPropertySource
        @JvmStatic
        fun configuration(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { container.jdbcUrl }
            registry.add("spring.datasource.username") { "hris_test_runtime" }
            registry.add("spring.datasource.password") { "test-runtime-only" }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.url") { container.jdbcUrl }
            registry.add("spring.flyway.user") { container.username }
            registry.add("spring.flyway.password") { container.password }
            registry.add("server.servlet.session.cookie.secure") { false }
            registry.add("HRIS_BOOTSTRAP_EMAIL") { "admin@example.test" }
            registry.add("HRIS_BOOTSTRAP_PASSWORD") { "Testing-password-123!" }
        }
    }

    protected val postgres: PostgreSQLContainer
        get() = container

    @LocalServerPort protected var port: Int = 0
    @Autowired protected lateinit var json: ObjectMapper

    private val clients = mutableListOf<HttpClient>()

    @AfterEach
    fun closeClients() {
        clients.forEach { it.close() }
    }

    protected fun client(): HttpClient =
        HttpClient.newBuilder()
            .cookieHandler(CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .connectTimeout(java.time.Duration.ofSeconds(5))
            .build()
            .also { clients += it }

    protected fun get(client: HttpClient, path: String): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                .timeout(java.time.Duration.ofSeconds(15))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    protected fun post(
        client: HttpClient,
        path: String,
        body: String,
        csrf: String? = null,
    ): HttpResponse<String> {
        val request =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                .timeout(java.time.Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf)
        return client.send(
            request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    protected fun login(client: HttpClient, email: String = "admin@example.test"): String {
        val csrf = json.readTree(get(client, "/api/v1/auth/csrf").body()).get("token").asString()
        val response =
            post(
                client,
                "/api/v1/auth/login",
                json.writeValueAsString(
                    mapOf("email" to email, "password" to "Testing-password-123!")
                ),
                csrf,
            )
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(get(client, "/api/v1/auth/csrf").body()).get("token").asString()
    }

    protected fun command(
        client: HttpClient,
        path: String,
        body: String,
        csrf: String,
        key: UUID,
        method: String = "POST",
    ): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                .timeout(java.time.Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", csrf)
                .header("Idempotency-Key", key.toString())
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    protected fun database(): JdbcTemplate =
        JdbcTemplate(
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        )
}
