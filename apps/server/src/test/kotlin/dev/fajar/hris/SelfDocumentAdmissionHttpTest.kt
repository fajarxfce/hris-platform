package dev.fajar.hris

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SelfDocumentAdmissionHttpTest : ClientPolicyApiFixture() {
    @Test
    fun documentDiscoveryHasIndependentModuleAdmissionAndNativeBuildRequirements() {
        val f = fixture()
        val owner = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Document client',password_hash from accounts where email='admin@example.test'",
                owner,
                "$owner@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                f.company,
                owner,
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'documents.self.read')",
                f.company,
                owner,
            )
        val employment = employee(f.browser, f.csrf, f.company, account = owner)
        val browser = client()
        val csrf = login(browser, "$owner@example.test")
        val paths =
            listOf(
                "${f.root}/documents/me/employments",
                "${f.root}/documents/me/evidence?employmentId=$employment",
            )
        assertEquals(
            200,
            save(f, changes = mapOf("disabledModules" to listOf("PEOPLE", "LEAVE", "WORKFORCE")))
                .statusCode(),
        )
        for (path in paths) {
            val response = get(browser, path)
            assertEquals(200, response.statusCode(), response.body())
        }
        assertEquals(200, save(f, 0, mapOf("disabledModules" to listOf("DOCUMENTS"))).statusCode())
        for (path in paths) {
            val denied = get(browser, path)
            failure(denied, 403, "company_module_disabled")
            assertEquals(
                "DOCUMENTS",
                json.readTree(denied.body())["parameters"]["module"].asString(),
            )
        }
        assertEquals(
            200,
            save(f, 1, mapOf("minimumBuilds" to mapOf("android" to 12, "ios" to 0, "web" to 0)))
                .statusCode(),
        )
        val exchange =
            command(
                browser,
                "/api/v1/auth/native/exchange",
                """{"deviceName":"Document fixture"}""",
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, exchange.statusCode(), exchange.body())
        val auth =
            "Authorization" to "Bearer ${json.readTree(exchange.body())["accessToken"].asString()}"
        for (path in paths) {
            failure(read(f, path, listOf(auth)), 403, "client_version_required")
            failure(
                read(
                    f,
                    path,
                    listOf(
                        auth,
                        "X-HRIS-Client-Platform" to "ANDROID",
                        "X-HRIS-Client-Build" to "11",
                    ),
                ),
                403,
                "client_update_required",
            )
            val accepted =
                read(
                    f,
                    path,
                    listOf(
                        auth,
                        "X-HRIS-Client-Platform" to "ANDROID",
                        "X-HRIS-Client-Build" to "12",
                    ),
                )
            assertEquals(200, accepted.statusCode(), accepted.body())
            assertEquals(owner.toString(), json.readTree(accepted.body())["accountId"].asString())
        }
    }
}
