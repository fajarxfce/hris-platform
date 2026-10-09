package dev.fajar.hris

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GroupHeadcountHttpTest : GroupHeadcountApiFixture() {
    @Test
    fun oneSnapshotReturnsCompanyBucketsAndDistinctPersonsAcrossEmploymentHistory() {
        val f = fixture()
        val second = anotherCompany(f)
        val empty = anotherCompany(f)
        val original = create(f)
        create(f, mapOf("status" to "PROBATION"))
        val shared = UUID.randomUUID()
        database()
            .update(
                "insert into employments(company_id,id,person_id,employee_number) select ?,?,person_id,? from employments where company_id=? and id=?",
                second.company,
                shared,
                "G${shared.toString().take(8)}",
                f.company,
                original,
            )
        database()
            .update(
                "insert into employment_revisions(company_id,employment_id,revision,effective_from,contract_kind,start_date,end_date,status,actor_id,reason) values(?,?,0,'2026-01-01','FIXED_TERM','2026-01-01','2026-10-01','ACTIVE',?,'Shared person fixture')",
                second.company,
                shared,
                f.account,
            )
        val changed = create(second)
        assertEquals(
            200,
            revise(
                    f.browser,
                    f.csrf,
                    second.company,
                    changed,
                    0,
                    terms("2026-09-01", status = "SUSPENDED"),
                )
                .statusCode(),
        )
        assertEquals(
            200,
            revise(
                    f.browser,
                    f.csrf,
                    second.company,
                    changed,
                    1,
                    terms("2026-11-01", status = "ACTIVE"),
                )
                .statusCode(),
        )
        assertEquals(
            200,
            cancellation(f.browser, f.csrf, second.company, changed, 2, 2).statusCode(),
        )
        val unrelated = fixture()
        create(unrelated)
        val selected = listOf(empty.company, second.company, f.company)
        val response = group(f, selected)
        val totals = response["totals"]
        assertEquals("headcount.v1", response["definitionVersion"].asString())
        assertEquals("2026-10-01", response["asOf"].asString())
        assertEquals(4, totals["employments"].asLong())
        assertEquals(3, totals["persons"].asLong())
        assertEquals(2, totals["active"].asLong())
        assertEquals(1, totals["probation"].asLong())
        assertEquals(1, totals["suspended"].asLong())
        assertEquals(3, totals["permanent"].asLong())
        assertEquals(1, totals["fixedTerm"].asLong())
        val companies = response["companies"].iterator().asSequence().toList()
        assertEquals(
            selected.sorted(),
            companies.map { UUID.fromString(it["companyId"].asString()) },
        )
        val buckets =
            companies.associate { UUID.fromString(it["companyId"].asString()) to it["counts"] }
        assertEquals(2, buckets.getValue(f.company)["persons"].asLong())
        assertEquals(2, buckets.getValue(second.company)["persons"].asLong())
        assertEquals(0, buckets.getValue(empty.company)["employments"].asLong())
        assertEquals(3, group(f, selected, "2026-10-02")["totals"]["employments"].asLong())
        assertEquals(1, group(f, selected, "2026-11-01")["totals"]["suspended"].asLong())
        assertEquals(0, group(f, selected, "2025-12-31")["totals"]["employments"].asLong())
        assertFalse(response.toString().contains("legalName"))
        assertFalse(response.toString().contains("employeeNumber"))
    }

    @Test
    fun everySelectionRequiresCurrentSourcePermissionAndInvalidScopesNeverReachTheQuery() {
        val f = fixture()
        val other = fixture()
        val marker = IllegalStateException("must not read partial totals")
        reportProbe.failure.set(marker)
        failure(readGroup(f, listOf(f.company, other.company)), 403, "company_access_denied")
        failure(readGroup(f, listOf(f.company, UUID.randomUUID())), 403, "company_access_denied")
        failure(readGroup(f, listOf(f.company, f.company)), 422, "invalid_report_companies")
        failure(readGroup(f, List(33) { UUID.randomUUID() }), 422, "invalid_report_companies")
        failure(readGroup(f, listOf(f.company), date = "2101-01-01"), 422, "invalid_report_date")
        val selected = anotherCompany(f)
        for (permission in listOf("reports.read", "people.read")) {
            database()
                .update(
                    "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                    selected.company,
                    f.account,
                    permission,
                )
            failure(readGroup(f, listOf(f.company, selected.company)), 403, "access_denied")
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    selected.company,
                    f.account,
                    permission,
                )
        }
        assertSame(marker, reportProbe.failure.get())
        val failed = readGroup(f, listOf(f.company))
        failure(failed, 500, "database_failure")
        assertFalse(failed.body().contains("must not read"))
        assertEquals(0, group(f, listOf(f.company))["totals"]["employments"].asLong())
    }

    @Test
    fun globalReportsValidateBuildMetadataAgainstAuthenticatedTransportForEveryCompany() {
        val f = fixture()
        val second = anotherCompany(f)
        assertEquals(
            200,
            savePolicy(
                    second,
                    changes =
                        mapOf("minimumBuilds" to mapOf("android" to 12, "ios" to 7, "web" to 3)),
                )
                .statusCode(),
        )
        val selected = listOf(f.company, second.company)
        val exchange =
            command(
                f.browser,
                "/api/v1/auth/native/exchange",
                """{"deviceName":"Group report fixture"}""",
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, exchange.statusCode(), exchange.body())
        val bearer =
            "Authorization" to "Bearer ${json.readTree(exchange.body())["accessToken"].asString()}"
        failure(readGroup(f, selected), 403, "client_version_required")
        failure(readGroup(f, selected, listOf(bearer)), 403, "client_version_required")
        for ((auth, platform, minimum) in
            listOf(
                Triple(listOf(bearer), "ANDROID", 12),
                Triple(listOf(bearer), "IOS", 7),
                Triple(emptyList(), "WEB", 3),
            )) {
            val old =
                readGroup(
                    f,
                    selected,
                    auth +
                        listOf(
                            "X-HRIS-Client-Platform" to platform,
                            "X-HRIS-Client-Build" to (minimum - 1).toString(),
                        ),
                )
            failure(old, 403, "client_update_required")
            assertEquals(
                minimum.toString(),
                json.readTree(old.body())["parameters"]["minimumBuild"].asString(),
            )
            assertEquals(
                second.company.toString(),
                json.readTree(old.body())["parameters"]["companyId"].asString(),
            )
            val current =
                readGroup(
                    f,
                    selected,
                    auth +
                        listOf(
                            "X-HRIS-Client-Platform" to platform,
                            "X-HRIS-Client-Build" to minimum.toString(),
                        ),
                )
            assertEquals(200, current.statusCode(), current.body())
        }
        failure(
            readGroup(
                f,
                selected,
                listOf(bearer, "X-HRIS-Client-Platform" to "WEB", "X-HRIS-Client-Build" to "3"),
                extra = "&native=false",
            ),
            422,
            "invalid_client_version",
        )
        failure(
            readGroup(
                f,
                selected,
                listOf("X-HRIS-Client-Platform" to "ANDROID", "X-HRIS-Client-Build" to "12"),
                extra = "&native=true",
            ),
            422,
            "invalid_client_version",
        )
        failure(
            readGroup(f, selected, listOf("X-HRIS-Client-Platform" to "WEB")),
            400,
            "invalid_request",
        )
        failure(
            readGroup(
                f,
                selected,
                listOf(
                    "X-HRIS-Client-Platform" to "WEB",
                    "X-HRIS-Client-Build" to "3",
                    "X-HRIS-Client-Build" to "4",
                ),
            ),
            400,
            "invalid_request",
        )
    }

    @Test
    fun maintenanceAndDisabledReportingRejectTheWholeGroupWithSafeCompanyParameters() {
        val f = fixture()
        val second = anotherCompany(f)
        val selected = listOf(f.company, second.company)
        val end = clock.instant().plusSeconds(90)
        assertEquals(
            200,
            savePolicy(
                    second,
                    changes =
                        mapOf(
                            "maintenance" to
                                mapOf(
                                    "startsAt" to clock.instant().toString(),
                                    "endsAt" to end.toString(),
                                )
                        ),
                )
                .statusCode(),
        )
        val blocked = readGroup(f, selected)
        failure(blocked, 503, "company_maintenance")
        assertEquals(
            second.company.toString(),
            json.readTree(blocked.body())["parameters"]["companyId"].asString(),
        )
        assertEquals("90", blocked.headers().firstValue("Retry-After").orElseThrow())
        clock.set(end)
        assertEquals(0, group(f, selected)["totals"]["employments"].asLong())
        assertEquals(
            200,
            savePolicy(second, 0, mapOf("disabledModules" to listOf("REPORTING"))).statusCode(),
        )
        failure(readGroup(f, selected), 403, "company_module_disabled")
        assertEquals(0, group(f, listOf(f.company))["totals"]["employments"].asLong())
    }
}
