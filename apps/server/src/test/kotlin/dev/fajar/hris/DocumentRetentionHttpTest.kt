package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.repositories.DocumentRetentionRepository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException

class DocumentRetentionHttpTest : DocumentRetentionApiFixture() {
    @Autowired private lateinit var retentionRepository: DocumentRetentionRepository
    @Autowired private lateinit var scope: TransactionRunner

    @Test
    fun archivalDefaultsToIndefiniteAndFreezesThePolicyChosenAtThatTime() {
        val f = retentionFixture()
        val doc = inactiveDocument(f)
        assertTrue(retention(f, doc).get("version").isNull)
        assertTrue(retention(f, doc).get("archive").isNull)
        assertEquals(200, retentionChange(f, doc, "archive").statusCode())
        val indefinite = retention(f, doc).get("archive")
        assertTrue(indefinite.get("eligibleAt").isNull)
        assertTrue(indefinite.get("policyId").isNull)
        val policy = UUID.randomUUID()
        assertEquals(200, retentionPolicy(f, policy).statusCode())
        assertEquals(indefinite, retention(f, doc).get("archive"))
        assertEquals(200, retentionChange(f, doc, "restore", 0).statusCode())
        assertEquals(200, retentionChange(f, doc, "archive", 1).statusCode())
        val archive = retention(f, doc).get("archive")
        assertEquals(policy.toString(), archive.get("policyId").asString())
        assertEquals(0, archive.get("policyVersion").asLong())
        assertEquals(30, archive.get("retentionDays").asInt())
        assertEquals(
            Instant.parse(archive.get("archivedAt").asString()).plusSeconds(30L * 86400),
            Instant.parse(archive.get("eligibleAt").asString()),
        )
        assertEquals(200, retentionPolicy(f, policy, 0, 1).statusCode())
        assertEquals(archive, retention(f, doc).get("archive"))
        assertEquals(200, retentionChange(f, doc, "hold", 2).statusCode())
        assertTrue(retention(f, doc).get("legalHold").asBoolean())
        assertEquals(200, retentionChange(f, doc, "release-hold", 3).statusCode())
        assertEquals(archive, retention(f, doc).get("archive"))
        val history =
            json.readTree(get(f.browser, "${f.path}/$doc/retention/history?limit=2").body())
        assertEquals(
            listOf("ARCHIVE", "RESTORE"),
            history
                .get("items")
                .iterator()
                .asSequence()
                .map { it.get("action").asString() }
                .toList(),
        )
        assertEquals("1", history.get("nextCursor").asString())
        val remaining =
            json.readTree(get(f.browser, "${f.path}/$doc/retention/history?after=1").body())
        assertEquals(3, remaining.get("items").size())
        val policies = json.readTree(get(f.browser, "${f.path}/retention-policies").body())
        assertEquals(1, policies.size())
        assertEquals(1, policies.get(0).get("version").asLong())
        val policyHistory =
            json.readTree(
                get(f.browser, "${f.path}/retention-policies/$policy/history?limit=1").body()
            )
        assertEquals(30, policyHistory.get("items").get(0).get("retentionDays").asInt())
        assertEquals("0", policyHistory.get("nextCursor").asString())
    }

    @Test
    fun explicitIndefinitePoliciesRemainIndefiniteAfterLaterChanges() {
        val f = retentionFixture()
        val policy = UUID.randomUUID()
        assertEquals(200, retentionPolicy(f, policy, days = null).statusCode())
        val doc = inactiveDocument(f)
        assertEquals(200, retentionChange(f, doc, "archive").statusCode())
        assertEquals(policy.toString(), retention(f, doc).get("archive").get("policyId").asString())
        assertEquals(200, retentionPolicy(f, policy, 0, 1).statusCode())
        assertTrue(retention(f, doc).get("archive").get("eligibleAt").isNull)
        assertEquals(200, retentionChange(f, doc, "hold", 0).statusCode())
        assertEquals(200, retentionChange(f, doc, "restore", 1).statusCode())
        assertTrue(retention(f, doc).get("legalHold").asBoolean())
        assertTrue(retention(f, doc).get("archive").isNull)
    }

    @Test
    fun unfinishedExpiredAndValidatingRevisionsRequireExplicitCancellation() {
        val f = retentionFixture()
        val activeDoc = UUID.randomUUID()
        val activeRevision = begin(f, document = activeDoc)
        assertEquals(409, retentionChange(f, activeDoc, "archive").statusCode())
        assertEquals(200, cancel(f, activeRevision).statusCode())
        // Expiry does not authorize a hidden cancellation during archival.
        val doc = UUID.randomUUID()
        clock.set(Instant.now().minusSeconds(90000))
        val rev = begin(f, document = doc)
        clock.set(Instant.now())
        assertEquals(409, retentionChange(f, doc, "archive").statusCode())
        assertEquals(200, cancel(f, rev).statusCode())
        assertEquals(200, retentionChange(f, doc, "archive").statusCode())
        val blocked = start(f, uploadInput(f, document = doc, version = 1))
        assertEquals(409, blocked.statusCode(), blocked.body())
        assertEquals("document_archived", json.readTree(blocked.body()).get("code").asString())
        assertEquals(200, retentionChange(f, doc, "restore", 0).statusCode())
        assertEquals(200, start(f, uploadInput(f, document = doc, version = 1)).statusCode())
        val validatedDoc = UUID.randomUUID()
        val validated = filled(f, document = validatedDoc)
        val lease = beginValidation(f, validated)
        assertEquals(409, retentionChange(f, validatedDoc, "archive").statusCode())
        assertEquals(
            200,
            cancel(f, validated, revision(f, validated).get("version").asLong()).statusCode(),
        )
        assertEquals(200, retentionChange(f, validatedDoc, "archive").statusCode())
        assertTrue(run(f, lease) is Result.Failed)
        assertEquals("CANCELLED", revision(f, validated).get("status").asString())
    }

    @Test
    fun holdsAllowNewEvidenceAndOriginalReceiptsReplayAfterLaterChanges() {
        val f = retentionFixture()
        val doc = inactiveDocument(f)
        val operation = UUID.randomUUID()
        val held = retentionChange(f, doc, "hold", key = operation)
        assertEquals(200, held.statusCode(), held.body())
        assertEquals(409, retentionChange(f, doc, "hold", 0).statusCode())
        val rev = filled(f, document = doc, version = 1)
        val lease = beginValidation(f, rev)
        assertTrue(run(f, lease) is Result.Success)
        assertEquals("READY", revision(f, rev).get("status").asString())
        assertEquals(200, retentionChange(f, doc, "archive", 0).statusCode())
        assertEquals(held.body(), retentionChange(f, doc, "hold", key = operation).body())
        assertEquals(409, retentionChange(f, doc, "release-hold", 1, key = operation).statusCode())
        assertEquals(409, retentionChange(f, doc, "release-hold", 0).statusCode())
        assertEquals(200, get(f.browser, "${f.path}/revisions/$rev/content").statusCode())
        assertEquals(0, garbage(rev))
        assertEquals(1, storageProbe.objects.keys.count { it.startsWith("${f.company}/$rev/") })
    }

    @Test
    fun policyIdentityValidationAndFiniteHistoryCannotBeBypassed() {
        val f = retentionFixture()
        val policy = UUID.randomUUID()
        for (days in listOf(0, -1, 36501)) assertEquals(
            422,
            retentionPolicy(f, policy, days = days).statusCode(),
        )
        assertEquals(422, retentionPolicy(f, policy, reason = 0.toChar().toString()).statusCode())
        assertEquals(200, retentionPolicy(f, policy, days = 36500).statusCode())
        assertEquals(409, retentionPolicy(f, days = 2).statusCode())
        assertEquals(409, retentionPolicy(f, policy, 0, classification = "RECEIPT").statusCode())
        assertEquals(409, retentionPolicy(f, policy, 5).statusCode())
        assertEquals(422, retentionPolicy(f, policy, 10000).statusCode())
        assertEquals(
            422,
            get(f.browser, "${f.path}/retention-policies/$policy/history?after=10000").statusCode(),
        )
        assertEquals(
            422,
            get(f.browser, "${f.path}/retention-policies/$policy/history?limit=201").statusCode(),
        )
        val doc = inactiveDocument(f)
        assertEquals(409, retentionChange(f, doc, "restore").statusCode())
        assertEquals(409, retentionChange(f, doc, "release-hold").statusCode())
        assertEquals(422, retentionChange(f, doc, "hold", -1).statusCode())
        assertEquals(422, retentionChange(f, doc, "hold", reason = "x".repeat(1001)).statusCode())
        assertEquals(422, get(f.browser, "${f.path}/$doc/retention/history?limit=0").statusCode())
        assertEquals(200, retentionChange(f, doc, "hold").statusCode())
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update document_retention_states set version=version+1 where company_id=? and document_id=?",
                    f.company,
                    doc,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update document_retention_policy_revisions set retention_days=1 where company_id=? and policy_id=?",
                    f.company,
                    policy,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "delete from document_retention_changes where company_id=? and document_id=?",
                    f.company,
                    doc,
                )
        }
    }

    @Test
    fun retentionReadsAreCompanyScopedAndDoNotGrantContentAccess() {
        val f = retentionFixture()
        val policy = UUID.randomUUID()
        assertEquals(200, retentionPolicy(f, policy).statusCode())
        val doc = UUID.randomUUID()
        val rev = filled(f, document = doc)
        assertTrue(run(f, beginValidation(f, rev)) is Result.Success)
        assertEquals(200, retentionChange(f, doc, "archive").statusCode())
        val other = retentionFixture()
        assertEquals(404, get(other.browser, "${other.path}/$doc/retention").statusCode())
        assertEquals(
            404,
            get(other.browser, "${other.path}/retention-policies/$policy/history").statusCode(),
        )
        assertEquals(
            Result.Success(null),
            scope.run(other.actor) { retentionRepository.policy(f.company, policy) },
        )
        assertEquals(
            Result.Success(null),
            scope.run(other.actor) { retentionRepository.state(f.company, doc) },
        )
        assertEquals(
            Result.Success(emptyList<DocumentRetentionPolicy>()),
            scope.run(other.actor) { retentionRepository.policies(f.company) },
        )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission in ('documents.read','documents.self.read','people.profile.read')",
                f.company,
                f.actor.accountId,
            )
        assertEquals(200, get(f.browser, "${f.path}/$doc/retention").statusCode())
        assertEquals(403, get(f.browser, "${f.path}/revisions/$rev/content").statusCode())
    }

    @Test
    fun databaseRejectsAnArchiveThatSkipsActiveUploadsOrItsPolicySnapshot() {
        val f = retentionFixture()
        val policyId = UUID.randomUUID()
        assertEquals(200, retentionPolicy(f, policyId).statusCode())
        val doc = UUID.randomUUID()
        val rev = begin(f, document = doc)
        val now = clock.instant()
        val snapshot = DocumentArchive(now, policyId, 0, 30, now.plusSeconds(30L * 86400))
        val change =
            DocumentRetentionChange(
                DocumentRetentionState(doc, 0, snapshot),
                DocumentRetentionAction.ARCHIVE,
                f.actor.accountId,
                now,
                "Constraint probe",
            )
        assertTrue(
            scope.run(f.actor) { retentionRepository.saveState(f.company, change, null) }
                is Result.Failed
        )
        assertTrue(retention(f, doc).get("version").isNull)
        assertEquals(200, cancel(f, rev).statusCode())
        assertTrue(
            scope.run(f.actor) {
                retentionRepository.saveState(
                    f.company,
                    change.copy(
                        state =
                            change.state.copy(
                                archive = DocumentArchive(now, null, null, null, null)
                            )
                    ),
                    null,
                )
            } is Result.Failed
        )
        assertTrue(retention(f, doc).get("version").isNull)
        assertEquals(200, retentionChange(f, doc, "archive").statusCode())
    }

    @Test
    fun competingArchiveAndUploadOrHoldCommandsHaveOneVersionWinner() {
        for (mode in listOf("upload", "hold")) {
            val f = retentionFixture()
            val doc = inactiveDocument(f)
            val gate = CountDownLatch(1)
            Executors.newFixedThreadPool(2).use { pool ->
                val archived =
                    pool.submit<java.net.http.HttpResponse<String>> {
                        check(gate.await(5, TimeUnit.SECONDS))
                        retentionChange(f, doc, "archive")
                    }
                val competing =
                    pool.submit<java.net.http.HttpResponse<String>> {
                        check(gate.await(5, TimeUnit.SECONDS))
                        if (mode == "upload") start(f, uploadInput(f, document = doc, version = 1))
                        else retentionChange(f, doc, "hold")
                    }
                gate.countDown()
                val results =
                    listOf(archived.get(15, TimeUnit.SECONDS), competing.get(15, TimeUnit.SECONDS))
                assertEquals(
                    listOf(200, 409),
                    results.map { it.statusCode() }.sorted(),
                    results.map { it.body() }.toString(),
                )
            }
        }
        val f = retentionFixture()
        val policy = UUID.randomUUID()
        val key = UUID.randomUUID()
        val gate = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val pending =
                (1..2).map {
                    pool.submit<java.net.http.HttpResponse<String>> {
                        check(gate.await(5, TimeUnit.SECONDS))
                        retentionPolicy(f, policy, key = key)
                    }
                }
            gate.countDown()
            val results = pending.map { it.get(15, TimeUnit.SECONDS) }
            results.forEach { assertEquals(200, it.statusCode(), it.body()) }
            assertEquals(results[0].body(), results[1].body())
        }
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from document_retention_policy_revisions where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun auditFailuresRollBackPolicyAndStateIncludingTheirOperationReceipts() {
        val f = retentionFixture()
        for (mode in listOf("policy", "archive")) {
            val id = if (mode == "policy") UUID.randomUUID() else inactiveDocument(f)
            val key = UUID.randomUUID()
            val action =
                if (mode == "policy") "documents.retention_policy_saved"
                else "documents.retention_changed"
            val execute = {
                if (mode == "policy") retentionPolicy(f, id, key = key)
                else retentionChange(f, id, "archive", key = key)
            }
            database()
                .execute(
                    """create function fail_retention_audit() returns trigger language plpgsql as ${'$'}${'$'} begin if new.resource_id='$id'::uuid and new.action='$action' then raise exception 'Fixture' using errcode='23514';end if;return new;end ${'$'}${'$'}"""
                )
            database()
                .execute(
                    "create trigger retention_audit_probe before insert on audit_entries for each row execute function fail_retention_audit()"
                )
            try {
                assertNotEquals(200, execute().statusCode())
                if (mode == "policy")
                    assertEquals(
                        0,
                        json.readTree(get(f.browser, "${f.path}/retention-policies").body()).size(),
                    )
                else assertTrue(retention(f, id).get("version").isNull)
            } finally {
                database().execute("drop trigger retention_audit_probe on audit_entries")
                database().execute("drop function fail_retention_audit()")
            }
            val retry = execute()
            assertEquals(200, retry.statusCode(), retry.body())
        }
    }
}
