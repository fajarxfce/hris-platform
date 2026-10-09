package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.DocumentRetentionAction
import dev.fajar.hris.documents.domain.usecases.ChangeDocumentRetention
import java.time.Instant
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
class DocumentRetentionSecurityHttpTest : DocumentRetentionApiFixture() {
    @Autowired private lateinit var accountProbe: AccountLockProbe
    @Autowired private lateinit var change: ChangeDocumentRetention

    @Test
    fun retentionRequiresAnIndependentSensitiveGrantAndDoesNotComeWithDocumentAdministration() {
        val f = fixture()
        val doc = inactiveDocument(f)
        assertEquals(403, retentionPolicy(f).statusCode())
        assertEquals(403, retentionChange(f, doc, "archive").statusCode())
        assertEquals(403, get(f.browser, "${f.path}/$doc/retention").statusCode())
        val self =
            command(
                f.browser,
                "/api/v1/companies/${f.company}/members/${f.actor.accountId}",
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to 0,
                        "permissions" to (f.actor.permissions + "documents.retention"),
                        "reason" to "Attempted self grant",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(403, self.statusCode(), self.body())
        assertEquals(
            "cannot_self_grant_sensitive_access",
            json.readTree(self.body()).get("code").asString(),
        )
        val other = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Retention operator',password_hash from accounts where email='admin@example.test'",
                other,
                "$other@example.test",
            )
        val grant =
            command(
                f.browser,
                "/api/v1/companies/${f.company}/members/$other",
                json.writeValueAsString(
                    mapOf(
                        "permissions" to listOf("company.read", "documents.retention"),
                        "reason" to "Approved records administrator",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, grant.statusCode(), grant.body())
        val browser = client()
        val csrf = login(browser, "$other@example.test")
        val operator = f.copy(browser = browser, csrf = csrf)
        val archive = retentionChange(operator, doc, "archive")
        assertEquals(200, archive.statusCode(), archive.body())
        assertEquals(200, get(browser, "${f.path}/$doc/retention").statusCode())
        assertEquals(403, get(browser, "${f.path}/$doc").statusCode())
    }

    @Test
    fun everyMutationRechecksItsGrantAfterWaitingAndKeepsDeniedOperationsRetryable() {
        for (action in listOf("policy", "archive", "restore", "hold", "release-hold")) {
            val f = retentionFixture()
            val id = if (action == "policy") UUID.randomUUID() else inactiveDocument(f)
            val version =
                when (action) {
                    "restore" -> {
                        assertEquals(200, retentionChange(f, id, "archive").statusCode())
                        0L
                    }
                    "release-hold" -> {
                        assertEquals(200, retentionChange(f, id, "hold").statusCode())
                        0L
                    }
                    else -> null
                }
            val key = UUID.randomUUID()
            val execute = {
                if (action == "policy") retentionPolicy(f, id, key = key)
                else retentionChange(f, id, action, version, key)
            }
            val barrier = AccountLockProbe.Barrier(f.actor.accountId)
            accountProbe.current.set(barrier)
            try {
                Executors.newSingleThreadExecutor().use { pool ->
                    val pending = pool.submit<java.net.http.HttpResponse<String>> { execute() }
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission='documents.retention'",
                            f.company,
                            f.actor.accountId,
                        )
                    barrier.release.countDown()
                    val failed = pending.get(15, TimeUnit.SECONDS)
                    assertEquals(403, failed.statusCode(), "$action ${failed.body()}")
                }
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'documents.retention')",
                    f.company,
                    f.actor.accountId,
                )
            val retry = execute()
            assertEquals(200, retry.statusCode(), "$action ${retry.body()}")
        }
    }

    @Test
    fun credentialChangesAndExpiredStepUpAreRejectedBeforeBothCommandsCommit() {
        for (action in listOf("policy", "archive")) for (mode in listOf("credential", "recent")) {
            val f = retentionFixture()
            val id = if (action == "policy") UUID.randomUUID() else inactiveDocument(f)
            val barrier = AccountLockProbe.Barrier(f.actor.accountId)
            accountProbe.current.set(barrier)
            try {
                Executors.newSingleThreadExecutor().use { pool ->
                    val pending =
                        pool.submit<java.net.http.HttpResponse<String>> {
                            if (action == "policy") retentionPolicy(f, id)
                            else retentionChange(f, id, "archive")
                        }
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    if (mode == "credential")
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                f.actor.accountId,
                            )
                    else clock.set(clock.instant().plusSeconds(601))
                    barrier.release.countDown()
                    val denied = pending.get(15, TimeUnit.SECONDS)
                    assertEquals(
                        if (mode == "credential") 401 else 403,
                        denied.statusCode(),
                        denied.body(),
                    )
                }
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
                clock.set(Instant.now())
            }
            val table =
                if (action == "policy") "document_retention_policies"
                else "document_retention_states"
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from $table where company_id=?",
                        Int::class.java,
                        f.company,
                    ),
            )
        }
    }

    @Test
    fun committedReceiptsDoNotBypassRevocationOrFreshAuthentication() {
        val f = retentionFixture()
        val id = inactiveDocument(f)
        val key = UUID.randomUUID()
        val original = retentionChange(f, id, "archive", key = key)
        assertEquals(200, original.statusCode(), original.body())
        clock.set(clock.instant().plusSeconds(601))
        assertEquals(403, retentionChange(f, id, "archive", key = key).statusCode())
        clock.set(Instant.now())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='documents.retention'",
                f.company,
                f.actor.accountId,
            )
        assertEquals(403, retentionChange(f, id, "archive", key = key).statusCode())
        assertEquals(403, get(f.browser, "${f.path}/$id/retention/history").statusCode())
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'documents.retention')",
                f.company,
                f.actor.accountId,
            )
        assertEquals(original.body(), retentionChange(f, id, "archive", key = key).body())
    }

    @Test
    fun interruptedCommandsReleaseTheirTransactionsAndCanUseTheSameOperationAgain() {
        val f = retentionFixture()
        val id = inactiveDocument(f)
        val operation = UUID.randomUUID()
        val barrier = AccountLockProbe.Barrier(f.actor.accountId)
        val exited = CountDownLatch(1)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<Result<MutationReceipt>> {
                        try {
                            change.execute(
                                f.actor,
                                operation,
                                id,
                                DocumentRetentionAction.ARCHIVE,
                                null,
                                "Archive approved",
                            )
                        } finally {
                            exited.countDown()
                        }
                    }
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                pending.cancel(true)
                assertTrue(exited.await(5, TimeUnit.SECONDS))
            }
        } finally {
            barrier.release.countDown()
            accountProbe.current.set(null)
        }
        assertTrue(retention(f, id).get("version").isNull)
        assertTrue(
            change.execute(
                f.actor,
                operation,
                id,
                DocumentRetentionAction.ARCHIVE,
                null,
                "Archive approved",
            ) is Result.Success
        )
    }
}
