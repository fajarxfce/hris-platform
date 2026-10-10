package dev.fajar.hris.organization.domain

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.organization.domain.entities.*
import dev.fajar.hris.organization.domain.policies.validateOrganizationUnitSearch
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OrganizationSearchPolicyTest {
    @Test
    fun normalizesSearchWithoutChangingLiteralCharactersOrTheContinuation() {
        val search =
            OrganizationUnitSearch(
                UnitKind.DEPARTMENT,
                "  R&D_100%  ",
                false,
                "DEPARTMENT:RND_01",
                200,
            )
        assertEquals(
            search.copy(query = "R&D_100%"),
            (validateOrganizationUnitSearch(search) as Result.Success).value,
        )
        assertTrue(validateOrganizationUnitSearch(OrganizationUnitSearch()) is Result.Success)
        for (kind in UnitKind.entries) assertTrue(
            validateOrganizationUnitSearch(OrganizationUnitSearch(after = "${kind.name}:AB"))
                is Result.Success
        )
    }

    @Test
    fun rejectsOversizedQueriesAndMalformedOrIncompatibleCursors() {
        val tooLong =
            validateOrganizationUnitSearch(OrganizationUnitSearch(query = "a".repeat(121)))
        assertEquals("invalid_organization_search", (tooLong as Result.Failed).failure.code)
        for (search in
            listOf(
                OrganizationUnitSearch(limit = 0),
                OrganizationUnitSearch(limit = 201),
                OrganizationUnitSearch(after = ""),
                OrganizationUnitSearch(after = "BRANCH:A"),
                OrganizationUnitSearch(after = "BRANCH:${"A".repeat(33)}"),
                OrganizationUnitSearch(after = "DEPARTMENT:AB:CD"),
                OrganizationUnitSearch(after = "OTHER:AB"),
                OrganizationUnitSearch(kind = UnitKind.BRANCH, after = "DEPARTMENT:AB"),
            )) assertEquals(
            "invalid_page",
            (validateOrganizationUnitSearch(search) as Result.Failed).failure.code,
        )
    }
}
