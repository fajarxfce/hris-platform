package dev.fajar.hris

import java.math.BigDecimal
import java.time.YearMonth
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollAmendmentDraftHttpTest : PayrollAmendmentDraftApiFixture() {
    @Test
    fun savedDraftsRetainEveryCalculationAndReplayTheirOriginalReceiptsAfterEditingAndCancellation() {
        val draft = draftFixture()
        val f = draft.source
        val path = draftsPath(f) + "/${draft.id}"
        val original = payrollBody(get(f.payroll.owner.client, amendmentPath(f)))
        val immutableCounts =
            listOf(
                    "payroll_assessments",
                    "payroll_run_results",
                    "payroll_payment_batches",
                    "background_jobs",
                    "approval_requests",
                )
                .associateWith { count(f.payroll.company, it) }
        val first = draftView(draft)
        assertEquals(0, first["amendment"]["summary"]["version"].asLong())
        assertEquals("DRAFT", first["amendment"]["summary"]["status"].asString())
        assertEquals(
            setOf("EDIT", "CALCULATE", "CANCEL"),
            first["availableActions"].toList().map { it.asString() }.toSet(),
        )
        assertTrue(first["latestCalculation"].isNull)
        val calculateKey = UUID.randomUUID()
        val receipt = payrollBody(draftCommand(draft, "calculate", 0, calculateKey))
        assertEquals(1, receipt["version"].asLong())
        val calculated = draftView(draft)
        assertEquals("CALCULATED", calculated["amendment"]["summary"]["status"].asString())
        val firstCalculation = calculated["latestCalculation"]["id"].asString()
        val result = payrollBody(get(f.maker.client, "$path/calculations/$firstCalculation"))
        assertEquals("SUCCEEDED", result["summary"]["status"].asString())
        assertEquals(1, result["summary"]["number"].asInt())
        assertEquals(original["calculation"], result["preview"]["calculations"][0]["original"])
        assertEquals(
            0,
            BigDecimal("1000000")
                .compareTo(BigDecimal(result["summary"]["difference"]["taxableGross"].asString())),
        )
        val preparation = amendmentPreparation(f)
        val editedBody =
            draftBody(f, preparation, 1, listOf(remuneration(preparation, difference = "2000000")))
        assertEquals(2, payrollBody(saveDraft(f, draft.id, editedBody))["version"].asLong())
        val edited = draftView(draft)
        assertEquals("DRAFT", edited["amendment"]["summary"]["status"].asString())
        assertEquals(firstCalculation, edited["latestCalculation"]["id"].asString())
        assertEquals(
            result,
            payrollBody(get(f.maker.client, "$path/calculations/$firstCalculation")),
        )
        payrollBody(draftCommand(draft, "calculate", 2))
        assertEquals(2, draftView(draft)["latestCalculation"]["number"].asInt())
        assertNotEquals(firstCalculation, draftView(draft)["latestCalculation"]["id"].asString())
        val cancelKey = UUID.randomUUID()
        val cancelled = payrollBody(draftCommand(draft, "cancel", 3, cancelKey))
        assertEquals(4, cancelled["version"].asLong())
        val counts = draftCounts(f)
        assertEquals(receipt, payrollBody(draftCommand(draft, "calculate", 0, calculateKey)))
        assertEquals(cancelled, payrollBody(draftCommand(draft, "cancel", 3, cancelKey)))
        assertEquals(
            0,
            payrollBody(saveDraft(f, draft.id, draft.body, draft.operation))["version"].asLong(),
        )
        assertEquals(counts, draftCounts(f))
        assertEquals(
            immutableCounts,
            immutableCounts.keys.associateWith { count(f.payroll.company, it) },
        )
        assertEquals(original, payrollBody(get(f.payroll.owner.client, amendmentPath(f))))
        val final = draftView(draft)
        assertEquals("CANCELLED", final["amendment"]["summary"]["status"].asString())
        assertEquals(0, final["availableActions"].size())
        assertEquals(
            listOf("SAVED", "CALCULATED", "SAVED", "CALCULATED", "CANCELLED"),
            final["history"]["items"].toList().map { it["action"].asString() },
        )
        val revision = payrollBody(get(f.maker.client, "$path/revisions/0"))
        assertEquals(first["amendment"], revision)
        payrollError(draftCommand(draft, "calculate", 4), 409, "payroll_amendment_not_editable")
        payrollError(
            saveDraft(f, draft.id, draftBody(f, preparation, 4)),
            409,
            "payroll_amendment_not_editable",
        )
        payrollError(
            get(f.maker.client, "$path/revisions/5"),
            404,
            "payroll_amendment_revision_not_found",
        )
    }

    @Test
    fun ruleFailureIsARetainedAttemptAndItsReceiptSurvivesALaterSuccessfulCalculation() {
        val f = amendmentFixture()
        val preparation = amendmentPreparation(f)
        val compensation =
            preparation["assessments"][0]["compensation"]
                .let { node -> node.propertyNames().associateWith { node[it] } }
                .toMutableMap<String, Any?>()
        compensation["payBasis"] = null
        val body =
            draftBody(
                f,
                preparation,
                changes =
                    listOf(mapOf("assessmentId" to f.assessment, "compensation" to compensation)),
            )
        val draft = DraftFixture(f, UUID.randomUUID(), UUID.randomUUID(), body)
        payrollBody(saveDraft(f, draft.id, draft.body, draft.operation))
        val key = UUID.randomUUID()
        val receipt = payrollBody(draftCommand(draft, "calculate", 0, key))
        val failed = draftView(draft)
        assertEquals("DRAFT", failed["amendment"]["summary"]["status"].asString())
        assertEquals("CALCULATION_FAILED", failed["amendment"]["summary"]["action"].asString())
        val calculation = failed["latestCalculation"]
        assertEquals("FAILED", calculation["status"].asString())
        assertEquals("payroll_pay_basis_required", calculation["failure"]["code"].asString())
        assertEquals(
            f.assessment.toString(),
            calculation["failure"]["parameters"]["assessmentId"].asString(),
        )
        val path = draftsPath(f) + "/${draft.id}/calculations/${calculation["id"].asString()}"
        val attempt = payrollBody(get(f.maker.client, path))
        assertTrue(attempt["preview"].isNull)
        assertEquals(0, count(f.payroll.company, "payroll_amendment_results"))
        payrollBody(saveDraft(f, draft.id, draftBody(f, preparation, 1)))
        payrollBody(draftCommand(draft, "calculate", 2))
        assertEquals("SUCCEEDED", draftView(draft)["latestCalculation"]["status"].asString())
        val counts = draftCounts(f)
        assertEquals(receipt, payrollBody(draftCommand(draft, "calculate", 0, key)))
        assertEquals(attempt, payrollBody(get(f.maker.client, path)))
        assertEquals(counts, draftCounts(f))
    }

    @Test
    fun aNewPublishedMonthRequiresExplicitDraftReviewWhileOldDraftsAndReceiptsStayReadable() {
        val draft = draftFixture()
        val f = draft.source
        val stale = draftFixture(f)
        val original = draftView(draft)
        val next = followingMonth(f.publication.calculation, YearMonth.of(2026, 10))
        publishTaxRun(next, beginTaxRun(next))
        val refreshed =
            f.copy(
                maker =
                    f.maker.copy(csrf = login(f.maker.client, "${f.maker.account}@example.test"))
            )
        val current = draft.copy(source = refreshed)
        val counts = draftCounts(f)
        payrollError(
            draftCommand(current, "calculate", 0),
            409,
            "payroll_amendment_sources_changed",
        )
        val oldPreparation = json.readTree(draft.body)
        val oldBody =
            json.writeValueAsString(
                oldPreparation.propertyNames().associateWith { oldPreparation[it] } +
                    ("expectedVersion" to 0)
            )
        payrollError(
            saveDraft(refreshed, draft.id, oldBody),
            409,
            "payroll_amendment_sources_changed",
        )
        assertEquals(
            0,
            payrollBody(saveDraft(refreshed, draft.id, draft.body, draft.operation))["version"]
                .asLong(),
        )
        assertEquals(counts, draftCounts(f))
        assertEquals(original, draftView(current))
        val updated = amendmentPreparation(refreshed)
        payrollBody(saveDraft(refreshed, draft.id, draftBody(refreshed, updated, 0)))
        payrollBody(draftCommand(current, "calculate", 1))
        assertEquals(2, draftView(current)["latestCalculation"]["assessmentCount"].asInt())
        // A separate stale draft can be cancelled without adopting the new published month.
        val staleCurrent = stale.copy(source = refreshed)
        payrollBody(draftCommand(staleCurrent, "cancel", 0))
        assertEquals(
            "CANCELLED",
            draftView(staleCurrent)["amendment"]["summary"]["status"].asString(),
        )
        payrollBody(draftCommand(current, "cancel", 2))
        assertEquals("CANCELLED", draftView(current)["amendment"]["summary"]["status"].asString())
    }

    @Test
    fun metadataPagesAreBoundedFilteredAndDoNotLoadFullProposalOrCalculationPayloads() {
        val first = draftFixture()
        val f = first.source
        val second = draftFixture(f)
        payrollBody(draftCommand(first, "calculate", 0))
        payrollBody(draftCommand(first, "cancel", 1))
        val firstPage = payrollBody(get(f.maker.client, draftsPath(f) + "?year=2026&limit=1"))
        val lastPage =
            payrollBody(
                get(
                    f.maker.client,
                    draftsPath(f) + "?year=2026&limit=1&after=" + firstPage["nextCursor"].asString(),
                )
            )
        assertEquals(
            setOf(first.id.toString(), second.id.toString()),
            (firstPage["items"].toList() + lastPage["items"].toList())
                .map { it["id"].asString() }
                .toSet(),
        )
        assertTrue(lastPage["nextCursor"].isNull)
        val filtered =
            payrollBody(
                get(
                    f.maker.client,
                    draftsPath(f) + "?year=2026&status=CANCELLED&employeeId=${f.payroll.employee}",
                )
            )
        assertEquals(first.id.toString(), filtered["items"].single()["id"].asString())
        assertFalse(filtered["items"].single().has("terms"))
        assertFalse(filtered["items"].single().has("calculation"))
        assertEquals(
            0,
            payrollBody(get(f.maker.client, draftsPath(f) + "?year=2026&query=%25"))["items"].size(),
        )
        val path = draftsPath(f) + "/${first.id}/history"
        val start = payrollBody(get(f.maker.client, "$path?limit=2"))
        assertEquals(listOf(0L, 1L), start["items"].toList().map { it["version"].asLong() })
        assertEquals("1", start["nextCursor"].asString())
        val end = payrollBody(get(f.maker.client, "$path?after=1&limit=2"))
        assertEquals(2, end["items"].single()["version"].asLong())
        assertTrue(end["nextCursor"].isNull)
        for (query in
            listOf(
                "year=2023",
                "year=2026&limit=101",
                "year=2026&limit=0",
                "year=2026&query=" + "a".repeat(121),
            )) payrollError(get(f.maker.client, draftsPath(f) + "?$query"), 422, "invalid_page")
        payrollError(get(f.maker.client, "$path?after=-1"), 422, "invalid_page")
        payrollError(get(f.maker.client, "$path?limit=101"), 422, "invalid_page")
    }
}
