package dev.fajar.hris.payroll.data

import dev.fajar.hris.payroll.data.mappers.*
import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.payroll.domain.entities.*
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

class PayrollSnapshotMappingTest {
    private val json = jacksonObjectMapper()

    @Test
    fun retainedInsuranceArraysKeepTheirOriginalOrderAcrossDomainRoundTrips() {
        val names = InsuranceProgram.entries.map { it.name }
        for (offset in names.indices) {
            val order = names.drop(offset) + names.take(offset)
            val source = order.toMutableList()
            val stored =
                CompensationTermsData(
                    "12000000.00",
                    emptyList(),
                    "GROSS",
                    TaxRegistrationData(
                        "RESIDENT",
                        "TK0",
                        "ID",
                        null,
                        null,
                        LocalDate.of(2026, 1, 1),
                        "verified",
                    ),
                    "12000000.00",
                    source,
                    null,
                    "LOW",
                    0,
                    "0",
                    "0",
                    "0",
                )
            val raw = json.writeValueAsString(stored)
            val domain = json.readValue(raw, CompensationTermsData::class.java).toTerms()
            assertEquals(
                json.readTree(raw),
                json.readTree(json.writeValueAsString(domain.toData())),
            )
            assertThrows(IllegalArgumentException::class.java) {
                stored.copy(insurancePrograms = order + order.first()).toTerms()
            }
            source.clear()
            assertEquals(order, domain.toData().insurancePrograms.toList())
            assertThrows(UnsupportedOperationException::class.java) {
                (domain.insurancePrograms as MutableSet).clear()
            }
        }
    }

    @Test
    fun retainedEmploymentDateArraysKeepTheirOrderWithoutAliasingStorageValues() {
        val order = (1..6).map { LocalDate.of(2026, 1, it) }.reversed()
        val source = order.toMutableList()
        val stored = PayrollPayUnitsData("6.00", "6.00", "0.00", source)
        val raw = json.writeValueAsString(stored)
        val domain = json.readValue(raw, PayrollPayUnitsData::class.java).toDomain()
        assertEquals(
            json.readTree(raw),
            json.readTree(json.writeValueAsString(domain.toSnapshotData())),
        )
        assertThrows(IllegalArgumentException::class.java) {
            stored.copy(employedDates = order + order.first()).toDomain()
        }
        source.clear()
        assertEquals(order, domain.employedDates.toList())
        assertEquals(order, domain.toSnapshotData().employedDates.toList())
        assertThrows(UnsupportedOperationException::class.java) {
            (domain.employedDates as MutableSet).clear()
        }
    }

    @Test
    fun employmentDateSerializationPreservesTheCalculationAndOwnsItsCopy() {
        val dates = (1..6).map { LocalDate.of(2026, 1, it) }
        for (order in listOf(dates, dates.reversed())) {
            val source = order.toCollection(LinkedHashSet())
            val stored =
                PayrollPayUnits(BigDecimal("6.00"), BigDecimal("6.00"), BigDecimal.ZERO, source)
                    .toSnapshotData()
            source.clear()
            assertEquals(order, stored.employedDates.toList())
            assertThrows(UnsupportedOperationException::class.java) {
                (stored.employedDates as MutableList).clear()
            }
        }
    }
}
