package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.sync.domain.entities.*
import dev.fajar.hris.sync.domain.repositories.*
import dev.fajar.hris.sync.domain.usecases.*
import java.net.URLEncoder
import java.net.http.HttpResponse
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.JsonNode

@TestPropertySource(properties = [MOBILE_SYNC_TEST_KEYS])
@Import(MobileSyncProbeConfiguration::class, AccountLockProbeConfiguration::class)
abstract class MobileSyncApiFixture : ExpenseApiFixture() {
    @Autowired protected lateinit var syncProbe: MobileSyncProbe
    @Autowired protected lateinit var accountProbe: AccountLockProbe
    @Autowired protected lateinit var bootstrapUseCase: GetMobileSyncBootstrap
    @Autowired protected lateinit var changesUseCase: GetMobileSyncChanges
    @Autowired protected lateinit var cursors: SyncCursorRepository
    @Autowired protected lateinit var transactions: TransactionRunner
    @Autowired protected lateinit var sync: SyncRepository
    @Autowired protected lateinit var runtimeJdbc: JdbcTemplate
    protected lateinit var publisher: MobileSyncTestWorker

    @BeforeEach
    fun prepareSyncWorker() {
        publisher = mobileSyncTestWorker(postgres.jdbcUrl, database())
        repeat(30) {
            val completed = success(publisher.maintain.execute())
            if (completed.published == 0 && completed.pruned == 0) return
        }
        fail<Unit>("Fixture publication exceeded its bounded cleanup budget")
    }

    @AfterEach
    fun clearSyncProbes() {
        syncProbe.clear()
        accountProbe.current.getAndSet(null)?.release?.countDown()
    }

    protected fun <T> success(result: Result<T>): T {
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    protected fun actor(f: ExpenseFixture) =
        Actor(
            f.account,
            f.company,
            setOf("company.read", "expenses.self.manage"),
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = 0,
        )

    protected fun bootstrap(f: ExpenseFixture, token: String? = null, limit: Int = 100) =
        get(
            f.worker,
            "/api/v1/companies/${f.company}/sync/bootstrap?limit=$limit" +
                (token?.let { "&cursor=${encode(it)}" } ?: ""),
        )

    protected fun changes(f: ExpenseFixture, token: String, limit: Int = 100) =
        get(
            f.worker,
            "/api/v1/companies/${f.company}/sync/changes?limit=$limit&cursor=${encode(token)}",
        )

    protected fun encode(token: String) = URLEncoder.encode(token, Charsets.UTF_8)

    protected fun body(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    protected fun error(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode(), response.body())
        assertEquals(code, json.readTree(response.body()).get("code").asString())
        assertTrue(
            response
                .headers()
                .allValues("Cache-Control")
                .flatMap { it.split(',') }
                .any { it.trim() == "no-store" }
        )
    }

    protected fun token(f: ExpenseFixture) = body(bootstrap(f)).get("changesCursor").asString()

    protected fun items(page: JsonNode): List<JsonNode> =
        page.get("items").iterator().asSequence().toList()

    protected fun ids(page: JsonNode) = items(page).map { it.get("id").asString() }

    protected fun queueCount(f: ExpenseFixture) =
        database()
            .queryForObject(
                "select count(*) from mobile_sync_changes where company_id=?",
                Int::class.java,
                f.company,
            )!!
}
