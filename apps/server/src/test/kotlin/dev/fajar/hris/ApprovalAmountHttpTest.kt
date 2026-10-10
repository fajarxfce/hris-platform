package dev.fajar.hris

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApprovalAmountHttpTest : ApprovalApiFixture() {
    @Test
    fun storageScaleDoesNotInvalidateAnAcceptedWholeAmountOnTheNextEdit() {
        val f = leaveFixture()
        val id = UUID.randomUUID()
        val path = "/api/v1/companies/${f.company}/approvals/templates/$id"
        val amount = "999999999999999999"
        val saved = saveTemplate(f, id, templateBody(changes = mapOf("minimumAmount" to amount)))
        assertEquals(200, saved.statusCode(), saved.body())
        val detail = get(f.admin, path)
        assertEquals(200, detail.statusCode(), detail.body())
        val stored = json.readTree(detail.body()).get("minimumAmount").asString()
        assertEquals("$amount.00", stored)
        val update =
            saveTemplate(
                f,
                id,
                templateBody(
                    version = 0,
                    changes = mapOf("minimumAmount" to stored, "name" to "Reviewed threshold"),
                ),
            )
        assertEquals(200, update.statusCode(), update.body())
        assertEquals(1, json.readTree(update.body()).get("version").asInt())
        assertEquals(
            stored,
            json.readTree(get(f.admin, path).body()).get("minimumAmount").asString(),
        )
        assertEquals(
            stored,
            json.readTree(get(f.admin, "$path?revision=0").body()).get("minimumAmount").asString(),
        )
    }

    @Test
    fun precisionAndScaleLimitsRejectUnrepresentableAmountsBeforeMutation() {
        val f = leaveFixture()
        for ((amount, code) in
            listOf(
                "999999999999999999.01" to "invalid_approval_template",
                "1000000000000000000" to "invalid_approval_template",
                "1.001" to "invalid_approval_template",
                "1e100000" to "invalid_decimal_amount",
            )) {
            val id = UUID.randomUUID()
            assertCode(
                saveTemplate(f, id, templateBody(changes = mapOf("minimumAmount" to amount))),
                422,
                code,
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from approval_templates where company_id=? and id=?",
                        Int::class.java,
                        f.company,
                        id,
                    ),
            )
        }
    }
}
