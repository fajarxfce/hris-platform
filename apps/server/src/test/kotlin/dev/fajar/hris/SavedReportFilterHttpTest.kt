package dev.fajar.hris

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.*
import java.util.UUID
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class SavedReportFilterHttpTest : SavedReportFilterApiFixture() {
    @Autowired private lateinit var runtime: DSLContext
    @Autowired private lateinit var reads: CompanyReadTransactionRunner

    @Test
    fun personalSelectionsSupportVersionedEditsReceiptReplayAndErasedDeletionWithoutResurrection() {
        val f = fixture()
        val id = UUID.randomUUID()
        val operation = UUID.randomUUID()
        val input = definition("  Monthly staffing  ")
        val first = ok(save(f, id, operation = operation, input = input))
        assertEquals(0, first["version"].asLong())
        assertEquals(first, ok(save(f, id, operation = operation, input = input)))
        failure(
            save(f, id, operation = operation, input = definition("Different selection")),
            409,
            "operation_payload_mismatch",
        )
        val loaded = ok(get(f.browser, "${base(f)}/$id"))
        assertEquals("Monthly staffing", loaded["name"].asString())
        assertEquals(f.company.toString(), loaded["parameters"]["companies"][0].asString())
        assertNull(loaded.get("accountId"))
        assertEquals(1, listed(f).size())
        failure(save(f, id), 409, "stale_version")
        val second =
            ok(
                save(
                    f,
                    id,
                    0,
                    input = definition("New date", ReportFilterParameters(asOf = "2026-10-02")),
                )
            )
        assertEquals(1, second["version"].asLong())
        assertEquals(first, ok(save(f, id, operation = operation, input = input)))
        failure(delete(f, id, 0), 409, "stale_version")
        val deletion = UUID.randomUUID()
        val deleted = ok(delete(f, id, 1, deletion))
        assertEquals(2, deleted["version"].asLong())
        assertEquals(deleted, ok(delete(f, id, 1, deletion)))
        assertEquals(listOf(3, 3, 3), evidence(f, id))
        assertEquals(0, listed(f).size())
        failure(get(f.browser, "${base(f)}/$id"), 404, "report_filter_not_found")
        failure(save(f, id, 2), 404, "report_filter_not_found")
        val stored =
            (transactions.run(f.actor) { filters.find(f.company, f.account, id) } as Result.Success)
                .value!!
        assertNull(stored.definition)
        assertEquals(2, stored.version)
        assertEquals(deleted, ok(delete(f, id, 2)))
        assertEquals(listOf(3, 3, 4), evidence(f, id))
        val restored =
            transactions.run(f.actor) {
                safeDatabaseCall {
                    runtime.execute(
                        """update saved_report_filters set version=version+1,name='Restored',kind='HEADCOUNT',
                    definition_version='headcount.v1',as_of=date '2026-10-01',selected_companies=?::uuid[]
                    where company_id=? and account_id=? and id=?""",
                        arrayOf(f.company),
                        f.company,
                        f.account,
                        id,
                    )
                }
            }
        assertEquals("data_conflict", (restored as Result.Failed).failure.code)
        val raw =
            database()
                .queryForMap(
                    "select name,kind,definition_version,as_of,range_from,range_until,status,cardinality(selected_companies) as companies from saved_report_filters where company_id=? and account_id=? and id=?",
                    f.company,
                    f.account,
                    id,
                )
        assertTrue(raw.filterKeys { it != "companies" }.values.all { it == null })
        assertEquals(0, raw["companies"])
        val journal =
            database()
                .queryForList(
                    "select details::text from audit_entries where company_id=? and actor_id=? and resource_id=?",
                    String::class.java,
                    f.company,
                    f.account,
                    id,
                )
                .joinToString()
        assertFalse(journal.contains("staffing"))
        assertFalse(journal.contains("2026-10-02"))
        val physicalDelete =
            transactions.run(f.actor) {
                safeDatabaseCall {
                    runtime.execute(
                        "delete from saved_report_filters where company_id=? and account_id=? and id=?",
                        f.company,
                        f.account,
                        id,
                    )
                }
            }
        assertEquals("access_denied", (physicalDelete as Result.Failed).failure.code)
    }

    @Test
    fun ownerAndCompanyRlsKeepIdenticalFilterIdsIndependentAndGroupReadScopeDoesNotExposeSelections() {
        val first = fixture()
        val second = fixture()
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                first.company,
                second.account,
            )
        val sameCompany =
            second.copy(
                company = first.company,
                actor = second.actor.copy(companyId = first.company),
            )
        grant(sameCompany, "reports.read")
        grant(sameCompany, "people.read")
        val id = UUID.randomUUID()
        ok(save(first, id, input = definition("First owner")))
        failure(get(second.browser, "${base(sameCompany)}/$id"), 404, "report_filter_not_found")
        failure(delete(sameCompany, id, 0), 404, "report_filter_not_found")
        assertEquals(
            Result.Success(null),
            transactions.run(sameCompany.actor) { filters.find(first.company, first.account, id) },
        )
        assertEquals(
            Result.Success(emptyList<SavedReportFilter>()),
            reads.run(first.actor.copy(companyId = null), setOf(first.company)) {
                filters.list(first.company, first.account)
            },
        )
        ok(save(sameCompany, id, input = definition("Second owner")))
        assertEquals("First owner", listed(first)[0]["name"].asString())
        assertEquals("Second owner", listed(sameCompany)[0]["name"].asString())
        assertEquals(0, listed(second).size())
        val foreign =
            (transactions.run(first.actor) { filters.find(first.company, first.account, id) }
                    as Result.Success)
                .value!!
        val forbidden =
            transactions.run(sameCompany.actor) {
                filters.save(foreign.copy(id = UUID.randomUUID()), null)
            }
        assertEquals("access_denied", (forbidden as Result.Failed).failure.code)
    }

    @Test
    fun retainingASelectionNeverRestoresSourceAuthorityAndOwnersCanDeleteAfterSourceRevocation() {
        val f = fixture()
        val other = fixture()
        val id = UUID.randomUUID()
        ok(
            save(
                f,
                id,
                input =
                    definition(
                        parameters =
                            ReportFilterParameters(
                                asOf = "2026-10-01",
                                companies = listOf(other.company),
                            )
                    ),
            )
        )
        assertEquals(
            other.company.toString(),
            listed(f)[0]["parameters"]["companies"][0].asString(),
        )
        val report =
            get(f.browser, "/api/v1/reports/headcount?companies=${other.company}&asOf=2026-10-01")
        assertEquals(403, report.statusCode(), report.body())
        revoke(f, "people.read")
        assertEquals(1, listed(f).size())
        assertEquals(200, get(f.browser, "${base(f)}/$id").statusCode())
        failure(save(f, id, 0), 403, "access_denied")
        ok(delete(f, id, 0))
        revoke(f, "reports.read")
        failure(get(f.browser, base(f)), 403, "access_denied")
        failure(delete(f, id, 1), 403, "access_denied")
    }

    @Test
    fun boundedTypedSelectionsRejectUnknownFieldsUnsupportedVersionsAndIrrelevantFilters() {
        val f = fixture()
        val id = UUID.randomUUID()
        for (input in
            listOf(
                definition(""),
                definition("x".repeat(81)),
                definition().copy(definitionVersion = "headcount.v2"),
                definition(parameters = ReportFilterParameters(asOf = "2026-02-29")),
                definition(
                    parameters =
                        ReportFilterParameters(
                            asOf = "2026-10-01",
                            companies = List(33) { UUID.randomUUID() },
                        )
                ),
            )) {
            assertEquals(422, save(f, id, input = input).statusCode())
        }
        val unknown =
            body(definition()).replace("\"parameters\":{", "\"parameters\":{\"url\":\"/private\",")
        assertNotEquals(body(definition()), unknown)
        assertEquals(
            400,
            command(f.browser, "${base(f)}/$id", unknown, f.csrf, UUID.randomUUID(), "PUT")
                .statusCode(),
        )
        assertEquals(0, listed(f).size())
        grant(f, "payroll.read")
        for (kind in ReportKind.entries.filter { it != ReportKind.HEADCOUNT }) {
            val parameters =
                if (kind in setOf(ReportKind.ATTENDANCE_CLOSINGS, ReportKind.PAYROLL_COSTS))
                    ReportFilterParameters(from = "2026-01", until = "2026-12")
                else
                    ReportFilterParameters(
                        from = "2026-01-01",
                        until = "2026-01-31",
                        status = if (kind == ReportKind.OVERTIME) "APPROVED" else null,
                    )
            ok(save(f, UUID.randomUUID(), input = definition(kind.name, parameters, kind)))
        }
        assertEquals(6, listed(f).size())
        assertEquals(listOf(0, 0, 0), evidence(f, id))
    }
}
