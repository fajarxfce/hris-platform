package dev.fajar.hris

import dev.fajar.hris.core.domain.TransactionRunner
import java.net.URLEncoder
import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import tools.jackson.databind.JsonNode

@Import(AccountLockProbeConfiguration::class)
abstract class ExpenseReportApiFixture : ExpenseReviewApiFixture() {
    @Autowired protected lateinit var transactions: TransactionRunner
    @Autowired protected lateinit var accountProbe: AccountLockProbe

    @AfterEach
    fun clearAccountAdmission() {
        accountProbe.current.getAndSet(null)?.release?.countDown()
    }

    protected fun reportFixture(): ExpenseFixture =
        expenseFixture().also {
            expensePolicy(it, changes = mapOf("receiptRequired" to false))
            expenseTemplate(it)
        }

    protected fun reportFrom(): LocalDate =
        LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Jakarta")).minusDays(3)

    protected fun reportUntil(): LocalDate = reportFrom().plusDays(2)

    protected fun reportLine(
        f: ExpenseFixture,
        amount: String = "123.45",
        date: LocalDate = reportFrom(),
    ) =
        expenseLine(
            f,
            mapOf("id" to UUID.randomUUID(), "occurredOn" to date.toString(), "amount" to amount),
        )

    protected fun reportPath(
        f: ExpenseFixture,
        rows: Boolean = false,
        status: String? = null,
        after: String? = null,
        limit: Int = 50,
    ): String =
        "/api/v1/companies/${f.company}/reports/expenses${if (rows) "/claims" else ""}?from=${reportFrom()}&until=${reportUntil()}" +
            (if (rows) "&limit=$limit" else "") +
            (status?.let { "&status=$it" } ?: "") +
            (after?.let { "&after=${URLEncoder.encode(it, Charsets.UTF_8)}" } ?: "")

    protected fun body(response: HttpResponse<String>): JsonNode =
        json.readTree(response.body()).also {
            assertEquals(200, response.statusCode(), response.body())
        }

    protected fun error(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode(), response.body())
        assertEquals(code, json.readTree(response.body())["code"].asString())
    }

    protected fun report(f: ExpenseFixture): JsonNode =
        get(f.admin, reportPath(f)).let {
            assertTrue(it.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
            body(it)
        }

    protected fun rows(
        f: ExpenseFixture,
        status: String? = null,
        after: String? = null,
        limit: Int = 50,
    ): JsonNode = body(get(f.admin, reportPath(f, true, status, after, limit)))

    protected fun counts(value: JsonNode, status: String): JsonNode =
        value["statuses"].single { it["status"].asString() == status }["counts"]
}
