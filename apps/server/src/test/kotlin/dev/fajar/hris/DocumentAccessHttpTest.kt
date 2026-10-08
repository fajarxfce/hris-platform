package dev.fajar.hris

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.documents.domain.policies.DOCUMENT_MAXIMUM_BYTES
import java.time.Instant
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DocumentAccessHttpTest : DocumentApiFixture() {
    @Test
    fun metadataValidatesTypeFilenameBoundsAndCompanyScope() {
        val f = fixture()
        val input = uploadInput(f)
        for (patch in
            listOf(
                mapOf("fileName" to "../evidence.pdf"),
                mapOf("fileName" to "evidence.exe"),
                mapOf("mediaType" to "text/html"),
                mapOf("size" to 0L),
                mapOf("size" to DOCUMENT_MAXIMUM_BYTES + 1),
                mapOf("sha256" to "invalid"),
                mapOf("title" to ""),
            )) {
            val response = start(f, input + patch)
            assertEquals(422, response.statusCode(), response.body())
        }
        val other = fixture()
        val foreign = begin(other)
        assertEquals(404, get(f.browser, "${f.path}/revisions/$foreign").statusCode())
        assertEquals(404, upload(f, foreign).statusCode())
        assertEquals(404, start(f, input + mapOf("employmentId" to other.employee)).statusCode())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from documents where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(0, storageProbe.calls.get())
    }

    @Test
    fun selfServiceCannotReadOtherEmployeesOrHrOnlyDocuments() {
        val f = fixture()
        val account = UUID.randomUUID()
        val email = "$account@example.test"
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Employee fixture',password_hash from accounts where email='admin@example.test'",
                account,
                email,
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                f.company,
                account,
            )
        for (permission in
            listOf("company.read", "documents.self.read", "documents.self.upload")) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                f.company,
                account,
                permission,
            )
        val linked = employee(f.browser, f.csrf, f.company, account = account)
        val browser = client()
        val csrf = login(browser, email)
        val self =
            Fixture(
                browser,
                csrf,
                Actor(
                    account,
                    f.company,
                    setOf("documents.self.read", "documents.self.upload"),
                    Instant.now(),
                    UUID.randomUUID(),
                ),
                linked,
            )
        val personal = begin(self)
        assertEquals(200, upload(self, personal).statusCode())
        assertEquals(200, get(browser, "${self.path}/revisions/$personal").statusCode())
        val hrId = UUID.randomUUID()
        val hr =
            start(
                f,
                uploadInput(f, revision = hrId, classification = "HR_ONLY") +
                    mapOf("employmentId" to linked),
            )
        assertEquals(200, hr.statusCode(), hr.body())
        assertEquals(403, get(browser, "${self.path}/revisions/$hrId").statusCode())
        assertEquals(403, start(self, uploadInput(self, classification = "HR_ONLY")).statusCode())
        assertEquals(403, get(browser, "${self.path}?employmentId=${f.employee}").statusCode())
        val page = json.readTree(get(browser, "${self.path}?employmentId=$linked").body())
        assertEquals(1, page.get("items").size())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='people.profile.read'",
                f.company,
                f.actor.accountId,
            )
        assertEquals(403, get(f.browser, "${f.path}/revisions/$personal").statusCode())
    }

    @Test
    fun competingStartsAndCancellationUseVersionsAndReplayWithoutNewRows() {
        val f = fixture()
        val document = UUID.randomUUID()
        val gate = CountDownLatch(1)
        val ready = CountDownLatch(2)
        Executors.newFixedThreadPool(2).use { pool ->
            val futures =
                (1..2).map {
                    pool.submit<Int> {
                        ready.countDown()
                        check(gate.await(5, TimeUnit.SECONDS))
                        start(f, uploadInput(f, document = document)).statusCode()
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            gate.countDown()
            assertEquals(listOf(200, 409), futures.map { it.get(10, TimeUnit.SECONDS) }.sorted())
        }
        val revision =
            database()
                .queryForObject(
                    "select id from document_revisions where company_id=? and document_id=?",
                    UUID::class.java,
                    f.company,
                    document,
                )!!
        val key = UUID.randomUUID()
        val cancelled = cancel(f, revision, key = key)
        assertEquals(200, cancelled.statusCode(), cancelled.body())
        assertEquals(cancelled.body(), cancel(f, revision, key = key).body())
        assertEquals(409, cancel(f, revision).statusCode())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='documents.upload_cancelled'",
                    Int::class.java,
                    revision,
                ),
        )
        val next = begin(f, document = document, version = 1)
        assertEquals(2, revision(f, next).get("number").asInt())
    }

    @Test
    fun activeUploadAndByteReservationsAreBoundedAndRecoverAfterCancellation() {
        val f = fixture()
        val ids = (1..10).map { begin(f) }
        val rejected = start(f, uploadInput(f))
        assertEquals(429, rejected.statusCode(), rejected.body())
        assertEquals(200, cancel(f, ids.first()).statusCode())
        begin(f)
        val quota = fixture()
        database()
            .update(
                """insert into object_cleanup_queue(id,company_id,resource_id,object_key,object_bytes,created_by,eligible_at)
            select gen_random_uuid(),?,gen_random_uuid(),?::text || '/' || gen_random_uuid()::text,5242880,?,clock_timestamp()+interval '1 day' from generate_series(1,2048)""",
                quota.company,
                quota.company,
                quota.actor.accountId,
            )
        val full = start(quota, uploadInput(quota))
        assertEquals(409, full.statusCode(), full.body())
        assertEquals("document_storage_quota", json.readTree(full.body()).get("code").asString())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from documents where company_id=?",
                    Int::class.java,
                    quota.company,
                ),
        )
    }

    @Test
    fun expiredUploadsRemainInspectableAndCanBeReplaced() {
        val f = fixture()
        val document = UUID.randomUUID()
        clock.set(Instant.now().minusSeconds(90000))
        val expired = begin(f, document = document)
        clock.set(Instant.now())
        assertEquals("EXPIRED", revision(f, expired).get("status").asString())
        assertEquals(409, upload(f, expired).statusCode())
        val current = begin(f, document = document, version = 1)
        assertEquals("UPLOADING", revision(f, current).get("status").asString())
        assertEquals(
            "EXPIRED",
            database()
                .queryForObject(
                    "select status from document_revisions where id=?",
                    String::class.java,
                    expired,
                ),
        )
    }
}
