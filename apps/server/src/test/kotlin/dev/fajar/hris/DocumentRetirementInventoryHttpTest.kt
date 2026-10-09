package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.JobStep
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DocumentRetirementInventoryHttpTest : DocumentInventoryApiFixture() {
    @Test
    fun inventoryRecoversRetiredContentThatReappearsAfterAcknowledgedDeletion() {
        val initial = fixture()
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'documents.retention')",
                initial.company,
                initial.actor.accountId,
            )
        val doc = UUID.randomUUID()
        val rev = filled(initial, document = doc)
        assertTrue(run(initial, beginValidation(initial, rev)) is Result.Success)
        val version = revision(initial, rev).get("version").asLong()
        val policy =
            command(
                initial.browser,
                "${initial.path}/retention-policies",
                json.writeValueAsString(
                    mapOf(
                        "policyId" to UUID.randomUUID(),
                        "classification" to "PERSONAL",
                        "retentionDays" to 1,
                        "expectedVersion" to null,
                        "reason" to "Records policy",
                    )
                ),
                initial.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, policy.statusCode(), policy.body())
        assertEquals(
            200,
            command(
                    initial.browser,
                    "${initial.path}/$doc/retention/archive",
                    json.writeValueAsString(
                        mapOf("expectedVersion" to null, "reason" to "Archive content")
                    ),
                    initial.csrf,
                    UUID.randomUUID(),
                )
                .statusCode(),
        )
        clock.set(clock.instant().plusSeconds(86401))
        val f =
            initial.copy(
                csrf = login(initial.browser),
                actor =
                    initial.actor.copy(
                        authenticatedAt = clock.instant(),
                        permissions = initial.actor.permissions + "documents.retention",
                    ),
            )
        val retired =
            command(
                f.browser,
                "${f.path}/revisions/$rev/retire",
                json.writeValueAsString(
                    mapOf(
                        "expectedRevisionVersion" to version,
                        "expectedRetentionVersion" to 0,
                        "reason" to "Retention expired",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, retired.statusCode(), retired.body())
        val key = storageProbe.objects.keys.single { it.startsWith("${f.company}/$rev/") }
        val stored = storageProbe.objects.getValue(key)
        due(rev)
        assertEquals(Result.Success(1), cleanupCollector().execute(UUID.randomUUID()))
        assertEquals(0, garbage(rev))
        assertFalse(storageProbe.objects.containsKey(key))
        storageProbe.objects[key] = stored.copy(modifiedAt = clock.instant().minusSeconds(172800))
        val lease = beginInventory(f)
        assertEquals(Result.Success(JobStep(1, true)), runInventory(f, lease))
        assertEquals(1, garbage(rev))
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from document_inventory_recoveries where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals("RETIRED", revision(f, rev).get("status").asString())
    }
}
