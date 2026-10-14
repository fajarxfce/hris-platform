package dev.fajar.hris

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollAmendmentDraftLimitsHttpTest : PayrollAmendmentDraftApiFixture() {
    @Test
    fun repeatedRuleFailuresAreBoundedAndTheExhaustedDraftCanStillBeCancelled() {
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
        payrollBody(saveDraft(f, draft.id, body, draft.operation))
        repeat(32) { version ->
            assertEquals(
                version + 1L,
                payrollBody(draftCommand(draft, "calculate", version.toLong()))["version"].asLong(),
            )
        }
        val before = draftCounts(f)
        payrollError(
            draftCommand(draft, "calculate", 32),
            409,
            "payroll_amendment_calculation_limit",
        )
        assertEquals(before, draftCounts(f))
        val exhausted = draftView(draft)
        assertEquals(32, exhausted["latestCalculation"]["number"].asInt())
        assertEquals(
            setOf("EDIT", "CANCEL"),
            exhausted["availableActions"].toList().map { it.asString() }.toSet(),
        )
        assertEquals(25, exhausted["history"]["items"].size())
        assertEquals("24", exhausted["history"]["nextCursor"].asString())
        val tail =
            payrollBody(
                get(f.maker.client, draftsPath(f) + "/${draft.id}/history?after=24&limit=25")
            )
        assertEquals(8, tail["items"].size())
        assertTrue(tail["nextCursor"].isNull)
        payrollBody(draftCommand(draft, "cancel", 32))
        assertEquals("CANCELLED", draftView(draft)["amendment"]["summary"]["status"].asString())
        assertEquals(32, count(f.payroll.company, "payroll_amendment_calculations"))
        assertEquals(0, count(f.payroll.company, "payroll_amendment_results"))
    }
}
