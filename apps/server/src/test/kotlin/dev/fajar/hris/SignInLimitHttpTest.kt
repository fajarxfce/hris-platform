package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.AuthenticationAttemptPolicy
import dev.fajar.hris.identity.domain.repositories.AuthenticationRateLimitRepository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties =
        [
            "server.address=127.0.0.1",
            "hris.security.sign-in-limits=true",
            "hris.security.enforce-mfa=false",
        ],
)
@Import(TestClockConfiguration::class)
class SignInLimitHttpTest : ApiIntegrationTest() {
    @Autowired lateinit var clock: MutableTestClock
    @Autowired lateinit var limits: AuthenticationRateLimitRepository
    @Autowired lateinit var transactions: TransactionRunner

    @Test
    fun failedAttemptsCommitTheirLimitsWithoutRevealingAccountExistence() {
        clock.set(Instant.parse("2026-10-01T00:00:00Z"))
        val client = client()
        val csrf = json.readTree(get(client, "/api/v1/auth/csrf").body()).get("token").asString()
        for (email in listOf("admin@example.test", "missing-${UUID.randomUUID()}@example.test")) {
            repeat(10) {
                val denied =
                    post(
                        client,
                        "/api/v1/auth/login",
                        json.writeValueAsString(
                            mapOf("email" to email, "password" to "wrong-password")
                        ),
                        csrf,
                    )
                assertEquals(401, denied.statusCode(), denied.body())
            }
            val blocked =
                post(
                    client,
                    "/api/v1/auth/login",
                    json.writeValueAsString(
                        mapOf("email" to email, "password" to "Testing-password-123!")
                    ),
                    csrf,
                )
            assertEquals(429, blocked.statusCode(), blocked.body())
            assertEquals(
                "sign_in_rate_limited",
                json.readTree(blocked.body()).get("code").asString(),
            )
            assertEquals("900", blocked.headers().firstValue("Retry-After").orElseThrow())
        }
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from authentication_attempts where attempts=10",
                    Int::class.java,
                ),
        )
        clock.set(clock.instant().plusSeconds(901))
        login(client)
        assertEquals(200, get(client, "/api/v1/me").statusCode())
        assertFalse(
            database()
                .queryForObject(
                    "select string_agg(bucket_hash, ',') from authentication_attempts",
                    String::class.java,
                )!!
                .contains("admin")
        )
    }

    @Test
    fun competingAttemptsHaveOneSharedBudgetAndCleanupIsBounded() {
        val now = Instant.parse("2026-10-02T00:00:00Z")
        val scope = Actor(UUID(0, 0), null, emptySet(), now, UUID.randomUUID())
        val email = "${UUID.randomUUID()}@example.test"
        val origin = UUID.randomUUID().toString()
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        val outcomes =
            Executors.newFixedThreadPool(8).use { executor ->
                val tasks =
                    (1..8).map {
                        executor.submit<Result<Boolean>> {
                            ready.countDown()
                            check(start.await(5, TimeUnit.SECONDS))
                            transactions.run(scope) {
                                limits.takeAttempt(
                                    email,
                                    origin,
                                    now,
                                    AuthenticationAttemptPolicy(perAccount = 3),
                                )
                            }
                        }
                    }
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                start.countDown()
                tasks.map { it.get(10, TimeUnit.SECONDS) }
            }
        assertEquals(3, outcomes.count { it == Result.Success(true) })
        assertEquals(5, outcomes.count { it == Result.Success(false) })
        database()
            .execute(
                "insert into authentication_attempts(bucket_hash,window_start,expires_at,attempts) select lpad(i::text,64,'0'),'2020-01-01'::timestamptz,'2020-01-02'::timestamptz,1 from generate_series(1,150) i"
            )
        val removed = transactions.run(scope) { limits.purgeExpired(now.minusSeconds(86400), 100) }
        assertEquals(Result.Success(100), removed)
        assertEquals(
            50,
            database()
                .queryForObject(
                    "select count(*) from authentication_attempts where expires_at<'2021-01-01'",
                    Int::class.java,
                ),
        )
        assertEquals(
            Result.Success(false),
            transactions.run(scope) {
                limits.takeAttempt(
                    email,
                    origin,
                    now.minusSeconds(1000),
                    AuthenticationAttemptPolicy(perAccount = 3),
                )
            },
        )
    }
}
