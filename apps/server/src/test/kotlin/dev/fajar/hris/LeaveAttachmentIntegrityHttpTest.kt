package dev.fajar.hris

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException

class LeaveAttachmentIntegrityHttpTest : LeaveAttachmentApiFixture() {
    @Test
    fun anOmittedRegistryEntryCannotCommitARequestOrConsumeItsRetryKey() {
        val f = preparedLeave()
        val revision = evidence(f)
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        attachmentProbe.omitReferences = true
        assertEquals(409, submitEvidence(f, id, listOf(revision), key).statusCode())
        assertEquals(0, referenceCount(f, id))
        assertEquals("0", balance(f).get("reservedDays").asString())
        attachmentProbe.clear()
        body(submitEvidence(f, id, listOf(revision), key))
        assertEquals(1, referenceCount(f, id))
    }

    @Test
    fun declaredEvidenceCannotCommitWithoutItsImmutableSnapshotRows() {
        val f = preparedLeave()
        val revision = evidence(f)
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        attachmentProbe.omitRows = true
        assertEquals(409, submitEvidence(f, id, listOf(revision), key).statusCode())
        assertEquals(0, referenceCount(f, id))
        assertEquals("0", balance(f).get("reservedDays").asString())
        attachmentProbe.clear()
        body(submitEvidence(f, id, listOf(revision), key))
        assertEquals(1, referenceCount(f, id))
    }

    @Test
    fun committedSnapshotsAndRegistryRowsAreImmutableAndCompanyIsolated() {
        val f = preparedLeave()
        val revision = evidence(f)
        val id = UUID.randomUUID()
        body(submitEvidence(f, id, listOf(revision)))
        for (statement in
            listOf(
                "update leave_request_attachments set file_name='other.pdf' where request_id=?",
                "delete from leave_request_attachments where request_id=?",
                "update leave_requests set attachment_count=0,version=version+1 where id=?",
                "delete from document_evidence_references where source_kind='LEAVE_REQUEST' and source_id=?",
            )) assertThrows(DataAccessException::class.java) { database().update(statement, id) }
        val other = preparedLeave()
        val scoped =
            transactions.run(actor(other)) {
                safeDatabaseCall {
                    runtimeJdbc.queryForObject(
                        "select count(*) from leave_request_attachments where company_id=? and request_id=?",
                        Int::class.java,
                        f.company,
                        id,
                    )!!
                }
            }
        assertEquals(Result.Success(0), scoped)
        assertEquals(1, referenceCount(f, id))
    }

    @Test
    fun submittedEvidenceRemainsProtectedFromRetirementAfterTheLeaveIsWithdrawn() {
        val f = preparedLeave()
        val document = UUID.randomUUID()
        val revision = evidence(f, document = document)
        val requestId = UUID.randomUUID()
        body(submitEvidence(f, requestId, listOf(revision)))
        body(action(f, requestId, "withdraw", 0))
        val admin = documentFixture(f).actor.accountId
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'documents.retention')",
                f.company,
                admin,
            )
        val base = "/api/v1/companies/${f.company}/documents"
        body(
            command(
                f.admin,
                "$base/retention-policies",
                json.writeValueAsString(
                    mapOf(
                        "policyId" to UUID.randomUUID(),
                        "classification" to "PERSONAL",
                        "retentionDays" to 1,
                        "reason" to "Evidence retention",
                    )
                ),
                f.adminCsrf,
                UUID.randomUUID(),
            )
        )
        val archived =
            body(
                command(
                    f.admin,
                    "$base/$document/retention/archive",
                    json.writeValueAsString(mapOf("reason" to "Case completed")),
                    f.adminCsrf,
                    UUID.randomUUID(),
                )
            )
        clock.set(clock.instant().plusSeconds(86401))
        val csrf = login(f.admin)
        val version =
            database()
                .queryForObject(
                    "select version from document_revisions where company_id=? and id=?",
                    Long::class.java,
                    f.company,
                    revision,
                )!!
        val retirement =
            command(
                f.admin,
                "$base/revisions/$revision/retire",
                json.writeValueAsString(
                    mapOf(
                        "expectedRevisionVersion" to version,
                        "expectedRetentionVersion" to archived.get("version").asLong(),
                        "reason" to "Retention review",
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        error(retirement, 409, "document_evidence_referenced")
        assertEquals(
            "READY",
            database()
                .queryForObject(
                    "select status from document_revisions where company_id=? and id=?",
                    String::class.java,
                    f.company,
                    revision,
                ),
        )
        assertEquals(1, referenceCount(f, requestId))
    }
}
