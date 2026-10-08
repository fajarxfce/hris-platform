package dev.fajar.hris

import java.net.CookieManager
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource

class IdentityHttpTest : ApiIntegrationTest() {
    @Test
    fun loginUsesServerSessionAndLogoutRevokesAccess() {
        val client = client()
        assertEquals(401, get(client, "/api/v1/me").statusCode())
        val csrf = login(client)
        val me = get(client, "/api/v1/me")
        assertEquals(200, me.statusCode(), me.body())
        val body = json.readTree(me.body())
        assertEquals("admin@example.test", body.get("account").get("email").asString())
        assertTrue(body.get("permissions").any { it.asString() == "companies.create" })
        assertFalse(me.body().contains("password"))
        val stored =
            JdbcTemplate(
                DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            )
        assertTrue(
            stored.queryForObject(
                "select count(*) from spring_session where principal_name=?",
                Int::class.java,
                body.get("account").get("id").asString(),
            )!! > 0
        )

        assertEquals(404, get(client, "/api/v1/does-not-exist").statusCode())
        assertEquals(204, post(client, "/api/v1/auth/logout", "{}", csrf).statusCode())
        assertEquals(401, get(client, "/api/v1/me").statusCode())
    }

    @Test
    fun loginRequiresCsrfAndDoesNotRevealAccountExistence() {
        val client = client()
        assertEquals(
            403,
            post(
                    client,
                    "/api/v1/auth/login",
                    """{"email":"admin@example.test","password":"wrong"}""",
                )
                .statusCode(),
        )
        val csrf = json.readTree(get(client, "/api/v1/auth/csrf").body()).get("token").asString()
        val existing =
            post(
                client,
                "/api/v1/auth/login",
                """{"email":"admin@example.test","password":"wrong"}""",
                csrf,
            )
        val missing =
            post(
                client,
                "/api/v1/auth/login",
                """{"email":"missing@example.test","password":"wrong"}""",
                csrf,
            )
        assertEquals(401, existing.statusCode())
        assertEquals(401, missing.statusCode())
        assertEquals(
            json.readTree(existing.body()).get("code"),
            json.readTree(missing.body()).get("code"),
        )
    }

    @Test
    fun companyPermissionsAreLoadedFreshAndRevocationTakesEffect() {
        val client = client()
        login(client)
        val account =
            UUID.fromString(
                json.readTree(get(client, "/api/v1/me").body()).get("account").get("id").asString()
            )
        val company = UUID.randomUUID()
        val another = UUID.randomUUID()
        val admin =
            JdbcTemplate(
                DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            )
        admin.update(
            "insert into companies(id,code,name,timezone) values(?,?,?,?)",
            company,
            company.toString().take(8),
            "Test Company",
            "Asia/Jakarta",
        )
        admin.update(
            "insert into companies(id,code,name,timezone) values(?,?,?,?)",
            another,
            another.toString().take(8),
            "Other Company",
            "Asia/Jakarta",
        )
        admin.update(
            "insert into company_memberships(company_id,account_id) values(?,?)",
            company,
            account,
        )
        admin.update(
            "insert into membership_permissions(company_id,account_id,permission) values(?,?,'people.read')",
            company,
            account,
        )

        assertEquals(200, get(client, "/api/v1/companies/$company/me/access").statusCode())
        assertEquals(403, get(client, "/api/v1/companies/$another/me/access").statusCode())
        val directory = json.readTree(get(client, "/api/v1/me").body()).get("companies")
        assertTrue(directory.any { it.get("id").asString() == company.toString() })
        assertFalse(directory.any { it.get("id").asString() == another.toString() })

        admin.update(
            "update company_memberships set active=false where company_id=? and account_id=?",
            company,
            account,
        )
        assertEquals(403, get(client, "/api/v1/companies/$company/me/access").statusCode())
        val refreshed = json.readTree(get(client, "/api/v1/me").body()).get("companies")
        assertFalse(refreshed.any { it.get("id").asString() == company.toString() })
    }

    @Test
    fun successfulLoginRotatesSessionAndCsrfTokens() {
        val client = client()
        val firstToken =
            json.readTree(get(client, "/api/v1/auth/csrf").body()).get("token").asString()
        val cookies = client.cookieHandler().orElseThrow() as CookieManager
        val oldSession = cookies.cookieStore.cookies.first { it.name == "SESSION" }.value
        val response =
            post(
                client,
                "/api/v1/auth/login",
                """{"email":"admin@example.test","password":"Testing-password-123!"}""",
                firstToken,
            )
        assertEquals(200, response.statusCode(), response.body())
        val newSession = cookies.cookieStore.cookies.first { it.name == "SESSION" }.value
        assertNotEquals(oldSession, newSession)
        assertEquals(403, post(client, "/api/v1/auth/logout", "{}", firstToken).statusCode())
        val newToken =
            json.readTree(get(client, "/api/v1/auth/csrf").body()).get("token").asString()
        assertEquals(204, post(client, "/api/v1/auth/logout", "{}", newToken).statusCode())
    }
}
