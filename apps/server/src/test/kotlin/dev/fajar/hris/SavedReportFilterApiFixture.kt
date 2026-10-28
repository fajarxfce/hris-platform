package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.repositories.SavedReportFilterRepository
import dev.fajar.hris.reporting.domain.usecases.*
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import tools.jackson.databind.JsonNode

@Import(SavedReportFilterProbeConfiguration::class)
abstract class SavedReportFilterApiFixture : HeadcountReportApiFixture() {
    @Autowired protected lateinit var filterProbe: SavedReportFilterProbe
    @Autowired protected lateinit var filters: SavedReportFilterRepository
    @Autowired protected lateinit var saveFilter: SaveReportFilter
    @Autowired protected lateinit var deleteFilter: DeleteReportFilter
    @Autowired protected lateinit var getFilter: GetSavedReportFilter
    @Autowired protected lateinit var listFilters: ListSavedReportFilters
    @Autowired protected lateinit var transactions: TransactionRunner

    protected fun base(f: Fixture) = "/api/v1/companies/${f.company}/reports/saved-filters"

    protected fun definition(
        name: String = "Private selection",
        parameters: ReportFilterParameters = ReportFilterParameters(asOf = "2026-10-01"),
        kind: ReportKind = ReportKind.HEADCOUNT,
    ) = SavedReportFilterDefinition(name, kind, kind.definitionVersion, parameters)

    protected fun body(input: SavedReportFilterDefinition, version: Long? = null) =
        json.writeValueAsString(
            mapOf(
                "name" to input.name,
                "kind" to input.kind.name,
                "definitionVersion" to input.definitionVersion,
                "parameters" to input.parameters,
                "expectedVersion" to version,
            )
        )

    protected fun save(
        f: Fixture,
        id: UUID,
        version: Long? = null,
        operation: UUID = UUID.randomUUID(),
        input: SavedReportFilterDefinition = definition(),
    ) = command(f.browser, "${base(f)}/$id", body(input, version), f.csrf, operation, "PUT")

    protected fun delete(f: Fixture, id: UUID, version: Long, operation: UUID = UUID.randomUUID()) =
        command(
            f.browser,
            "${base(f)}/$id/delete",
            """{"expectedVersion":$version}""",
            f.csrf,
            operation,
        )

    protected fun ok(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    protected fun listed(f: Fixture): JsonNode {
        val response = get(f.browser, base(f))
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        return ok(response)["items"]
    }

    protected fun evidence(f: Fixture, id: UUID): List<Int> =
        listOf(
                "select count(*) from audit_entries where company_id=? and actor_id=? and resource_id=? and resource_type='report_filter'",
                "select count(*) from outbox_events where company_id=? and actor_id=? and resource_id=? and event_type in ('reporting.filter_saved','reporting.filter_deleted')",
                "select count(*) from operation_receipts where company_id=? and actor_id=? and resource_id=? and operation in ('reporting.filter_saved','reporting.filter_deleted')",
            )
            .map {
                requireNotNull(
                    database().queryForObject(it, Int::class.java, f.company, f.account, id)
                )
            }

    protected fun grant(f: Fixture, permission: String) {
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?) on conflict do nothing",
                f.company,
                f.account,
                permission,
            )
    }

    protected fun revoke(f: Fixture, permission: String) {
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                f.company,
                f.account,
                permission,
            )
    }

    protected fun <T> waiting(f: Fixture, stage: String, change: () -> Unit, invoke: () -> T): T {
        val gate = SavedReportFilterProbe.Gate(f.account, stage)
        filterProbe.gate.set(gate)
        return Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<T> { invoke() }
            try {
                assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
                change()
                gate.release.countDown()
                pending.get(15, TimeUnit.SECONDS)
            } finally {
                gate.release.countDown()
                filterProbe.gate.set(null)
            }
        }
    }

    @AfterEach fun resetFilterProbe() = filterProbe.clear()
}
