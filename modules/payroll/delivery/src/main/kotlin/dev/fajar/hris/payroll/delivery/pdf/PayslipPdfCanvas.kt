package dev.fajar.hris.payroll.delivery.pdf

import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.core.domain.FailureKind
import dev.fajar.hris.core.http.DomainFailureException
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font

/** Owns page/content streams, text measurement, wrapping, and finite rendering progress. */
internal class PayslipPdfCanvas(
    private val document: PDDocument,
    private val font: PDType0Font,
    private val start: Long,
    private val nanoTime: () -> Long,
) : AutoCloseable {
    private val margin = 48f
    private val width = PDRectangle.A4.width - 2 * margin
    private val characterMap = requireNotNull(font.cmapLookup)
    private var baseline = PDRectangle.A4.height - margin
    private var characters = 0
    private var stream = openPage()

    fun paragraph(text: String, size: Float = 10f) {
        for (line in wrap(text, width, size)) {
            reserve(size + 5f)
            draw(line, margin, baseline, size)
            baseline -= size + 5f
        }
    }

    fun row(label: String, value: String, emphasized: Boolean = false) {
        val size = if (emphasized) 12f else 10f
        val valueLines = wrap(value, 190f, size)
        if (valueLines.size != 1) throw DomainFailureException(PAYSLIP_PDF_LIMIT)
        val valueWidth = font.getStringWidth(valueLines.single()) / 1000f * size
        val labels = wrap(label, width - valueWidth - 20f, size)
        for ((index, line) in labels.withIndex()) {
            reserve(size + 6f)
            draw(line, margin, baseline, size)
            if (index == 0) draw(valueLines.single(), margin + width - valueWidth, baseline, size)
            baseline -= size + 6f
        }
    }

    fun section(title: String) {
        reserve(46f)
        baseline -= 10f
        divider()
        paragraph(title, 12f)
        baseline -= 5f
    }

    fun total(label: String, value: String) {
        reserve(46f)
        baseline -= 10f
        divider()
        row(label, value, emphasized = true)
        baseline -= 5f
    }

    private fun divider() {
        stream.setStrokingColor(0.75f, 0.77f, 0.8f)
        stream.moveTo(margin, baseline + 14f)
        stream.lineTo(margin + width, baseline + 14f)
        stream.stroke()
    }

    private fun wrap(text: String, available: Float, size: Float): List<String> {
        if (text.length > 32768 - characters) throw DomainFailureException(PAYSLIP_PDF_LIMIT)
        characters += text.length
        val lines = mutableListOf<String>()
        val line = StringBuilder()
        var used = 0f
        for (codePoint in text.codePoints().toArray()) {
            checkProgress()
            val character =
                if (Character.isWhitespace(codePoint)) " " else String(Character.toChars(codePoint))
            if (characterMap.getGlyphId(if (character == " ") 32 else codePoint) == 0)
                throw DomainFailureException(
                    Failure(FailureKind.UNAVAILABLE, "payroll_pdf_text_unsupported")
                )
            val advance = font.getStringWidth(character) / 1000f * size
            if (advance > available) throw DomainFailureException(PAYSLIP_PDF_LIMIT)
            if (used + advance > available && line.lastIndexOf(" ") > 0) {
                val breakAt = line.lastIndexOf(" ")
                lines.add(line.substring(0, breakAt))
                val remaining = line.substring(breakAt + 1)
                line.setLength(0)
                line.append(remaining)
                used = font.getStringWidth(remaining) / 1000f * size
            }
            if (used + advance > available && line.isNotEmpty()) {
                lines.add(line.toString())
                line.setLength(0)
                used = 0f
            }
            line.append(character)
            used += advance
        }
        if (line.isNotEmpty() || lines.isEmpty()) lines.add(line.toString())
        return lines
    }

    private fun reserve(height: Float) {
        checkProgress()
        if (baseline - height < margin) {
            if (document.numberOfPages >= 16) throw DomainFailureException(PAYSLIP_PDF_LIMIT)
            stream.close()
            stream = openPage()
            baseline = PDRectangle.A4.height - margin
        }
    }

    private fun openPage(): PDPageContentStream {
        val page = PDPage(PDRectangle.A4)
        document.addPage(page)
        return PDPageContentStream(document, page)
    }

    private fun draw(text: String, x: Float, y: Float, size: Float) {
        stream.beginText()
        stream.setFont(font, size)
        stream.setNonStrokingColor(0.08f, 0.1f, 0.14f)
        stream.newLineAtOffset(x, y)
        stream.showText(text)
        stream.endText()
    }

    private fun checkProgress() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        if (nanoTime() - start > 5_000_000_000L)
            throw DomainFailureException(
                Failure(FailureKind.UNAVAILABLE, "payroll_pdf_generation_timeout")
            )
    }

    override fun close() {
        stream.close()
    }
}
