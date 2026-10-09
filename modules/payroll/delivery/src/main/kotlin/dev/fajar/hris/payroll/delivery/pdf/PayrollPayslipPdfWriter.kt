package dev.fajar.hris.payroll.delivery.pdf

import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.core.domain.FailureKind
import dev.fajar.hris.core.http.DomainFailureException
import dev.fajar.hris.payroll.domain.entities.PayrollPayslip
import java.io.ByteArrayOutputStream
import java.util.GregorianCalendar
import java.util.concurrent.Semaphore
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType0Font

/**
 * Owns synchronous representation capacity; no queue, scheduler, cache, or database transaction.
 */
class PayrollPayslipPdfWriter(
    concurrency: Int = 4,
    private val render: (PayrollPayslip, PayrollDocumentLanguage) -> ByteArray =
        { payslip, language ->
            renderPayrollPayslipPdf(payslip, language)
        },
) {
    private val permits = Semaphore(concurrency.also { require(it in 1..8) })

    fun write(payslip: PayrollPayslip, language: PayrollDocumentLanguage): ByteArray {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        if (!permits.tryAcquire())
            throw DomainFailureException(Failure(FailureKind.RATE_LIMITED, "payroll_pdf_capacity"))
        try {
            val bytes = render(payslip, language)
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            return bytes
        } finally {
            permits.release()
        }
    }
}

internal val PAYSLIP_PDF_LIMIT = Failure(FailureKind.UNAVAILABLE, "payroll_pdf_generation_limit")
internal const val PAYSLIP_PDF_MAXIMUM_BYTES = 1048576

/** Serializes the authorized immutable snapshot; PDF layout does not recalculate payroll. */
internal fun renderPayrollPayslipPdf(
    payslip: PayrollPayslip,
    language: PayrollDocumentLanguage,
    nanoTime: () -> Long = System::nanoTime,
): ByteArray =
    PDDocument().use { document ->
        val start = nanoTime()
        document.documentId =
            payslip.summary.id.mostSignificantBits xor payslip.summary.id.leastSignificantBits
        document.documentInformation.apply {
            title = PayslipText.TITLE.value(language)
            producer = "HRIS Platform / PDFBox"
            creationDate =
                GregorianCalendar.from(payslip.summary.publishedAt.atZone(java.time.ZoneOffset.UTC))
        }
        val font =
            requireNotNull(
                    PayrollPayslipPdfWriter::class
                        .java
                        .getResourceAsStream("/fonts/NotoSans-Regular.ttf")
                ) {
                    "Packaged PDF font is unavailable"
                }
                .use { PDType0Font.load(document, it, true) }
        PayslipPdfCanvas(document, font, start, nanoTime).use { canvas ->
            writePayslipLayout(canvas, payslip, language)
        }
        for ((index, page) in document.pages.withIndex()) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, false).use {
                stream ->
                stream.beginText()
                stream.setFont(font, 8f)
                stream.newLineAtOffset(48f, 26f)
                stream.showText(
                    "${PayslipText.PAGE.value(language)} ${index + 1} / ${document.numberOfPages}"
                )
                stream.endText()
            }
        }
        PdfOutputBuffer().use { output ->
            document.save(output)
            if (nanoTime() - start > 5_000_000_000L)
                throw DomainFailureException(
                    Failure(FailureKind.UNAVAILABLE, "payroll_pdf_generation_timeout")
                )
            output.toByteArray()
        }
    }

private class PdfOutputBuffer : ByteArrayOutputStream() {
    override fun write(value: Int) {
        if (size() >= PAYSLIP_PDF_MAXIMUM_BYTES) throw DomainFailureException(PAYSLIP_PDF_LIMIT)
        super.write(value)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (length > PAYSLIP_PDF_MAXIMUM_BYTES - size())
            throw DomainFailureException(PAYSLIP_PDF_LIMIT)
        super.write(bytes, offset, length)
    }
}
