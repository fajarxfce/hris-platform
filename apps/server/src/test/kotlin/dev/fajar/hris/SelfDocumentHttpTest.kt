package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SelfDocumentHttpTest : SelfDocumentApiFixture() {
    @Test
    fun directoryExposesOnlyOwnedReferencesWithCompanyDateAndBoundedPages() {
        val (administrator, owner) = selfFixture()
        database()
            .update("update companies set timezone='Pacific/Honolulu' where id=?", owner.company)
        val other = employee(administrator.browser, administrator.csrf, owner.company)
        val path = "${owner.path}/me/employments"
        val response = get(owner.browser, path)
        assertEquals(200, response.statusCode(), response.body())
        val directory = json.readTree(response.body())
        assertEquals(owner.actor.accountId.toString(), directory["accountId"].asString())
        assertEquals(owner.company.toString(), directory["companyId"].asString())
        assertEquals(
            LocalDate.ofInstant(clock.instant(), ZoneId.of("Pacific/Honolulu")).toString(),
            directory["asOf"].asString(),
        )
        assertEquals("Pacific/Honolulu", directory["timezone"].asString())
        val items = directory["employments"]["items"]
        assertEquals(1, items.size())
        assertEquals(owner.employee.toString(), items[0]["id"].asString())
        assertEquals(
            setOf("id", "employeeNumber", "status", "version"),
            items[0].properties().map { it.key }.toSet(),
        )
        assertTrue(directory["employments"]["nextCursor"].isNull)
        assertFalse(response.body().contains(other.toString()))
        assertFalse(response.body().contains("Example employee"))
        failure(get(administrator.browser, path), 403, "access_denied")
        for (query in listOf("limit=0", "limit=51", "after=", "after=${"A".repeat(33)}")) {
            failure(get(owner.browser, "$path?$query"), 422, "invalid_page")
        }
        val number = items[0]["employeeNumber"].asString()
        val tail = get(owner.browser, "$path?after=$number&limit=1")
        assertEquals(200, tail.statusCode(), tail.body())
        assertEquals(0, json.readTree(tail.body())["employments"]["items"].size())
    }

    @Test
    fun evidencePagesExposeOnlyPublishedRevisionsOfTheSelectedClassification() {
        val (administrator, owner) = selfFixture()
        val documents = listOf(UUID.randomUUID(), UUID.randomUUID()).sortedBy(UUID::toString)
        val revisions = documents.associateWith { ready(administrator, it) }
        begin(administrator)
        val receipt = UUID.randomUUID()
        val restricted = UUID.randomUUID()
        for ((document, classification) in listOf(receipt to "RECEIPT", restricted to "HR_ONLY")) {
            val revision = UUID.randomUUID()
            val started =
                start(
                    administrator,
                    uploadInput(
                        administrator,
                        pdf,
                        document,
                        revision,
                        classification = classification,
                    ),
                )
            assertEquals(200, started.statusCode(), started.body())
            assertEquals(200, upload(administrator, revision, pdf).statusCode())
            assertTrue(
                run(administrator, beginValidation(administrator, revision)) is Result.Success
            )
        }
        val other = employee(administrator.browser, administrator.csrf, owner.company)
        ready(administrator.copy(employee = other), UUID.randomUUID())
        storageProbe.reads.set(0)

        val firstResponse = evidence(owner, "&limit=1")
        assertEquals(200, firstResponse.statusCode(), firstResponse.body())
        val first = json.readTree(firstResponse.body())
        assertEquals(owner.actor.accountId.toString(), first["accountId"].asString())
        assertEquals(owner.company.toString(), first["companyId"].asString())
        assertEquals(owner.employee.toString(), first["employmentId"].asString())
        assertEquals("PERSONAL", first["classification"].asString())
        val item = first["documents"]["items"].single()
        assertEquals(
            setOf(
                "documentId",
                "revisionId",
                "revisionNumber",
                "title",
                "fileName",
                "mediaType",
                "size",
                "sha256",
                "validatedAt",
            ),
            item.properties().map { it.key }.toSet(),
        )
        assertEquals(documents.first().toString(), item["documentId"].asString())
        assertEquals(
            revisions.getValue(documents.first()).toString(),
            item["revisionId"].asString(),
        )
        assertEquals(pdf.size.toLong(), item["size"].asLong())
        assertEquals(digest(pdf), item["sha256"].asString())
        assertEquals("application/pdf", item["mediaType"].asString())
        assertEquals(1, item["revisionNumber"].asInt())
        val cursor = first["documents"]["nextCursor"].asString()
        assertEquals(documents.first().toString(), cursor)
        val secondResponse = evidence(owner, "&limit=1&after=$cursor")
        assertEquals(200, secondResponse.statusCode(), secondResponse.body())
        val second = json.readTree(secondResponse.body())["documents"]
        assertEquals(documents.last().toString(), second["items"].single()["documentId"].asString())
        assertTrue(second["nextCursor"].isNull)
        val receipts = evidence(owner, "&classification=RECEIPT")
        assertEquals(200, receipts.statusCode(), receipts.body())
        assertEquals(
            receipt.toString(),
            json.readTree(receipts.body())["documents"]["items"].single()["documentId"].asString(),
        )
        assertEquals(0, storageProbe.reads.get())
    }

    @Test
    fun replacementStaysOnTheAcceptedRevisionUntilValidationPublishesIt() {
        val (administrator, owner) = selfFixture()
        val document = UUID.randomUUID()
        val accepted = ready(administrator, document)
        val header = get(administrator.browser, "${administrator.path}/$document")
        assertEquals(200, header.statusCode(), header.body())
        val replacement =
            filled(
                administrator,
                document = document,
                version = json.readTree(header.body())["version"].asLong(),
            )
        assertEquals(
            accepted.toString(),
            json
                .readTree(evidence(owner).body())["documents"]["items"]
                .single()["revisionId"]
                .asString(),
        )
        val lease = beginValidation(administrator, replacement)
        assertEquals(
            accepted.toString(),
            json
                .readTree(evidence(owner).body())["documents"]["items"]
                .single()["revisionId"]
                .asString(),
        )
        assertTrue(run(administrator, lease) is Result.Success)
        val current = json.readTree(evidence(owner).body())["documents"]["items"].single()
        assertEquals(replacement.toString(), current["revisionId"].asString())
        assertEquals(2, current["revisionNumber"].asInt())
    }

    @Test
    fun foreignScopesBroadHrGrantsAndInvalidPagesCannotReadSelfEvidence() {
        val (administrator, owner) = selfFixture()
        val other = employee(administrator.browser, administrator.csrf, owner.company)
        ready(administrator.copy(employee = other), UUID.randomUUID())
        failure(evidence(owner.copy(employee = other)), 404, "employee_not_found")
        failure(evidence(owner.copy(employee = UUID.randomUUID())), 404, "employee_not_found")
        failure(evidence(administrator), 403, "access_denied")
        val foreign = company(administrator.browser, administrator.csrf)
        assertEquals(
            403,
            evidence(owner.copy(actor = owner.actor.copy(companyId = foreign))).statusCode(),
        )
        for (query in listOf("&limit=0", "&limit=51")) failure(
            evidence(owner, query),
            422,
            "invalid_document_page",
        )
        failure(evidence(owner, "&classification=HR_ONLY"), 422, "invalid_document_classification")
        for (permission in listOf("documents.read", "people.self.read")) {
            val account = selfFixture(permission).owner
            failure(evidence(account), 403, "access_denied")
            failure(get(account.browser, "${account.path}/me/employments"), 403, "access_denied")
        }
    }
}
