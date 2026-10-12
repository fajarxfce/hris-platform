package dev.fajar.hris

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DocumentEmployeesAdmissionHttpTest : ClientPolicyApiFixture() {
    @Test
    fun employeeSelectionFollowsDocumentsAvailabilityIndependentlyOfPeople() {
        val f = fixture()
        employee(f.browser, f.csrf, f.company)
        val path = "${f.root}/documents/employees"
        assertEquals(
            200,
            save(f, changes = mapOf("disabledModules" to listOf("PEOPLE", "WORKFORCE", "LEAVE")))
                .statusCode(),
        )
        val accepted = get(f.browser, path)
        assertEquals(200, accepted.statusCode(), accepted.body())
        assertEquals(f.company.toString(), json.readTree(accepted.body())["companyId"].asString())
        assertEquals(200, save(f, 0, mapOf("disabledModules" to listOf("DOCUMENTS"))).statusCode())
        failure(get(f.browser, path), 403, "company_module_disabled")
        assertEquals(200, save(f, 1, mapOf("disabledModules" to emptyList<String>())).statusCode())
        assertEquals(200, get(f.browser, path).statusCode())
    }
}
