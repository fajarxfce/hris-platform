package dev.fajar.hris

import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClientPolicyHttpTest : ClientPolicyApiFixture() {
    @Test
    fun defaultsScheduledActivationAndImmediateSupersessionRetainHistoryAndOriginalReceipts() {
        val f = fixture()
        val other = company(f.browser, f.csrf)
        val initial = json.readTree(get(f.browser, "${f.root}/client-policy").body())
        assertTrue(initial["version"].isNull)
        assertEquals(8, initial["enabledModules"].size())
        assertEquals(
            200,
            get(f.browser, "${f.root}/employees?asOf=2026-10-01&limit=1").statusCode(),
        )
        assertTrue(json.readTree(get(f.browser, f.settings).body())["latest"].isNull)
        val activate = clock.instant().plusSeconds(30)
        val key = UUID.randomUUID()
        val changes = mapOf("activateAt" to activate, "disabledModules" to listOf("PEOPLE"))
        val saved = save(f, changes = changes, key = key)
        assertEquals(200, saved.statusCode(), saved.body())
        assertEquals(
            activate.toString(),
            json.readTree(get(f.browser, "${f.root}/client-policy").body())["validUntil"].asString(),
        )
        assertEquals(200, get(f.browser, "${f.root}/employees?asOf=2026-10-01").statusCode())
        clock.set(activate)
        val disabled = get(f.browser, "${f.root}/employees?asOf=2026-10-01")
        failure(disabled, 403, "company_module_disabled")
        assertEquals("PEOPLE", json.readTree(disabled.body())["parameters"]["module"].asString())
        assertEquals(
            200,
            get(f.browser, "/api/v1/companies/$other/employees?asOf=2026-10-01").statusCode(),
        )
        assertEquals(200, get(f.browser, f.settings).statusCode())
        assertEquals(200, save(f, 0).statusCode())
        assertEquals(200, get(f.browser, "${f.root}/employees?asOf=2026-10-01").statusCode())
        assertEquals(saved.body(), save(f, changes = changes, key = key).body())
        assertEquals(2, versions(f))
        assertEquals(
            0,
            json.readTree(get(f.browser, "${f.settings}/revisions/0").body())["version"].asInt(),
        )
        failure(
            get(f.browser, "${f.settings}/revisions/9"),
            404,
            "client_policy_revision_not_found",
        )
    }

    @Test
    fun nativeAndBrowserBuildGatesReturnLocalizableCodesAndKeepRecoveryAvailable() {
        val f = fixture()
        assertEquals(
            200,
            save(
                    f,
                    changes =
                        mapOf("minimumBuilds" to mapOf("android" to 12, "ios" to 7, "web" to 0)),
                )
                .statusCode(),
        )
        val exchange =
            command(
                f.browser,
                "/api/v1/auth/native/exchange",
                """{"deviceName":"Policy fixture"}""",
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, exchange.statusCode(), exchange.body())
        val auth =
            "Authorization" to "Bearer ${json.readTree(exchange.body())["accessToken"].asString()}"
        val path = "${f.root}/employees?asOf=2026-10-01"
        failure(read(f, path, listOf(auth)), 403, "client_version_required")
        val old =
            read(
                f,
                path,
                listOf(auth, "X-HRIS-Client-Platform" to "ANDROID", "X-HRIS-Client-Build" to "11"),
            )
        failure(old, 403, "client_update_required")
        assertEquals("12", json.readTree(old.body())["parameters"]["minimumBuild"].asString())
        for ((platform, version) in listOf("ANDROID" to "12", "IOS" to "7")) assertEquals(
            200,
            read(
                    f,
                    path,
                    listOf(
                        auth,
                        "X-HRIS-Client-Platform" to platform,
                        "X-HRIS-Client-Build" to version,
                    ),
                )
                .statusCode(),
        )
        failure(
            read(
                f,
                path,
                listOf(auth, "X-HRIS-Client-Platform" to "WEB", "X-HRIS-Client-Build" to "12"),
            ),
            422,
            "invalid_client_version",
        )
        assertEquals(200, read(f, "${f.root}/client-policy", listOf(auth)).statusCode())
        assertEquals(200, get(f.browser, path).statusCode())
        failure(
            read(
                f,
                path,
                listOf("X-HRIS-Client-Platform" to "ANDROID", "X-HRIS-Client-Build" to "12"),
            ),
            422,
            "invalid_client_version",
        )
        failure(read(f, path, listOf("X-HRIS-Client-Platform" to "WEB")), 400, "invalid_request")
        failure(
            read(
                f,
                path,
                listOf(
                    "X-HRIS-Client-Platform" to "WEB",
                    "X-HRIS-Client-Build" to "1",
                    "X-HRIS-Client-Build" to "2",
                ),
            ),
            400,
            "invalid_request",
        )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='people.read'",
                f.company,
                f.account,
            )
        failure(
            read(
                f,
                path,
                listOf(auth, "X-HRIS-Client-Platform" to "ANDROID", "X-HRIS-Client-Build" to "12"),
            ),
            403,
            "access_denied",
        )
    }

    @Test
    fun maintenanceHasAnExplicitEndAndDoesNotDisablePolicyOrIdentityRecovery() {
        val f = fixture()
        val end = clock.instant().plusSeconds(120)
        assertEquals(
            200,
            save(
                    f,
                    changes =
                        mapOf(
                            "maintenance" to mapOf("startsAt" to clock.instant(), "endsAt" to end)
                        ),
                )
                .statusCode(),
        )
        val denied = get(f.browser, "${f.root}/employees?asOf=2026-10-01")
        failure(denied, 503, "company_maintenance")
        assertEquals("120", denied.headers().firstValue("Retry-After").orElseThrow())
        assertEquals(120, json.readTree(denied.body())["retryAfterSeconds"].asInt())
        assertEquals(
            end.toString(),
            json.readTree(denied.body())["parameters"]["endsAt"].asString(),
        )
        assertTrue(
            json
                .readTree(get(f.browser, "${f.root}/client-policy").body())["maintenanceActive"]
                .asBoolean()
        )
        assertEquals(200, get(f.browser, f.settings).statusCode())
        assertEquals(200, get(f.browser, "/api/v1/me").statusCode())
        assertEquals(200, get(f.browser, f.root).statusCode())
        clock.set(end)
        assertEquals(200, get(f.browser, "${f.root}/employees?asOf=2026-10-01").statusCode())
        assertFalse(
            json
                .readTree(get(f.browser, "${f.root}/client-policy").body())["maintenanceActive"]
                .asBoolean()
        )
    }

    @Test
    fun concurrentWritesSerializeWithOneVersionAndFailuresDoNotConsumeReceipts() {
        val f = fixture()
        val key = UUID.randomUUID()
        Executors.newFixedThreadPool(2).use { pool ->
            val start = CountDownLatch(1)
            val futures =
                (1..2).map {
                    pool.submit<java.net.http.HttpResponse<String>> {
                        check(start.await(5, TimeUnit.SECONDS))
                        save(f, key = key)
                    }
                }
            start.countDown()
            val results = futures.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(listOf(200, 200), results.map { it.statusCode() })
            assertEquals(results[0].body(), results[1].body())
            val edits =
                listOf("PEOPLE", "LEAVE")
                    .map { module ->
                        pool.submit<java.net.http.HttpResponse<String>> {
                            save(f, 0, mapOf("disabledModules" to listOf(module)))
                        }
                    }
                    .map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(listOf(200, 409), edits.map { it.statusCode() }.sorted())
        }
        assertEquals(2, versions(f))
        failure(
            save(f, changes = mapOf("reason" to "Different intent"), key = key),
            409,
            "operation_payload_mismatch",
        )
        val fresh = UUID.randomUUID()
        failure(
            save(
                f,
                1,
                mapOf("minimumBuilds" to mapOf("android" to -1, "ios" to 0, "web" to 0)),
                fresh,
            ),
            422,
            "invalid_client_policy",
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where operation_id=?",
                    Int::class.java,
                    fresh,
                ),
        )
        assertEquals(200, save(f, 1, key = fresh).statusCode())
    }

    @Test
    fun failedAuditRollsBackPolicyHeadRevisionReceiptAndEvent() {
        val f = fixture()
        val key = UUID.randomUUID()
        database()
            .execute(
                """CREATE FUNCTION block_client_policy_audit() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'} BEGIN IF NEW.resource_id='${f.company}'::uuid AND NEW.action='administration.client_policy_saved' THEN RAISE EXCEPTION 'fixture'; END IF; RETURN NEW; END ${'$'}${'$'}"""
            )
        database()
            .execute(
                "CREATE TRIGGER block_client_policy_audit BEFORE INSERT ON audit_entries FOR EACH ROW EXECUTE FUNCTION block_client_policy_audit()"
            )
        try {
            assertTrue(save(f, key = key).statusCode() >= 400)
            assertEquals(0, versions(f))
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from company_client_policy_heads where company_id=?",
                        Int::class.java,
                        f.company,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from operation_receipts where operation_id=?",
                        Int::class.java,
                        key,
                    ),
            )
        } finally {
            database().execute("DROP TRIGGER block_client_policy_audit ON audit_entries")
            database().execute("DROP FUNCTION block_client_policy_audit()")
        }
        assertEquals(200, save(f, key = key).statusCode())
        assertEquals(1, versions(f))
    }

    @Test
    fun disabledAdmissionPreservesAnOfflineOperationUntilTheCapabilityIsRestored() {
        val f = fixture()
        val employee = UUID.randomUUID()
        val key = UUID.randomUUID()
        val input =
            json.writeValueAsString(
                mapOf(
                    "id" to employee,
                    "employeeNumber" to "E${employee.toString().take(8)}",
                    "person" to
                        mapOf(
                            "id" to UUID.randomUUID(),
                            "legalName" to "Example employee",
                            "nationality" to "ID",
                        ),
                    "terms" to terms(),
                    "reason" to "Onboarding",
                )
            )
        assertEquals(
            200,
            save(f, changes = mapOf("disabledModules" to listOf("PEOPLE"))).statusCode(),
        )
        failure(
            command(f.browser, "${f.root}/employees", input, f.csrf, key),
            403,
            "company_module_disabled",
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where operation_id=?",
                    Int::class.java,
                    key,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from employments where company_id=? and id=?",
                    Int::class.java,
                    f.company,
                    employee,
                ),
        )
        assertEquals(200, save(f, 0).statusCode())
        val accepted = command(f.browser, "${f.root}/employees", input, f.csrf, key)
        assertEquals(200, accepted.statusCode(), accepted.body())
        assertEquals(200, save(f, 1, mapOf("disabledModules" to listOf("PEOPLE"))).statusCode())
        failure(
            command(f.browser, "${f.root}/employees", input, f.csrf, key),
            403,
            "company_module_disabled",
        )
        assertEquals(200, save(f, 2).statusCode())
        assertEquals(
            accepted.body(),
            command(f.browser, "${f.root}/employees", input, f.csrf, key).body(),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from employments where company_id=? and id=?",
                    Int::class.java,
                    f.company,
                    employee,
                ),
        )
    }

    @Test
    fun aNewImmediateRevisionCancelsPreviouslyScheduledPolicyWithoutRewritingIt() {
        val f = fixture()
        val future = clock.instant().plusSeconds(60)
        assertEquals(
            200,
            save(f, changes = mapOf("activateAt" to future, "disabledModules" to listOf("PEOPLE")))
                .statusCode(),
        )
        assertEquals(200, save(f, 0).statusCode())
        clock.set(future.plusSeconds(1))
        assertEquals(200, get(f.browser, "${f.root}/employees?asOf=2026-10-01").statusCode())
        assertEquals(
            1,
            json.readTree(get(f.browser, "${f.root}/client-policy").body())["version"].asInt(),
        )
        assertEquals(2, versions(f))
        failure(save(f, 1, mapOf("activateAt" to future)), 409, "client_policy_activation_expired")
    }
}
