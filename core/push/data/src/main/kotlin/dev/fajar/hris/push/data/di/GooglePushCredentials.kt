package dev.fajar.hris.push.data.di

import com.google.auth.oauth2.ServiceAccountCredentials
import dev.fajar.hris.push.data.transport.GooglePushAuthTransport
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import tools.jackson.databind.ObjectMapper

fun readGooglePushCredentials(
    path: Path,
    project: String,
    transport: GooglePushAuthTransport,
    json: ObjectMapper,
): ServiceAccountCredentials {
    require(project.matches(Regex("[a-z][a-z0-9-]{4,28}[a-z0-9]"))) {
        "Invalid FCM project identifier"
    }
    val bytes = Files.newInputStream(path).use { it.readNBytes(32769) }
    try {
        require(bytes.size in 1..32768)
        val value = json.readTree(bytes)
        require(
            value.get("type")?.asString() == "service_account" &&
                value.get("project_id")?.asString() == project
        )
        require(value.get("token_uri")?.asString() == "https://oauth2.googleapis.com/token")
        require(value.get("universe_domain")?.asString() in setOf(null, "googleapis.com"))
        return ServiceAccountCredentials.fromStream(ByteArrayInputStream(bytes))
            .toBuilder()
            .setScopes(listOf("https://www.googleapis.com/auth/firebase.messaging"))
            .setHttpTransportFactory { transport }
            .setDefaultRetriesEnabled(false)
            .setUseJwtAccessWithScope(false)
            .setServiceAccountUser("")
            .build()
    } catch (_: Exception) {
        throw IllegalArgumentException("Invalid FCM service-account configuration")
    } finally {
        bytes.fill(0)
    }
}
