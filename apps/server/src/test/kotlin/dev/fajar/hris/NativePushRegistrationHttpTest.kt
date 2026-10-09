package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.usecases.SaveNativePushRegistration
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

@Import(NativePushRegistrationProbeConfiguration::class)
class NativePushRegistrationHttpTest : MfaApiFixture() {
    @Autowired private lateinit var save: SaveNativePushRegistration
    @Autowired private lateinit var probe: NativePushRegistrationProbe
    @Autowired private lateinit var crypto: IdentityTokenDataSource
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var jdbc: JdbcTemplate

    private data class Native(
        val fixture: Fixture,
        val id: UUID,
        val access: String,
        val credential: Long,
    ) {
        override fun toString() = "Native(<redacted>)"
    }

    private val path = "/api/v1/auth/native/push-registration"

    private fun native(f: Fixture = enroll(fixture()).fixture): Native {
        val response =
            command(
                f.client,
                "/api/v1/auth/native/exchange",
                """{"deviceName":"Push fixture"}""",
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, response.statusCode(), response.body())
        val body = json.readTree(response.body())
        return Native(
            f,
            UUID.fromString(body["sessionId"].asString()),
            body["accessToken"].asString(),
            requireNotNull(
                database()
                    .queryForObject(
                        "select security_version from accounts where id=?",
                        Long::class.java,
                        f.account,
                    )
            ),
        )
    }

    private fun actor(n: Native) =
        Actor(
            n.fixture.account,
            null,
            emptySet(),
            clock.instant(),
            UUID.randomUUID(),
            clock.instant(),
            n.credential,
        )

    private fun request(
        n: Native,
        method: String = "GET",
        body: Any? = null,
        operation: UUID = UUID.randomUUID(),
        suffix: String = "",
        client: HttpClient = n.fixture.client,
    ): HttpResponse<String> {
        val builder =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path$suffix"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer ${n.access}")
                .header("Idempotency-Key", operation.toString())
        if (body != null) builder.header("Content-Type", "application/json")
        return client.send(
            builder
                .method(
                    method,
                    body?.let { HttpRequest.BodyPublishers.ofString(json.writeValueAsString(it)) }
                        ?: HttpRequest.BodyPublishers.noBody(),
                )
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    private fun input(token: String, version: Long? = null, platform: String = "ANDROID") =
        mapOf("expectedVersion" to version, "platform" to platform, "token" to token)

    private fun token() = "fixture:${UUID.randomUUID()}:${UUID.randomUUID()}"

    private fun failure(response: HttpResponse<String>, code: String, status: Int) {
        assertEquals(status, response.statusCode(), response.body())
        assertEquals(code, json.readTree(response.body())["code"].asString())
    }

    @AfterEach
    fun clearProbe() {
        probe.current.getAndSet(null)?.release?.countDown()
    }

    @Test
    fun encryptedRegistrationRotationAndDisableAreVersionedReplayableAndSessionOwned() {
        val n = native()
        failure(request(n), "push_registration_not_found", 404)
        val token = token()
        val operation = UUID.randomUUID()
        val body = input(token)
        val created = request(n, "PUT", body, operation)
        assertEquals(200, created.statusCode(), created.body())
        assertEquals(created.body(), request(n, "PUT", body, operation).body())
        assertEquals(0, json.readTree(created.body())["version"].asInt())
        val read = request(n)
        assertEquals(200, read.statusCode(), read.body())
        assertFalse(read.body().contains(token))
        assertFalse(read.body().contains("token"))
        assertEquals(n.id.toString(), json.readTree(read.body())["sessionId"].asString())
        val row =
            database()
                .queryForMap("select * from native_push_registrations where session_id=?", n.id)
        assertNotEquals(token, row["token_hash"])
        assertFalse(row["token_encrypted"].toString().contains(token))
        assertEquals(
            token,
            crypto.decrypt(
                "hris:push:${n.fixture.account}:${n.id}:0",
                row["token_encrypted"].toString(),
            ),
        )
        assertThrows(Exception::class.java) {
            crypto.decrypt(
                "hris:push:${n.fixture.account}:${n.id}:1",
                row["token_encrypted"].toString(),
            )
        }
        failure(request(n, "PUT", input(token(), 0), operation), "operation_payload_mismatch", 409)
        failure(request(n, "PUT", input(token())), "stale_version", 409)
        val next = token()
        assertEquals(200, request(n, "PUT", input(next, 0, "IOS")).statusCode())
        val disableId = UUID.randomUUID()
        val disabled = request(n, "POST", mapOf("expectedVersion" to 1), disableId, "/disable")
        assertEquals(200, disabled.statusCode(), disabled.body())
        assertEquals(
            disabled.body(),
            request(n, "POST", mapOf("expectedVersion" to 1), disableId, "/disable").body(),
        )
        val cleared =
            database()
                .queryForMap(
                    "select token_hash,token_encrypted,enabled,version from native_push_registrations where session_id=?",
                    n.id,
                )
        assertNull(cleared["token_hash"])
        assertNull(cleared["token_encrypted"])
        assertEquals(false, cleared["enabled"])
        assertEquals(2L, cleared["version"])
        assertEquals(
            200,
            request(n, "POST", mapOf("expectedVersion" to 2), suffix = "/disable").statusCode(),
        )
        assertEquals(created.body(), request(n, "PUT", body, operation).body())
        assertFalse(json.readTree(request(n).body())["enabled"].asBoolean())
        assertEquals(200, request(n, "PUT", input(token(), 2)).statusCode())
        assertEquals(
            4,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action like 'identity.push_registration_%'",
                    Int::class.java,
                    n.id,
                ),
        )
    }

    @Test
    fun duplicateTokenCannotSilentlyTransferToAnotherSessionOrAccount() {
        val first = native()
        val second = native(first.fixture)
        val other = native()
        val shared = token()
        assertEquals(200, request(first, "PUT", input(shared)).statusCode())
        failure(request(second, "PUT", input(shared)), "data_conflict", 409)
        failure(request(other, "PUT", input(shared)), "data_conflict", 409)
        failure(request(second), "push_registration_not_found", 404)
        assertEquals(
            200,
            request(first, "POST", mapOf("expectedVersion" to 0), suffix = "/disable").statusCode(),
        )
        assertEquals(200, request(other, "PUT", input(shared)).statusCode())
        val browser =
            command(
                first.fixture.client,
                path,
                json.writeValueAsString(input(token())),
                first.fixture.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        failure(browser, "invalid_session_transport", 403)
        assertEquals(403, get(first.fixture.client, path).statusCode())
    }

    @Test
    fun competingCreatesAndUpdatesDoNotOverwriteTheWinningToken() {
        val n = native()
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val mobile = client()
        val responses =
            Executors.newFixedThreadPool(2).use { executor ->
                val futures =
                    (1..2).map {
                        executor.submit<HttpResponse<String>> {
                            ready.countDown()
                            check(start.await(5, TimeUnit.SECONDS))
                            request(n, "PUT", input(token()), client = mobile)
                        }
                    }
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                start.countDown()
                futures.map { it.get(15, TimeUnit.SECONDS) }
            }
        assertEquals(listOf(200, 409), responses.map { it.statusCode() }.sorted())
        failure(responses.single { it.statusCode() == 409 }, "stale_version", 409)
        val operation = UUID.randomUUID()
        val body = input(token(), 0)
        val equal =
            Executors.newFixedThreadPool(2).use { executor ->
                (1..2)
                    .map {
                        executor.submit<HttpResponse<String>> {
                            request(n, "PUT", body, operation, client = mobile)
                        }
                    }
                    .map { it.get(15, TimeUnit.SECONDS) }
            }
        assertEquals(200, equal.first().statusCode(), equal.first().body())
        assertEquals(equal.first().body(), equal.last().body())
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='identity.push_registration_saved'",
                    Int::class.java,
                    n.id,
                ),
        )
    }

    @Test
    fun sessionAndCredentialRevocationDuringAPendingGuardRejectTheCommandAndReceiptReplay() {
        val n = native()
        val operation = UUID.randomUUID()
        val input = SaveNativePushRegistrationCommand(null, PushPlatform.ANDROID, token())
        val first = save.execute(actor(n), n.id, 0, operation, input)
        assertTrue(first is Result.Success, first.toString())
        val barrier = NativePushRegistrationProbe.Barrier(n.fixture.account)
        probe.current.set(barrier)
        val result =
            Executors.newSingleThreadExecutor().use { executor ->
                val pending =
                    executor.submit<Result<MutationReceipt>> {
                        save.execute(actor(n), n.id, 0, operation, input)
                    }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    database()
                        .update(
                            "update accounts set security_version=security_version+1 where id=?",
                            n.fixture.account,
                        )
                } finally {
                    barrier.release.countDown()
                }
                pending.get(10, TimeUnit.SECONDS)
            }
        assertEquals("native_session_invalid", (result as Result.Failed).failure.code)
        failure(request(n), "session_revoked", 401)
        probe.current.set(null)
        val second = native()
        val stopped = NativePushRegistrationProbe.Barrier(second.fixture.account)
        probe.current.set(stopped)
        val failed =
            Executors.newSingleThreadExecutor().use { executor ->
                val pending =
                    executor.submit<Result<MutationReceipt>> {
                        save.execute(actor(second), second.id, 0, UUID.randomUUID(), input)
                    }
                try {
                    assertTrue(stopped.entered.await(5, TimeUnit.SECONDS))
                    database()
                        .update(
                            "update native_sessions set revoked_at=? where id=?",
                            java.sql.Timestamp.from(clock.instant()),
                            second.id,
                        )
                } finally {
                    stopped.release.countDown()
                }
                pending.get(10, TimeUnit.SECONDS)
            }
        assertEquals("native_session_invalid", (failed as Result.Failed).failure.code)
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from native_push_registrations where session_id=?",
                    Int::class.java,
                    second.id,
                ),
        )
    }

    @Test
    fun lateJournalFailureRollsBackEncryptedTokenVersionAndOperationReceipt() {
        val n = native()
        val oldToken = token()
        assertEquals(200, request(n, "PUT", input(oldToken)).statusCode())
        val before =
            database()
                .queryForMap("select * from native_push_registrations where session_id=?", n.id)
        database()
            .execute(
                """CREATE FUNCTION test_block_push_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.resource_id='${n.id}'::uuid AND NEW.action='identity.push_registration_saved' THEN RAISE EXCEPTION 'private push detail' USING ERRCODE='23514'; END IF; RETURN NEW; END $$"""
            )
        database()
            .execute(
                "CREATE TRIGGER test_block_push_audit BEFORE INSERT ON audit_entries FOR EACH ROW EXECUTE FUNCTION test_block_push_audit()"
            )
        val operation = UUID.randomUUID()
        val body = input(token(), 0)
        try {
            val failed = request(n, "PUT", body, operation)
            failure(failed, "data_conflict", 409)
            assertFalse(failed.body().contains("private"))
            assertEquals(
                before,
                database()
                    .queryForMap("select * from native_push_registrations where session_id=?", n.id),
            )
        } finally {
            database().execute("DROP TRIGGER test_block_push_audit ON audit_entries")
            database().execute("DROP FUNCTION test_block_push_audit()")
        }
        assertEquals(200, request(n, "PUT", body, operation).statusCode())
        assertEquals(1, json.readTree(request(n).body())["version"].asInt())
    }

    @Test
    fun registrationBoundsAndDatabaseOwnershipCannotBeBypassed() {
        val n = native()
        for (value in
            listOf("short", "x".repeat(2049), "x".repeat(20) + "\n", "x".repeat(20) + "é")) failure(
            request(n, "PUT", input(value)),
            "invalid_push_registration",
            422,
        )
        failure(request(n, "PUT", input(token(), -1)), "invalid_push_registration", 422)
        assertEquals(200, request(n, "PUT", input(token())).statusCode())
        val other = native()
        assertEquals(
            Result.Success(0),
            transactions.run(actor(other)) {
                Result.Success(
                    jdbc.queryForObject(
                        "select count(*) from native_push_registrations where session_id=?",
                        Int::class.java,
                        n.id,
                    )
                )
            },
        )
        assertTrue(
            transactions.run(actor(other)) {
                jdbc.update(
                    "update native_push_registrations set enabled=false,token_hash=null,token_encrypted=null,version=version+1 where session_id=?",
                    n.id,
                )
                jdbc.update(
                    "insert into native_push_registrations(session_id,account_id,platform,enabled,version,registered_at,updated_at,expires_at) select id,?,'ANDROID',false,0,created_at,created_at,expires_at from native_sessions where id=?",
                    other.fixture.account,
                    n.id,
                )
                Result.Success(Unit)
            } is Result.Failed
        )
        assertThrows(Exception::class.java) {
            database()
                .update(
                    "update native_push_registrations set account_id=? where session_id=?",
                    other.fixture.account,
                    n.id,
                )
        }
        assertThrows(Exception::class.java) {
            database()
                .update(
                    "update native_push_registrations set version=version+2 where session_id=?",
                    n.id,
                )
        }
        assertThrows(Exception::class.java) {
            database()
                .update(
                    "update native_push_registrations set expires_at=expires_at+interval '100 days',version=version+1 where session_id=?",
                    n.id,
                )
        }
        val state = json.readTree(request(n).body())
        assertTrue(state["enabled"].asBoolean())
        val sessionExpiry =
            requireNotNull(
                    database()
                        .queryForObject(
                            "select expires_at from native_sessions where id=?",
                            java.sql.Timestamp::class.java,
                            n.id,
                        )
                )
                .toInstant()
        assertEquals(sessionExpiry, java.time.Instant.parse(state["expiresAt"].asString()))
        assertThrows(Exception::class.java) {
            database()
                .update(
                    "update native_push_registrations set token_encrypted=null,version=version+1 where session_id=?",
                    n.id,
                )
        }
    }
}
