package dev.fajar.hris

import dev.fajar.hris.core.domain.Actor
import java.net.http.HttpResponse
import java.util.UUID
import org.junit.jupiter.api.Assertions.*

abstract class SelfDocumentApiFixture : DocumentAssuranceApiFixture() {
    protected data class SelfFixture(val administrator: Fixture, val owner: Fixture)

    protected fun selfFixture(permission: String = "documents.self.read"): SelfFixture {
        val administrator = fixture()
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Document owner',password_hash from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                administrator.company,
                id,
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                administrator.company,
                id,
                permission,
            )
        val employment =
            employee(administrator.browser, administrator.csrf, administrator.company, account = id)
        val browser = client()
        val csrf = login(browser, "$id@example.test")
        val version =
            database()
                .queryForObject(
                    "select security_version from accounts where id=?",
                    Long::class.java,
                    id,
                )
        return SelfFixture(
            administrator.copy(employee = employment),
            Fixture(
                browser,
                csrf,
                Actor(
                    id,
                    administrator.company,
                    setOf(permission),
                    clock.instant(),
                    UUID.randomUUID(),
                    credentialVersion = version,
                ),
                employment,
            ),
        )
    }

    protected fun evidence(f: Fixture, query: String = ""): HttpResponse<String> =
        get(f.browser, "${f.path}/me/evidence?employmentId=${f.employee}$query")

    protected fun failure(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode(), response.body())
        assertEquals(code, json.readTree(response.body())["code"].asString())
    }
}
