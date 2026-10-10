package dev.fajar.hris

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LifecycleAssigneeHttpTest : LifecycleApiFixture() {
    @Test
    fun managementLookupReturnsOnlyActiveEligibleCompanyReferencesWithoutAdministrativeData() {
        client().use { admin ->
            val csrf = login(admin)
            val company = company(admin, csrf)
            val foreign = company(admin, csrf)
            val operator = user()
            member(company, operator, setOf("people.lifecycle.manage"))
            val eligible = listOf(user(), user(), user())
            eligible.forEachIndexed { index, account ->
                member(
                    company,
                    account,
                    if (index == 0) setOf("people.lifecycle.perform")
                    else if (index == 1) setOf("people.lifecycle.manage")
                    else setOf("people.lifecycle.perform", "people.lifecycle.manage"),
                )
                database()
                    .update(
                        "update accounts set display_name=? where id=?",
                        "Lookup member $index",
                        account,
                    )
            }
            val disabledAccount = user()
            member(company, disabledAccount)
            database()
                .update(
                    "update accounts set display_name='Lookup disabled account', active=false where id=?",
                    disabledAccount,
                )
            val disabledMember = user()
            member(company, disabledMember)
            database()
                .update(
                    "update accounts set display_name='Lookup disabled member' where id=?",
                    disabledMember,
                )
            database()
                .update(
                    "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
                    company,
                    disabledMember,
                )
            val unqualified = user()
            member(company, unqualified, setOf("company.read", "people.lifecycle.read"))
            database()
                .update(
                    "update accounts set display_name='Lookup unqualified' where id=?",
                    unqualified,
                )
            val elsewhere = user()
            member(foreign, elsewhere)
            database()
                .update("update accounts set display_name='Lookup foreign' where id=?", elsewhere)
            client().use { browser ->
                login(browser, "$operator@example.test")
                val path = "${api(company)}/assignees?query=lookup&limit=2"
                val first = get(browser, path)
                assertEquals(200, first.statusCode(), first.body())
                val page = json.readTree(first.body())
                assertEquals(2, page["items"].size())
                val cursor = page["nextCursor"].asString()
                val second = get(browser, "$path&after=$cursor")
                assertEquals(200, second.statusCode(), second.body())
                val rest = json.readTree(second.body())
                assertEquals(1, rest["items"].size())
                assertTrue(rest["nextCursor"].isNull)
                val items =
                    page["items"].iterator().asSequence().toList() +
                        rest["items"].iterator().asSequence().toList()
                assertEquals(
                    eligible.map { it.toString() }.sorted(),
                    items.map { it["id"].asString() },
                )
                items.forEach { item ->
                    assertEquals(
                        setOf("id", "displayName"),
                        item.properties().map { it.key }.toSet(),
                    )
                    assertTrue(item["displayName"].asString().startsWith("Lookup member"))
                }
                assertEquals(items[1]["id"].asString(), cursor)
                assertFalse(first.body().contains("@example.test"))
                assertEquals(403, get(browser, "/api/v1/companies/$company/members").statusCode())
                assertEquals(403, get(browser, "${api(foreign)}/assignees").statusCode())
            }
        }
    }

    @Test
    fun lookupMatchesLiteralNamesWithBoundedPagesAndDoesNotSearchEmail() {
        client().use { browser ->
            val csrf = login(browser)
            val company = company(browser, csrf)
            val target = user()
            member(company, target)
            database()
                .update("update accounts set display_name=? where id=?", "Lookup%_Member", target)
            for (query in listOf("  Lookup%_  ", "%_", "lookup%_member")) {
                val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
                val response = get(browser, "${api(company)}/assignees?query=$encoded&limit=1")
                assertEquals(200, response.statusCode(), response.body())
                val result = json.readTree(response.body())
                assertEquals(1, result["items"].size())
                assertEquals(target.toString(), result["items"][0]["id"].asString())
                assertTrue(result["nextCursor"].isNull)
            }
            val emailQuery = get(browser, "${api(company)}/assignees?query=example.test")
            assertEquals(200, emailQuery.statusCode(), emailQuery.body())
            assertEquals(0, json.readTree(emailQuery.body())["items"].size())
            for (query in listOf("limit=0", "limit=201", "query=${"x".repeat(121)}")) {
                val invalid = get(browser, "${api(company)}/assignees?$query")
                assertEquals(422, invalid.statusCode(), invalid.body())
                assertEquals("invalid_page", json.readTree(invalid.body())["code"].asString())
            }
            assertEquals(400, get(browser, "${api(company)}/assignees?after=invalid").statusCode())
        }
    }

    @Test
    fun readingCasesOrPerformingTasksDoesNotAuthorizeAssigneeDiscovery() {
        client().use { admin ->
            val csrf = login(admin)
            val company = company(admin, csrf)
            for (permission in
                listOf(
                    "people.lifecycle.read",
                    "people.lifecycle.perform",
                    "identity.manage",
                    "people.manage",
                )) {
                val account = user()
                member(company, account, setOf(permission))
                client().use { browser ->
                    login(browser, "$account@example.test")
                    val denied = get(browser, "${api(company)}/assignees")
                    assertEquals(403, denied.statusCode(), denied.body())
                }
            }
        }
    }

    @Test
    fun aSelectedCandidateMustStillBeEligibleWhenAssignmentIsSubmitted() {
        client().use { browser ->
            val csrf = login(browser)
            val company = company(browser, csrf)
            val employee = employee(browser, csrf, company)
            val template = template(browser, csrf, company)
            val case = case(browser, csrf, company, employee, template)
            val target = user()
            member(company, target)
            database().update("update accounts set display_name='Lookup target' where id=?", target)
            val selected = get(browser, "${api(company)}/assignees?query=Lookup")
            assertEquals(200, selected.statusCode(), selected.body())
            assertEquals(
                target.toString(),
                json.readTree(selected.body())["items"][0]["id"].asString(),
            )
            database()
                .update(
                    "delete from membership_permissions where company_id=? and account_id=? and permission='people.lifecycle.perform'",
                    company,
                    target,
                )
            val refreshed = get(browser, "${api(company)}/assignees?query=Lookup")
            assertEquals(0, json.readTree(refreshed.body())["items"].size())
            val key = UUID.randomUUID()
            val rejected =
                command(
                    browser,
                    "${api(company)}/cases/$case/tasks/equipment/assignee",
                    json.writeValueAsString(
                        mapOf(
                            "expectedVersion" to 0,
                            "assigneeId" to target,
                            "reason" to "Assign selected member",
                        )
                    ),
                    csrf,
                    key,
                    "PUT",
                )
            assertEquals(422, rejected.statusCode(), rejected.body())
            assertEquals(
                "lifecycle_assignee_unavailable",
                json.readTree(rejected.body())["code"].asString(),
            )
            assertEquals(
                0,
                json.readTree(get(browser, "${api(company)}/cases/$case").body())["version"].asInt(),
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
        }
    }
}
