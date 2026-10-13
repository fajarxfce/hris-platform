package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.ExpenseDraftReferenceQuery
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SelfExpenseDraftReferencesHttpTest : ExpenseReadAssuranceApiFixture() {
    @Test
    fun employeeReadsMinimalDraftNamesAndUploadStatusWithoutOrganizationOrDocumentBrowsing() {
        val f = expenseFixture()
        val revision = expenseReceipt(f)
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='company.read'",
                f.company,
                f.account,
            )
        val result =
            get(
                f.worker,
                path(f) +
                    "?categoryIds=${f.category}&costCenterIds=${f.costCenter}&receiptRevisionIds=$revision",
            )
        assertEquals(200, result.statusCode(), result.body())
        val body = json.readTree(result.body())
        assertEquals(f.employee.toString(), body["employmentId"].asString())
        assertEquals("Business travel", body["categories"][0]["name"].asString())
        assertEquals("Operations", body["costCenters"][0]["name"].asString())
        assertEquals(
            setOf("id", "code", "name", "active"),
            body["categories"][0].properties().map { it.key }.toSet(),
        )
        val receipt = body["receipts"][0]
        assertEquals(
            setOf("revisionId", "documentId", "fileName", "mediaType", "size", "status"),
            receipt.properties().map { it.key }.toSet(),
        )
        assertEquals("receipt.pdf", receipt["fileName"].asString())
        assertEquals("UPLOADING", receipt["status"].asString())
        assertFalse(
            result.body().contains("sha256") ||
                result.body().contains("createdBy") ||
                result.body().contains("inspection")
        )
        assertEquals(
            403,
            get(f.worker, "/api/v1/companies/${f.company}/organization-units").statusCode(),
        )
        assertEquals(
            403,
            get(f.worker, "/api/v1/companies/${f.company}/documents?employmentId=${f.employee}")
                .statusCode(),
        )
        val empty = get(f.worker, path(f))
        assertEquals(200, empty.statusCode(), empty.body())
        assertEquals(0, json.readTree(empty.body())["receipts"].size())
    }

    @Test
    fun unavailableAndForeignReferencesNeverDiscloseLabelsOrReceiptMetadata() {
        val f = expenseFixture()
        val other = expenseFixture()
        val wrongEmployee = expenseReceipt(f, employee = f.manager)
        val wrongClassification = expenseReceipt(f, classification = "PERSONAL")
        val foreignRevision = expenseReceipt(other)
        val missing = UUID.randomUUID()
        val department = expenseUnit(f.admin, f.adminCsrf, f.company, "DEPARTMENT")
        val categories = listOf(other.category, missing).joinToString(",")
        val centers = listOf(other.costCenter, department, missing).joinToString(",")
        val receipts =
            listOf(wrongEmployee, wrongClassification, foreignRevision, missing).joinToString(",")
        val result =
            get(
                f.worker,
                path(f) +
                    "?categoryIds=$categories&costCenterIds=$centers&receiptRevisionIds=$receipts",
            )
        assertEquals(200, result.statusCode(), result.body())
        val body = json.readTree(result.body())
        for (field in listOf("categories", "costCenters")) for (item in body[field]) {
            assertTrue(item["code"].isNull)
            assertTrue(item["name"].isNull)
            assertTrue(item["active"].isNull)
        }
        for (item in body["receipts"]) for (field in
            listOf("documentId", "fileName", "mediaType", "size", "status")) assertTrue(
            item[field].isNull
        )
        assertFalse(
            result.body().contains("receipt.pdf") ||
                result.body().contains("Business travel") ||
                result.body().contains("Operations")
        )
        assertEquals(404, get(f.worker, path(f, f.manager)).statusCode())
        assertEquals(404, get(f.admin, path(f)).statusCode())
    }

    @Test
    fun archivedNamesAndReadyReceiptMetadataRemainAvailableForReviewWithoutReadingContent() {
        val f = expenseFixture()
        val revision = readyReceipt(f)
        expensePolicy(f, changes = mapOf("active" to false))
        val changed =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/organization-units/${f.costCenter}",
                json.writeValueAsString(
                    mapOf(
                        "code" to "CC${f.costCenter.toString().take(8)}",
                        "name" to "Archived operations",
                        "kind" to "COST_CENTER",
                        "active" to false,
                        "expectedVersion" to 0,
                    )
                ),
                f.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, changed.statusCode(), changed.body())
        storageProbe.beforeRead = {
            error("Draft reference queries must not acquire file contents")
        }
        val result =
            get(
                f.worker,
                path(f) +
                    "?categoryIds=${f.category}&costCenterIds=${f.costCenter}&receiptRevisionIds=$revision",
            )
        assertEquals(200, result.statusCode(), result.body())
        val body = json.readTree(result.body())
        assertFalse(body["categories"][0]["active"].asBoolean())
        assertFalse(body["costCenters"][0]["active"].asBoolean())
        assertEquals("Archived operations", body["costCenters"][0]["name"].asString())
        assertEquals("READY", body["receipts"][0]["status"].asString())
    }

    @Test
    fun queryCardinalityAndEditingGrantsAreEnforcedBeforeReferenceAcquisition() {
        val f = expenseFixture()
        for ((name, maximum) in
            listOf("categoryIds" to 20, "costCenterIds" to 20, "receiptRevisionIds" to 60)) {
            val ids = List(maximum + 1) { UUID.randomUUID() }.joinToString(",")
            val response = get(f.worker, path(f) + "?$name=$ids")
            assertEquals(422, response.statusCode(), response.body())
            assertEquals(
                "invalid_expense_references",
                json.readTree(response.body())["code"].asString(),
            )
        }
        for (permission in
            listOf("company.read", "expenses.read", "expenses.pay", "expenses.team.read")) {
            val id = expenseMember(f.company, listOf(permission))
            val browser = client()
            login(browser, "$id@example.test")
            assertEquals(403, get(browser, path(f)).statusCode())
        }
        val duplicated = get(f.worker, path(f) + "?categoryIds=${f.category},${f.category}")
        assertEquals(200, duplicated.statusCode(), duplicated.body())
        assertEquals(1, json.readTree(duplicated.body())["categories"].size())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun expiredMfaCannotAuthorizeMetadataAfterGuardOrAcquisitionWait(metadata: Boolean) {
        val scene = prepare()
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val account = AccountLockProbe.Barrier(actor.accountId)
        val read = ExpenseReadProbe.Barrier(scene.company, "receipt-metadata")
        if (metadata) readProbe.current.set(read) else accountProbe.current.set(account)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<*>> {
                    draftReferenceReader()
                        .execute(
                            actor,
                            scene.fixture.employee,
                            ExpenseDraftReferenceQuery(receiptRevisionIds = setOf(scene.revision)),
                        )
                }
            try {
                assertTrue(
                    (if (metadata) read.entered else account.entered).await(5, TimeUnit.SECONDS)
                )
                clock.set(clock.instant().plusSeconds(2))
                read.release.countDown()
                account.release.countDown()
                assertEquals(
                    "mfa_required",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                read.release.countDown()
                account.release.countDown()
                readProbe.current.set(null)
                accountProbe.current.set(null)
            }
        }
        assertTrue(
            draftReferenceReader()
                .execute(
                    actor.copy(mfaVerifiedAt = clock.instant()),
                    scene.fixture.employee,
                    ExpenseDraftReferenceQuery(receiptRevisionIds = setOf(scene.revision)),
                ) is Result.Success
        )
    }

    @Test
    fun originalAndCurrentPermissionsBothConstrainTheMetadataRead() {
        val scene = prepare()
        val query = ExpenseDraftReferenceQuery(categoryIds = setOf(scene.fixture.category))
        val beforeGrant = scene.actor.copy(permissions = setOf("company.read"))
        assertEquals(
            "access_denied",
            (draftReferenceReader().execute(beforeGrant, scene.fixture.employee, query)
                    as Result.Failed)
                .failure
                .code,
        )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='expenses.self.manage'",
                scene.company,
                scene.actor.accountId,
            )
        assertEquals(
            "access_denied",
            (draftReferenceReader().execute(scene.actor, scene.fixture.employee, query)
                    as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun interruptedGuardReleasesResourcesAndTheExecutorCanReadAgain() {
        val scene = prepare()
        val barrier = AccountLockProbe.Barrier(scene.actor.accountId)
        accountProbe.current.set(barrier)
        val query = ExpenseDraftReferenceQuery(receiptRevisionIds = setOf(scene.revision))
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<*>> {
                    draftReferenceReader().execute(scene.actor, scene.fixture.employee, query)
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                barrier.release.countDown()
                assertThrows(CancellationException::class.java) {
                    pending.get(10, TimeUnit.SECONDS)
                }
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
            assertTrue(
                pool
                    .submit<Result<*>> {
                        draftReferenceReader().execute(scene.actor, scene.fixture.employee, query)
                    }
                    .get(10, TimeUnit.SECONDS) is Result.Success
            )
        }
    }

    private fun path(f: ExpenseFixture, employee: UUID = f.employee) =
        "/api/v1/companies/${f.company}/expenses/me/employments/$employee/draft-references"
}
