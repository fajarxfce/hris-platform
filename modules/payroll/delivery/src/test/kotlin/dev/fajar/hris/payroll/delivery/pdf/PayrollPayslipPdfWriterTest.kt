package dev.fajar.hris.payroll.delivery.pdf

import dev.fajar.hris.core.http.DomainFailureException
import dev.fajar.hris.payroll.domain.entities.*
import java.io.IOException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollPayslipPdfWriterTest {
    private fun payslip(): PayrollPayslip {
        val month = YearMonth.of(2026, 9)
        val gross = BigDecimal("10000000.00")
        val tax =
            IncomeTaxCalculation(
                "ID-PMK168-2023-v1",
                gross,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal("250000.00"),
                BigDecimal("9650000.00"),
                null,
                null,
                null,
                null,
                null,
                null,
            )
        return PayrollPayslip(
            PayrollPayslipSummary(
                UUID.fromString("ba7f8db5-b7b6-4d1a-a9e0-2ff52541e4b3"),
                UUID.randomUUID(),
                "EMP-001",
                "Élodie Núñez",
                month,
                LocalDate.of(2026, 10, 1),
                gross,
                tax.withheld,
                tax.takeHome,
                Instant.parse("2026-09-30T08:00:00Z"),
            ),
            "ACME",
            "PT Contoh Sejahtera",
            PayrollMonthlyCalculation(
                PayrollPayUnits(BigDecimal(30), BigDecimal(30), BigDecimal.ZERO, emptySet()),
                listOf(PayrollEarningLine(PayrollEarningKind.BASIC, "BASIC", null, gross, true)),
                emptyList(),
                null,
                emptyList(),
                IncomeTaxInput(
                    month,
                    TaxResidency.RESIDENT,
                    PtkpStatus.TK0,
                    TaxTreatment.GROSS,
                    gross,
                    otherNetDeductions = BigDecimal("100000.00"),
                ),
                tax,
                BigDecimal("100000.00"),
                gross,
            ),
        )
    }

    @Test
    fun unicodeFontsLocalesAndRetainedAmountsProduceDeterministicPrivateDocuments() {
        val model = payslip()
        for (language in PayrollDocumentLanguage.entries) {
            val writer = PayrollPayslipPdfWriter()
            val bytes = writer.write(model, language)
            assertArrayEquals(bytes, writer.write(model, language))
            assertTrue(bytes.size in 1..PAYSLIP_PDF_MAXIMUM_BYTES)
            Loader.loadPDF(bytes).use { document ->
                val text = PDFTextStripper().getText(document)
                assertTrue(text.contains("Élodie Núñez"), text)
                assertTrue(text.contains("PT Contoh Sejahtera"), text)
                assertTrue(text.contains(model.summary.id.toString()), text)
                assertTrue(
                    text.contains(
                        if (language == PayrollDocumentLanguage.ID) "Gaji bersih"
                        else "Take-home pay"
                    ),
                    text,
                )
                assertTrue(
                    text.contains(
                        if (language == PayrollDocumentLanguage.ID) "9.650.000,00"
                        else "9,650,000.00"
                    ),
                    text,
                )
                assertEquals(
                    model.summary.publishedAt,
                    document.documentInformation.creationDate.toInstant(),
                )
                for (page in document.pages) {
                    assertTrue(page.annotations.isEmpty())
                    for (name in page.resources.fontNames) assertTrue(
                        page.resources.getFont(name).isEmbedded
                    )
                }
                assertNull(document.documentCatalog.openAction)
            }
        }
    }

    @Test
    fun longNamesAndMaximumEarningLinesWrapAcrossFinitePagesWithoutDroppingAmounts() {
        val model = payslip()
        val earnings =
            (1..63).map { index ->
                PayrollEarningLine(
                    PayrollEarningKind.VARIABLE,
                    "C$index",
                    "Component $index " + "annual benefit ".repeat(11),
                    BigDecimal(index),
                    true,
                )
            }
        val bytes =
            PayrollPayslipPdfWriter()
                .write(
                    model.copy(
                        summary =
                            model.summary.copy(employeeName = "Long employee name ".repeat(10)),
                        calculation = model.calculation.copy(earnings = earnings),
                    ),
                    PayrollDocumentLanguage.EN,
                )
        Loader.loadPDF(bytes).use { document ->
            assertTrue(document.numberOfPages in 2..16)
            val text = PDFTextStripper().getText(document)
            assertTrue(text.contains("Component 1"), text)
            assertTrue(text.contains("Component 63"), text)
            assertTrue(text.contains("IDR 63.00"), text)
            assertTrue(
                text.contains("Page ${document.numberOfPages} / ${document.numberOfPages}"),
                text,
            )
        }
    }

    @Test
    fun unsupportedGlyphsSizeAndTimeLimitsFailBeforeReturningPartialBytes() {
        val writer = PayrollPayslipPdfWriter()
        val model = payslip()
        val unsupported =
            assertThrows(DomainFailureException::class.java) {
                writer.write(model.copy(companyName = "\u0378"), PayrollDocumentLanguage.EN)
            }
        assertEquals("payroll_pdf_text_unsupported", unsupported.failure.code)
        val oversized =
            assertThrows(DomainFailureException::class.java) {
                writer.write(
                    model.copy(companyName = "A".repeat(32769)),
                    PayrollDocumentLanguage.EN,
                )
            }
        assertEquals("payroll_pdf_generation_limit", oversized.failure.code)
        val calls = AtomicInteger()
        val expired =
            assertThrows(DomainFailureException::class.java) {
                renderPayrollPayslipPdf(model, PayrollDocumentLanguage.EN) {
                    if (calls.getAndIncrement() == 0) 0L else 5_000_000_001L
                }
            }
        assertEquals("payroll_pdf_generation_timeout", expired.failure.code)
        assertTrue(writer.write(model, PayrollDocumentLanguage.EN).isNotEmpty())
    }

    @Test
    fun capacityDoesNotQueueAndAParsingFailureReleasesItsPermit() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val attempts = AtomicInteger()
        val writer =
            PayrollPayslipPdfWriter(concurrency = 1) { _, _ ->
                if (attempts.getAndIncrement() == 0) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    throw IOException("Fixture parse failure")
                }
                byteArrayOf(1)
            }
        val model = payslip()
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Boolean> {
                    assertThrows(IOException::class.java) {
                        writer.write(model, PayrollDocumentLanguage.ID)
                    }
                    true
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val capacity =
                    assertThrows(DomainFailureException::class.java) {
                        writer.write(model, PayrollDocumentLanguage.ID)
                    }
                assertEquals("payroll_pdf_capacity", capacity.failure.code)
                assertEquals(1, attempts.get())
                release.countDown()
                assertTrue(pending.get(10, TimeUnit.SECONDS))
            } finally {
                release.countDown()
            }
        }
        assertArrayEquals(byteArrayOf(1), writer.write(model, PayrollDocumentLanguage.ID))
    }

    @Test
    fun cancellationBeforeAndAfterRenderingPropagatesAndReleasesCapacity() {
        val attempts = AtomicInteger()
        val writer =
            PayrollPayslipPdfWriter(concurrency = 1) { _, _ ->
                if (attempts.getAndIncrement() == 0) Thread.currentThread().interrupt()
                byteArrayOf(1)
            }
        val model = payslip()
        try {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedException::class.java) {
                writer.write(model, PayrollDocumentLanguage.EN)
            }
            assertEquals(0, attempts.get())
        } finally {
            Thread.interrupted()
        }
        try {
            assertThrows(InterruptedException::class.java) {
                writer.write(model, PayrollDocumentLanguage.EN)
            }
            assertEquals(1, attempts.get())
        } finally {
            Thread.interrupted()
        }
        assertArrayEquals(byteArrayOf(1), writer.write(model, PayrollDocumentLanguage.EN))
    }
}
