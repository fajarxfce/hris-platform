package dev.fajar.hris

import java.util.UUID
import org.junit.jupiter.api.Assertions.*

abstract class DocumentRetentionApiFixture : DocumentValidationApiFixture() {
    protected fun retentionFixture(): Fixture {
        val f = fixture()
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'documents.retention')",
                f.company,
                f.actor.accountId,
            )
        return f.copy(
            actor =
                f.actor.copy(
                    permissions = f.actor.permissions + "documents.retention",
                    authenticatedAt = clock.instant(),
                )
        )
    }

    protected fun retentionPolicy(
        f: Fixture,
        id: UUID = UUID.randomUUID(),
        version: Long? = null,
        days: Int? = 30,
        classification: String = "PERSONAL",
        key: UUID = UUID.randomUUID(),
        reason: String = "Approved retention policy",
    ) =
        command(
            f.browser,
            "${f.path}/retention-policies",
            json.writeValueAsString(
                mapOf(
                    "policyId" to id,
                    "classification" to classification,
                    "expectedVersion" to version,
                    "retentionDays" to days,
                    "reason" to reason,
                )
            ),
            f.csrf,
            key,
        )

    protected fun retentionChange(
        f: Fixture,
        id: UUID,
        action: String,
        version: Long? = null,
        key: UUID = UUID.randomUUID(),
        reason: String = "Document lifecycle decision",
    ) =
        command(
            f.browser,
            "${f.path}/$id/retention/$action",
            json.writeValueAsString(mapOf("expectedVersion" to version, "reason" to reason)),
            f.csrf,
            key,
        )

    protected fun retention(f: Fixture, id: UUID): tools.jackson.databind.JsonNode {
        val result = get(f.browser, "${f.path}/$id/retention")
        assertEquals(200, result.statusCode(), result.body())
        return json.readTree(result.body())
    }

    protected fun inactiveDocument(f: Fixture): UUID {
        val doc = UUID.randomUUID()
        val revision = begin(f, document = doc)
        assertEquals(200, cancel(f, revision).statusCode())
        return doc
    }
}
