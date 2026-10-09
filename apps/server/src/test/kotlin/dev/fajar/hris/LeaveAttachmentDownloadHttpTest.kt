package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LeaveAttachmentDownloadHttpTest : LeaveAttachmentApiFixture() {
    @Test
    fun ownersAndAssignedApproversReadExactEvidenceWithBoundedRangeAndConditionalResponses() {
        val f = preparedLeave()
        val bytes = ByteArray(1048600) { (it % 127).toByte() }
        pdf.copyInto(bytes)
        val revision = evidence(f, bytes)
        val id = UUID.randomUUID()
        body(submitEvidence(f, id, listOf(revision)))
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='leave.team.read'",
                f.company,
                f.managerAccount,
            )
        assertEquals(
            403,
            get(f.supervisor, "/api/v1/companies/${f.company}/documents/revisions/$revision")
                .statusCode(),
        )
        val full = download(f, id, revision, f.supervisor)
        assertEquals(200, full.statusCode())
        assertArrayEquals(bytes, full.body())
        assertEquals("private, no-store", full.headers().firstValue("Cache-Control").orElseThrow())
        assertEquals("nosniff", full.headers().firstValue("X-Content-Type-Options").orElseThrow())
        val partial = download(f, id, revision, headers = mapOf("Range" to "bytes=1048570-1048585"))
        assertEquals(206, partial.statusCode())
        assertArrayEquals(bytes.copyOfRange(1048570, 1048586), partial.body())
        storageProbe.reads.set(0)
        val head = download(f, id, revision, method = "HEAD")
        assertEquals(200, head.statusCode())
        val etag = head.headers().firstValue("ETag").orElseThrow()
        assertEquals(
            304,
            download(f, id, revision, headers = mapOf("If-None-Match" to etag)).statusCode(),
        )
        assertEquals(0, storageProbe.reads.get())
        val stranger = member(f, listOf("company.read", "leave.approve")).second
        assertEquals(
            404,
            download(f, id, revision, stranger, mapOf("If-None-Match" to etag), "HEAD").statusCode(),
        )
        assertEquals(0, storageProbe.reads.get())
    }

    @Test
    fun unattachedCrossCompanyAndInvalidRangeRequestsDoNotReadStorage() {
        val f = preparedLeave()
        val revision = evidence(f)
        val unrelated = evidence(f)
        val id = UUID.randomUUID()
        body(submitEvidence(f, id, listOf(revision)))
        storageProbe.reads.set(0)
        assertEquals(404, download(f, id, unrelated).statusCode())
        assertEquals(404, download(f, UUID.randomUUID(), revision).statusCode())
        val company = company(f.admin, f.adminCsrf)
        assertEquals(404, download(f.copy(company = company), id, revision, f.admin).statusCode())
        for (range in
            listOf("bytes=9999999-", "bytes=-0", "bytes=0-1,3-4", "bytes=4-2")) assertEquals(
            416,
            download(f, id, revision, headers = mapOf("Range" to range)).statusCode(),
        )
        assertEquals(0, storageProbe.reads.get())
    }

    @Test
    fun permissionOrCredentialRevocationDuringStorageReadPreventsReturningTheBytes() {
        for (credential in listOf(false, true)) {
            val f = preparedLeave()
            val revision = evidence(f)
            val id = UUID.randomUUID()
            body(submitEvidence(f, id, listOf(revision)))
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            storageProbe.beforeRead = {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            Executors.newSingleThreadExecutor().use { executor ->
                val pending =
                    executor.submit<Result<ByteArray>> {
                        readAttachment.execute(actor(f), id, revision, 0, pdf.size)
                    }
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    if (credential)
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                f.account,
                            )
                    else
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission='leave.self.manage'",
                                f.company,
                                f.account,
                            )
                    release.countDown()
                    val result = pending.get(10, TimeUnit.SECONDS)
                    assertTrue(result is Result.Failed, result.toString())
                    assertEquals(
                        if (credential) FailureKind.UNAUTHENTICATED else FailureKind.NOT_FOUND,
                        (result as Result.Failed).failure.kind,
                    )
                } finally {
                    release.countDown()
                    storageProbe.beforeRead = null
                }
            }
        }
    }

    @Test
    fun reassignmentDuringAStorageReadRemovesTheFormerApproversContentAccess() {
        val f = preparedLeave()
        val revision = evidence(f)
        val id = UUID.randomUUID()
        body(submitEvidence(f, id, listOf(revision)))
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='leave.team.read'",
                f.company,
                f.managerAccount,
            )
        val replacement = member(f, listOf("company.read", "leave.approve")).first
        val approval = details(f, id).get("approval")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        storageProbe.beforeRead = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        val reviewer =
            actor(f)
                .copy(
                    accountId = f.managerAccount,
                    permissions = setOf("company.read", "leave.team.approve", "approvals.read"),
                )
        Executors.newSingleThreadExecutor().use { executor ->
            val pending =
                executor.submit<Result<ByteArray>> {
                    readAttachment.execute(reviewer, id, revision, 0, pdf.size)
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                body(
                    command(
                        f.admin,
                        "/api/v1/companies/${f.company}/approvals/${approval.get("id").asString()}/reassign",
                        json.writeValueAsString(
                            mapOf(
                                "version" to approval.get("version").asLong(),
                                "assignees" to listOf(replacement),
                                "reason" to "Coverage change",
                            )
                        ),
                        f.adminCsrf,
                        UUID.randomUUID(),
                    )
                )
                release.countDown()
                val result = pending.get(10, TimeUnit.SECONDS)
                assertTrue(result is Result.Failed, result.toString())
                assertEquals("leave_attachment_not_found", (result as Result.Failed).failure.code)
            } finally {
                release.countDown()
                storageProbe.beforeRead = null
            }
        }
    }

    @Test
    fun cancellationReleasesPendingIoAndCorruptedStoredContentIsRejected() {
        val f = preparedLeave()
        val revision = evidence(f)
        val id = UUID.randomUUID()
        body(submitEvidence(f, id, listOf(revision)))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        storageProbe.beforeRead = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        val request = Thread {
            try {
                readAttachment.execute(actor(f), id, revision, 0, pdf.size)
                failure.set(AssertionError("Cancelled I/O returned"))
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                exited.countDown()
            }
        }
        try {
            request.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            request.interrupt()
            assertTrue(exited.await(5, TimeUnit.SECONDS))
            request.join(1000)
            assertFalse(request.isAlive)
            assertTrue(failure.get() is InterruptedException, failure.get().toString())
        } finally {
            release.countDown()
            request.interrupt()
            request.join(5000)
            storageProbe.beforeRead = null
        }
        val key = storageProbe.objects.keys.single()
        val stored = storageProbe.objects.getValue(key)
        val corrupted = stored.bytes.copyOf()
        corrupted[0] = (corrupted[0].toInt() xor 1).toByte()
        storageProbe.objects[key] = stored.copy(bytes = corrupted)
        val result = readAttachment.execute(actor(f), id, revision, 0, pdf.size)
        assertTrue(result is Result.Failed)
        assertEquals("document_content_integrity_failure", (result as Result.Failed).failure.code)
        storageProbe.objects[key] = stored
        assertArrayEquals(pdf, download(f, id, revision).body())
    }
}
