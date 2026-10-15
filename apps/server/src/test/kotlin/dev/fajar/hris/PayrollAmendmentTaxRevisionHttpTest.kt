package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import java.time.YearMonth
import java.util.UUID
import org.jooq.JSONB
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.node.ObjectNode

class PayrollAmendmentTaxRevisionHttpTest : PayrollAmendmentDraftApiFixture() {
    @Test
    fun retainedSuffixNamesItsRevisedPredecessorAndRejectsForgedTaxHistoryAtomically() {
        val source = amendmentFixture()
        val next = followingMonth(source.publication.calculation, YearMonth.of(2026, 10))
        publishTaxRun(next, beginTaxRun(next))
        val current =
            source.copy(
                maker =
                    source.maker.copy(
                        csrf = login(source.maker.client, "${source.maker.account}@example.test")
                    )
            )
        val draft = draftFixture(current)
        val before = draftCounts(current)
        val key = UUID.randomUUID()
        for (corruption in listOf("revision", "reference", "amount", "opening", "calculation")) {
            draftProbe.transformResults = { rows ->
                rows.onEach { row ->
                    if (row.ordinal == 2 && corruption == "revision")
                        row.previousAssessmentRevision = 0
                    if (
                        (row.ordinal == 2 &&
                            corruption in setOf("reference", "amount", "calculation")) ||
                            (row.ordinal == 1 && corruption == "opening")
                    ) {
                        val tree = json.readTree(row.facts.data()) as ObjectNode
                        val history = tree["taxHistory"] as ObjectNode
                        when (corruption) {
                            "reference" ->
                                history.put("reference", "assessment:${source.assessment}")
                            "amount",
                            "opening" -> (history["history"] as ObjectNode).put("withheld", "1")
                        }
                        row.facts = JSONB.valueOf(json.writeValueAsString(tree))
                        if (corruption in setOf("calculation", "amount", "opening")) {
                            val calculation = json.readTree(row.calculation.data()) as ObjectNode
                            (calculation["taxInput"]["history"] as ObjectNode).put("withheld", "1")
                            row.calculation = JSONB.valueOf(json.writeValueAsString(calculation))
                        }
                    }
                }
            }
            val failed = draftCommand(draft, "calculate", 0, key)
            assertEquals(409, failed.statusCode(), corruption)
            draftProbe.clear()
            assertEquals(before, draftCounts(current))
        }
        val receipt = payrollBody(draftCommand(draft, "calculate", 0, key))
        val calculationId = draftView(draft)["latestCalculation"]["id"].asString()
        val result =
            payrollBody(
                get(
                    current.maker.client,
                    draftsPath(current) + "/${draft.id}/calculations/$calculationId",
                )
            )
        val acquired =
            transactions.run(payrollActor(current.payroll, current.maker)) {
                amendments.calculation(
                    current.payroll.company,
                    draft.id,
                    UUID.fromString(calculationId),
                )
            }
        val second = (acquired as Result.Success).value!!.preview!!.calculations[1]
        assertEquals(1, second.target.previousAssessmentRevision)
        assertEquals("assessment:${source.assessment}:1", second.facts.taxHistory.reference)
        assertEquals(0, result["preview"]["calculations"][1]["source"]["revision"].asLong())
        assertEquals(receipt, payrollBody(draftCommand(draft, "calculate", 0, key)))
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from payroll_assessment_heads where company_id=? and revision<>0",
                    Int::class.java,
                    current.payroll.company,
                ),
        )
    }
}
