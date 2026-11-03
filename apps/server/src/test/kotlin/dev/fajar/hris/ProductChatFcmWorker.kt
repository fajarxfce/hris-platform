package dev.fajar.hris

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64
import java.util.concurrent.TimeUnit
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Real worker, restricted role, disposable database; only this opt-in fixture reads FCM
 * credentials.
 */
internal class ProductChatFcmWorker(
    root: Path,
    directory: Path,
    database: JdbcTemplate,
    databaseUrl: String,
    provider: MatrixPushProviderFixture,
) : AutoCloseable {
    private val process: Process

    init {
        val jar = Path.of(requireNotNull(System.getenv("HRIS_COLLAB_PRODUCT_WORKER_JAR")))
        require(jar.isAbsolute && Files.isRegularFile(jar))
        val project = requireNotNull(System.getenv("HRIS_TEST_FCM_PROJECT_ID"))
        val credentials = Path.of(requireNotNull(System.getenv("HRIS_TEST_FCM_CREDENTIALS_FILE")))
        require(project.matches(Regex("[a-z][a-z0-9-]{4,28}[a-z0-9]")))
        require(
            credentials.isAbsolute &&
                Files.isRegularFile(credentials) &&
                Files.size(credentials) in 1..32768
        )
        val permissions = Files.getPosixFilePermissions(credentials)
        require(permissions.none { it.name.startsWith("GROUP_") || it.name.startsWith("OTHERS_") })
        // The migration creates the capability role; this login belongs only to the owned test DB.
        database.execute(
            "CREATE ROLE hris_product_fcm_worker LOGIN PASSWORD 'product-fcm-fixture-only' INHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS"
        )
        database.execute("GRANT hris_worker_capability TO hris_product_fcm_worker")
        database.execute("GRANT USAGE ON SCHEMA public TO hris_product_fcm_worker")
        database.execute(
            "GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO hris_product_fcm_worker"
        )
        database.execute(
            "GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO hris_product_fcm_worker"
        )
        val log =
            Files.createFile(
                directory.resolve("worker.log"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")),
            )
        val builder =
            ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Xmx512m",
                    "-jar",
                    jar.toString(),
                    // Match ApiIntegrationTest's explicit fictional-account assurance policy.
                    "--hris.security.enforce-mfa=false",
                )
                .directory(root.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
        val env = builder.environment()
        // Never inherit live HRIS configuration into the independently owned fixture.
        env.keys.removeIf {
            it.startsWith("HRIS_") || it.startsWith("SPRING_") || it.startsWith("SERVER_")
        }
        env.putAll(
            mapOf(
                "HRIS_DATABASE_URL" to databaseUrl,
                "HRIS_WORKER_DATABASE_USER" to "hris_product_fcm_worker",
                "HRIS_WORKER_DATABASE_PASSWORD" to "product-fcm-fixture-only",
                "HRIS_IDENTITY_ACTIVE_KEY" to "v1",
                "HRIS_IDENTITY_KEYS" to
                    "v1:${Base64.getEncoder().encodeToString(ByteArray(32) { 17 })}",
                "HRIS_COLLABORATION_ENABLED" to "true",
                "HRIS_MATRIX_ENABLED" to "true",
                "HRIS_MATRIX_ORIGIN" to provider.origin.toString(),
                "HRIS_MATRIX_SERVER_NAME" to "matrix.example.test",
                "HRIS_MATRIX_JWT_ISSUER" to "urn:hris:matrix:fixture",
                "HRIS_MATRIX_JWT_AUDIENCE" to "matrix:fixture",
                "HRIS_MATRIX_JWT_SECRET_FILE" to provider.signingFile.toString(),
                "HRIS_MATRIX_PUSH_GATEWAY_URL" to provider.callback,
                "HRIS_COLLABORATION_PUSH_ENABLED" to "true",
                "HRIS_COLLABORATION_PUSH_DELIVERY_ENABLED" to "true",
                "HRIS_FCM_ENABLED" to "true",
                "HRIS_FCM_PROJECT_ID" to project,
                "HRIS_FCM_CREDENTIALS_FILE" to credentials.toString(),
                "HRIS_MAIL_ENABLED" to "false",
                "HRIS_STORAGE_ENABLED" to "false",
                "HRIS_FIREBASE_AUTH_ENABLED" to "false",
                "HRIS_NATIVE_SESSION_CLEANUP_ENABLED" to "false",
            )
        )
        process = builder.start()
    }

    fun checkAlive() = check(process.isAlive) { "Owned FCM worker exited; inspect its private log" }

    override fun close() {
        process.destroy()
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            check(process.waitFor(5, TimeUnit.SECONDS))
        }
    }
}
