package dev.fajar.hris

import java.math.BigDecimal
import java.net.http.HttpClient
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import tools.jackson.databind.JsonNode

abstract class PayrollCostReportApiFixture : PayrollAmendmentPublicationApiFixture() {
    private val reportReaders = mutableMapOf<UUID, PayrollMember>()

    protected fun reportReader(f: PayrollFixture): PayrollMember =
        reportReaders.getOrPut(f.company) {
            payrollMember(f.company, setOf("reports.read", "payroll.read"))
        }

    protected fun reportPath(
        f: PayrollFixture,
        from: String = "2026-08",
        until: String = "2026-12",
    ) = "/api/v1/companies/${f.company}/reports/payroll-costs?from=$from&until=$until"

    protected fun report(
        f: PayrollFixture,
        from: String = "2026-08",
        until: String = "2026-12",
        client: HttpClient = reportReader(f).client,
    ): JsonNode {
        val response = get(client, reportPath(f, from, until))
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        return payrollBody(response)
    }

    protected fun reconcile(f: PayrollFixture, report: JsonNode) {
        val base = "/api/v1/companies/${f.company}/payroll/payslips"
        val client = reportReader(f).client
        val slips =
            payrollBody(
                get(
                    client,
                    "$base?from=${report["from"].asString()}&until=${report["until"].asString()}&limit=200",
                )
            )["items"]
        assertEquals(slips.size().toLong(), report["totals"]["assessments"].asLong())
        val details =
            (0 until slips.size()).map { index ->
                val summary = slips[index]
                payrollBody(
                    get(
                        client,
                        "$base/${summary["id"].asString()}?revision=${summary["version"].asLong()}",
                    )
                )
            }
        val measures =
            listOf("employerCost", "taxableGross", "employeeDeductions", "withheld", "takeHome")
        fun amount(slip: JsonNode, field: String): BigDecimal =
            (if (field in setOf("employerCost", "employeeDeductions")) slip["calculation"][field]
                else slip["calculation"]["tax"][field])
                .asString()
                .toBigDecimal()
        for (field in measures) {
            assertEquals(
                0,
                details
                    .fold(BigDecimal.ZERO) { sum, slip -> sum + amount(slip, field) }
                    .compareTo(report["totals"][field].asString().toBigDecimal()),
                field,
            )
            for (month in report["months"]) {
                val selected = details.filter { it["summary"]["taxMonth"] == month["month"] }
                assertEquals(selected.size.toLong(), month["counts"]["assessments"].asLong())
                assertEquals(
                    0,
                    selected
                        .fold(BigDecimal.ZERO) { sum, slip -> sum + amount(slip, field) }
                        .compareTo(month["counts"][field].asString().toBigDecimal()),
                    "${month["month"]}:$field",
                )
            }
        }
    }
}
