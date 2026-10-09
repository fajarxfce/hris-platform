package dev.fajar.hris

import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
class DocumentRetirementReferenceHttpTest : ExpenseSubmissionApiFixture() {
    @Autowired private lateinit var accountProbe: AccountLockProbe

    @Test
    fun expenseRegistrationAndRetirementSerializeInBothOrders() {
        for (first in listOf("reference", "retire")) {
            val initial = expenseFixture()
            val receipt = readyReceipt(initial)
            val admin =
                database()
                    .queryForObject(
                        "select id from accounts where email='admin@example.test'",
                        UUID::class.java,
                    )!!
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'documents.retention')",
                    initial.company,
                    admin,
                )
            val path = "/api/v1/companies/${initial.company}/documents"
            val doc =
                database()
                    .queryForObject(
                        "select document_id from document_revisions where id=?",
                        UUID::class.java,
                        receipt,
                    )!!
            val version =
                database()
                    .queryForObject(
                        "select version from document_revisions where id=?",
                        Long::class.java,
                        receipt,
                    )!!
            assertEquals(
                200,
                command(
                        initial.admin,
                        "$path/retention-policies",
                        json.writeValueAsString(
                            mapOf(
                                "policyId" to UUID.randomUUID(),
                                "classification" to "RECEIPT",
                                "retentionDays" to 1,
                                "expectedVersion" to null,
                                "reason" to "Records policy",
                            )
                        ),
                        initial.adminCsrf,
                        UUID.randomUUID(),
                    )
                    .statusCode(),
            )
            assertEquals(
                200,
                command(
                        initial.admin,
                        "$path/$doc/retention/archive",
                        json.writeValueAsString(
                            mapOf("expectedVersion" to null, "reason" to "Archive receipt")
                        ),
                        initial.adminCsrf,
                        UUID.randomUUID(),
                    )
                    .statusCode(),
            )
            clock.set(clock.instant().plusSeconds(86401))
            val f = initial.copy(adminCsrf = login(initial.admin))
            val lines = readyLines(f, receipt)
            val save = { saveExpense(f, lines = lines, asAdmin = true) }
            val retire = {
                command(
                    f.admin,
                    "$path/revisions/$receipt/retire",
                    json.writeValueAsString(
                        mapOf(
                            "expectedRevisionVersion" to version,
                            "expectedRetentionVersion" to 0,
                            "reason" to "Retention expired",
                        )
                    ),
                    f.adminCsrf,
                    UUID.randomUUID(),
                )
            }
            val barrier = AccountLockProbe.Barrier(admin)
            accountProbe.current.set(barrier)
            try {
                Executors.newFixedThreadPool(2).use { pool ->
                    val leading =
                        pool.submit<java.net.http.HttpResponse<String>> {
                            if (first == "reference") save() else retire()
                        }
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    val trailing =
                        pool.submit<java.net.http.HttpResponse<String>> {
                            if (first == "reference") retire() else save()
                        }
                    barrier.release.countDown()
                    val one = leading.get(15, TimeUnit.SECONDS)
                    val two = trailing.get(15, TimeUnit.SECONDS)
                    assertEquals(200, one.statusCode(), one.body())
                    assertEquals(
                        if (first == "reference") 409 else 422,
                        two.statusCode(),
                        two.body(),
                    )
                }
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
            if (first == "reference") {
                assertEquals(
                    200,
                    saveExpense(f, 0, lines = emptyList(), asAdmin = true).statusCode(),
                )
                val rejected = retire()
                assertEquals(409, rejected.statusCode(), rejected.body())
                assertEquals(
                    "document_evidence_referenced",
                    json.readTree(rejected.body()).get("code").asString(),
                )
                assertEquals(
                    "READY",
                    database()
                        .queryForObject(
                            "select status from document_revisions where id=?",
                            String::class.java,
                            receipt,
                        ),
                )
                assertEquals(0, garbage(receipt))
            } else {
                assertEquals(
                    0,
                    database()
                        .queryForObject(
                            "select count(*) from expense_claims where id=?",
                            Int::class.java,
                            f.claim,
                        ),
                )
                assertEquals(
                    "RETIRED",
                    database()
                        .queryForObject(
                            "select status from document_revisions where id=?",
                            String::class.java,
                            receipt,
                        ),
                )
                assertEquals(1, garbage(receipt))
            }
        }
    }
}
