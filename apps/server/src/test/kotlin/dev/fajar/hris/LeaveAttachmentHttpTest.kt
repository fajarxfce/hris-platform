package dev.fajar.hris

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveAttachmentHttpTest : LeaveAttachmentApiFixture() {
    @Test
    fun requiredEvidenceFailsBeforeReservationAndRejectsDuplicateOversizedOrUnavailableInput() {
        val f = preparedLeave()
        body(policy(f, version = 0, attachmentRequired = true))
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        error(submitEvidence(f, id, emptyList(), key), 422, "leave_attachment_required")
        assertEquals("5", balance(f).get("availableDays").asString())
        assertEquals("0", balance(f).get("reservedDays").asString())
        val ready = evidence(f)
        error(
            submitEvidence(f, id, List(4) { UUID.randomUUID() }, key),
            422,
            "invalid_leave_attachments",
        )
        error(submitEvidence(f, id, listOf(ready, ready), key), 422, "invalid_leave_attachments")
        for (revision in
            listOf(
                UUID.randomUUID(),
                evidence(f, ready = false),
                evidence(f, employee = f.manager),
                evidence(f, classification = "HR_ONLY"),
                evidence(f, classification = "RECEIPT"),
            )) error(
            submitEvidence(f, id, listOf(revision), key),
            422,
            "leave_attachment_unavailable",
        )
        assertEquals(0, referenceCount(f, id))
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from leave_requests where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        body(submitEvidence(f, id, listOf(ready), key))
        assertEquals(1, referenceCount(f, id))
        assertEquals("1", balance(f).get("reservedDays").asString())
    }

    @Test
    fun threeExactRevisionsAndRequiredPolicyRemainFrozenAcrossReplacementAndReplay() {
        val f = preparedLeave()
        body(policy(f, version = 0, attachmentRequired = true))
        val document = UUID.randomUUID()
        val first = evidence(f, document = document)
        val revisions = listOf(first, evidence(f), evidence(f))
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val submitted = body(submitEvidence(f, id, revisions, key))
        val snapshot = details(f, id)
        assertEquals(3, snapshot.get("attachments").size())
        assertTrue(snapshot.get("policy").get("attachmentRequired").asBoolean())
        val docVersion =
            database()
                .queryForObject(
                    "select version from documents where company_id=? and id=?",
                    Long::class.java,
                    f.company,
                    document,
                )!!
        val replacement =
            evidence(
                f,
                bytes = pdf + "\n% replacement".toByteArray(),
                document = document,
                version = docVersion,
            )
        assertNotEquals(first, replacement)
        body(policy(f, version = 1, attachmentRequired = false))
        assertEquals(submitted, body(submitEvidence(f, id, revisions.reversed(), key)))
        assertEquals(snapshot.get("attachments"), details(f, id).get("attachments"))
        assertTrue(details(f, id).get("policy").get("attachmentRequired").asBoolean())
        body(action(f, id, "withdraw", 0))
        assertEquals(3, referenceCount(f, id))
        assertEquals(snapshot.get("attachments"), details(f, id).get("attachments"))
        assertArrayEquals(pdf, download(f, id, first).body())
        assertEquals(
            3,
            database()
                .queryForObject(
                    "select count(*) from document_evidence_references where company_id=? and source_kind='LEAVE_REQUEST' and source_id=? and source_version=0",
                    Int::class.java,
                    f.company,
                    id,
                ),
        )
    }

    @Test
    fun crossCompanyEvidenceIsRejectedEvenWhenTheCallerAdministersBothCompanies() {
        val f = preparedLeave()
        val foreign = preparedLeave()
        val revision = evidence(foreign)
        val id = UUID.randomUUID()
        error(submitEvidence(f, id, listOf(revision)), 422, "leave_attachment_unavailable")
        assertEquals(0, referenceCount(f, id))
        assertEquals("0", balance(f).get("reservedDays").asString())
    }

    @Test
    fun competingReplaysProduceOneRequestReservationAndEvidenceRegistration() {
        val f = preparedLeave()
        val revision = evidence(f)
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val ready = CountDownLatch(2)
        val release = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val requests =
                (1..2).map {
                    executor.submit<String> {
                        ready.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        body(submitEvidence(f, id, listOf(revision), key)).toString()
                    }
                }
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                release.countDown()
                val replies = requests.map { it.get(15, TimeUnit.SECONDS) }
                assertEquals(replies[0], replies[1])
            } finally {
                release.countDown()
            }
        }
        assertEquals(1, referenceCount(f, id))
        assertEquals("1", balance(f).get("reservedDays").asString())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from mobile_sync_changes where company_id=? and collection='LEAVE_REQUESTS' and resource_id=?",
                    Int::class.java,
                    f.company,
                    id,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where company_id=? and resource_id=? and action='leave.request_submitted'",
                    Int::class.java,
                    f.company,
                    id,
                ),
        )
    }

    @Test
    fun auditFailureRollsBackAttachmentRowsReferencesLedgerCursorChangesAndTheOperationKey() {
        val f = preparedLeave()
        val revision = evidence(f)
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        attachmentProbe.beforeJournal = { row ->
            if (row.resourceId == id) throw IllegalStateException("private-attachment-audit")
        }
        val failed = submitEvidence(f, id, listOf(revision), key)
        assertEquals(500, failed.statusCode(), failed.body())
        assertFalse(failed.body().contains("private-attachment-audit"))
        assertEquals(0, referenceCount(f, id))
        assertEquals("0", balance(f).get("reservedDays").asString())
        for (table in
            listOf(
                "leave_requests",
                "leave_request_attachments",
                "mobile_sync_changes",
            )) assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from $table where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        attachmentProbe.clear()
        body(submitEvidence(f, id, listOf(revision), key))
        assertEquals(1, referenceCount(f, id))
    }

    @Test
    fun livePolicyPermissionIsRecheckedAfterTheCommandWaitsForTheAccountGuard() {
        val f = preparedLeave()
        val admin = documentFixture(f).actor.accountId
        val key = UUID.randomUUID()
        val barrier = AccountLockProbe.Barrier(admin)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { executor ->
            val pending =
                executor.submit<java.net.http.HttpResponse<String>> {
                    policy(f, version = 0, attachmentRequired = true, key = key)
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='leave.manage'",
                        f.company,
                        admin,
                    )
                barrier.release.countDown()
                error(pending.get(10, TimeUnit.SECONDS), 403, "access_denied")
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select version from leave_types where company_id=? and id=?",
                    Long::class.java,
                    f.company,
                    f.type,
                ),
        )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.manage')",
                f.company,
                admin,
            )
        body(policy(f, version = 0, attachmentRequired = true, key = key))
    }
}
