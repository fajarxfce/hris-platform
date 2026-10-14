package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.data.datasources.DocumentDataSource
import java.net.URLEncoder
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.JsonNode

@TestPropertySource(properties = [MOBILE_SYNC_TEST_KEYS])
class DocumentEvidenceSyncHttpTest : SelfDocumentApiFixture() {
    @Autowired private lateinit var source: DocumentDataSource

    private fun snapshot(owner: Fixture, selection: String = "DOCUMENT_EVIDENCE") =
        get(
            owner.browser,
            "/api/v1/companies/${owner.company}/sync/bootstrap?collections=$selection",
        )

    private fun delta(owner: Fixture, cursor: String) =
        get(
            owner.browser,
            "/api/v1/companies/${owner.company}/sync/changes?collections=DOCUMENT_EVIDENCE&cursor=${URLEncoder.encode(cursor, Charsets.UTF_8)}",
        )

    private fun ok(response: java.net.http.HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    private fun publish() {
        val worker = mobileSyncTestWorker(postgres.jdbcUrl, database())
        repeat(30) {
            val result = worker.maintain.execute()
            assertTrue(result is Result.Success, result.toString())
            val counts = (result as Result.Success).value
            if (counts.published == 0 && counts.pruned == 0) return
        }
        fail<Unit>("Fixture publication exceeded its finite budget")
    }

    @Test
    fun bootstrapAndChangesExposeOwnedPublishedMetadataWithIndependentSelection() {
        val (admin, owner) = selfFixture()
        val empty = ok(snapshot(owner))
        assertEquals(
            listOf("DOCUMENT_EVIDENCE"),
            empty["collections"].iterator().asSequence().map { it.asString() }.toList(),
        )
        assertEquals(0, empty["items"].size())
        val cursor = empty["changesCursor"].asString()
        val document = UUID.randomUUID()
        val accepted = ready(admin, document)
        val pendingId = UUID.randomUUID()
        begin(admin, document = pendingId)
        val other = employee(admin.browser, admin.csrf, owner.company)
        ready(admin.copy(employee = other), UUID.randomUUID())
        val hidden = UUID.randomUUID()
        val hiddenRevision = UUID.randomUUID()
        assertEquals(
            200,
            start(
                    admin,
                    uploadInput(admin, pdf, hidden, hiddenRevision, classification = "HR_ONLY"),
                )
                .statusCode(),
        )
        assertEquals(200, upload(admin, hiddenRevision, pdf).statusCode())
        assertTrue(run(admin, beginValidation(admin, hiddenRevision)) is Result.Success)
        val initial = ok(snapshot(owner))
        assertEquals(document.toString(), initial["items"].single()["id"].asString())
        assertTrue(ok(delta(owner, cursor))["pendingPublication"].asBoolean())
        publish()
        val changes = ok(delta(owner, cursor))
        assertEquals(1, changes["items"].size())
        assertEquals(document.toString(), changes["items"].single()["id"].asString())
        assertEquals("UPSERT", changes["items"].single()["operation"].asString())
        assertFalse(changes["pendingPublication"].asBoolean())
        assertFalse(changes.toString().contains("fileName"))
        val record = ok(get(owner.browser, "${owner.path}/me/evidence/$document"))
        assertEquals(accepted.toString(), record["evidence"]["revisionId"].asString())
        assertEquals(record["version"].asLong(), changes["items"].single()["version"].asLong())
        failure(snapshot(admin), 403, "sync_access_denied")
        failure(
            get(owner.browser, "/api/v1/companies/${owner.company}/sync/bootstrap"),
            403,
            "sync_access_denied",
        )
        val foreign = selfFixture().owner
        failure(delta(foreign, changes["cursor"].asString()), 422, "invalid_sync_cursor")
    }

    @Test
    fun headerUpdatesAndRetirementPublishVersionedChangesWithAtomicRollback() {
        val (admin, owner) = selfFixture()
        val document = UUID.randomUUID()
        val revision = ready(admin, document)
        publish()
        val cursor = ok(snapshot(owner))["changesCursor"].asString()
        val before = ok(get(owner.browser, "${owner.path}/me/evidence/$document"))
        val replacement = filled(admin, document = document, version = before["version"].asLong())
        publish()
        val updated = ok(delta(owner, cursor))
        assertEquals("UPSERT", updated["items"].single()["operation"].asString())
        assertEquals(
            revision.toString(),
            ok(get(owner.browser, "${owner.path}/me/evidence/$document"))["evidence"]["revisionId"]
                .asString(),
        )
        val cancelled = cancel(admin, replacement, revision(admin, replacement)["version"].asLong())
        assertEquals(200, cancelled.statusCode(), cancelled.body())
        val rollback =
            transactions.run(admin.actor) {
                val current = (documents.find(admin.company, document) as Result.Success).value!!
                // A replacement first advances its header. A later failure must also
                // roll back the invalidation captured by that valid header transition.
                assertNotNull(source.incrementRevision(admin.company, document, current.version))
                Result.Failed(Failure(FailureKind.CONFLICT, "fixture_rollback"))
            }
        assertEquals("fixture_rollback", (rollback as Result.Failed).failure.code)
        publish()
        assertEquals(0, ok(delta(owner, updated["cursor"].asString()))["items"].size())
        assertEquals(
            revision.toString(),
            ok(get(owner.browser, "${owner.path}/me/evidence/$document"))["evidence"]["revisionId"]
                .asString(),
        )

        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'documents.retention')",
                admin.company,
                admin.actor.accountId,
            )
        ok(
            command(
                admin.browser,
                "${admin.path}/retention-policies",
                json.writeValueAsString(
                    mapOf(
                        "policyId" to UUID.randomUUID(),
                        "classification" to "PERSONAL",
                        "expectedVersion" to null,
                        "retentionDays" to 1,
                        "reason" to "Fixture retention",
                    )
                ),
                admin.csrf,
                UUID.randomUUID(),
            )
        )
        ok(
            command(
                admin.browser,
                "${admin.path}/$document/retention/archive",
                """{"expectedVersion":null,"reason":"Fixture archive"}""",
                admin.csrf,
                UUID.randomUUID(),
            )
        )
        clock.set(clock.instant().plusSeconds(86401))
        val csrf = login(admin.browser)
        login(owner.browser, "${owner.actor.accountId}@example.test")
        ok(
            command(
                admin.browser,
                "${admin.path}/revisions/$revision/retire",
                json.writeValueAsString(
                    mapOf(
                        "expectedRevisionVersion" to revision(admin, revision)["version"].asLong(),
                        "expectedRetentionVersion" to 0,
                        "reason" to "Fixture elapsed retention",
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        )
        publish()
        val removed = ok(delta(owner, updated["cursor"].asString()))
        assertEquals("DELETE", removed["items"].single()["operation"].asString())
        assertTrue(
            removed["items"].single()["version"].asLong() >
                updated["items"].single()["version"].asLong()
        )
        assertEquals(0, ok(snapshot(owner))["items"].size())
        failure(
            get(owner.browser, "${owner.path}/me/evidence/$document"),
            404,
            "document_evidence_not_found",
        )
        assertEquals(removed["items"], ok(delta(owner, updated["cursor"].asString()))["items"])
    }

    @Test
    fun documentModuleAndPermissionRevocationGateBothFeedAndCanonicalReads() {
        val (admin, owner) = selfFixture()
        val id = UUID.randomUUID()
        ready(admin, id)
        val cursor = ok(snapshot(owner))["changesCursor"].asString()
        val policyPath = "/api/v1/companies/${owner.company}/settings/client-policy"
        fun policy(disabled: List<String>, version: Long?) =
            command(
                admin.browser,
                policyPath,
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to version,
                        "activateAt" to null,
                        "disabledModules" to disabled,
                        "minimumBuilds" to mapOf("android" to 0, "ios" to 0, "web" to 0),
                        "maintenance" to null,
                        "reason" to "Fixture capability scope",
                    )
                ),
                admin.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        ok(policy(listOf("PEOPLE", "LEAVE", "WORKFORCE"), null))
        ok(snapshot(owner))
        ok(get(owner.browser, "${owner.path}/me/evidence/$id"))
        ok(policy(listOf("DOCUMENTS"), 0))
        failure(snapshot(owner), 403, "company_module_disabled")
        failure(delta(owner, cursor), 403, "company_module_disabled")
        failure(get(owner.browser, "${owner.path}/me/evidence/$id"), 403, "company_module_disabled")
        ok(policy(emptyList(), 1))
        ok(delta(owner, cursor))
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=?",
                owner.company,
                owner.actor.accountId,
            )
        failure(snapshot(owner), 403, "sync_access_denied")
        failure(delta(owner, cursor), 403, "sync_access_denied")
        failure(get(owner.browser, "${owner.path}/me/evidence/$id"), 403, "access_denied")
    }
}
