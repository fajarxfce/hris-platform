package dev.fajar.hris

import java.net.URI
import java.net.http.*
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Assertions.*

abstract class NativeCollaborationApiFixture : ClientPolicyApiFixture() {
    protected class Native(val fixture: Fixture, val session: UUID, val access: String) {
        val path = "${fixture.root}/collaboration/native-device"

        override fun toString() = "Native(<redacted>)"
    }

    protected fun native(grant: Boolean = true): Native {
        val f = fixture()
        if (grant) {
            // Existing role snapshots deliberately do not acquire new permissions implicitly.
            val path = "${f.root}/members/${f.account}"
            val current = get(f.browser, path)
            assertEquals(200, current.statusCode(), current.body())
            val membership = json.readTree(current.body())
            val permissions =
                membership["directPermissions"]
                    .iterator()
                    .asSequence()
                    .map { it.asString() }
                    .toSet() + "collaboration.chat"
            val roles =
                membership["roleTemplates"]
                    .iterator()
                    .asSequence()
                    .map {
                        mapOf(
                            "id" to it["id"].asString(),
                            "version" to it["version"].asLong(),
                            "retainApplied" to true,
                        )
                    }
                    .toList()
            val saved =
                command(
                    f.browser,
                    path,
                    json.writeValueAsString(
                        mapOf(
                            "expectedVersion" to membership["member"]["version"].asLong(),
                            "active" to true,
                            "permissions" to permissions,
                            "roleTemplates" to roles,
                            "reason" to "Enable collaboration for fictional HTTP acceptance",
                        )
                    ),
                    f.csrf,
                    UUID.randomUUID(),
                    "PUT",
                )
            assertEquals(200, saved.statusCode(), saved.body())
        }
        val exchange =
            command(
                f.browser,
                "/api/v1/auth/native/exchange",
                """{"deviceName":"Collaboration HTTP fixture"}""",
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, exchange.statusCode(), exchange.body())
        val data = json.readTree(exchange.body())
        return Native(
            f,
            UUID.fromString(data["sessionId"].asString()),
            data["accessToken"].asString(),
        )
    }

    protected fun register(
        native: Native,
        operation: UUID? = UUID.randomUUID(),
        body: Map<String, Any> = mapOf("platform" to "ANDROID", "label" to "Fictional emulator"),
        platform: String = "ANDROID",
        build: String = "1",
        token: String = native.access,
    ): HttpResponse<String> {
        val builder =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port${native.path}"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("X-HRIS-Client-Platform", platform)
                .header("X-HRIS-Client-Build", build)
        operation?.let { builder.header("Idempotency-Key", it.toString()) }
        return native.fixture.browser.send(
            builder
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }
}
