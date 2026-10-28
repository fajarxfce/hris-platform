package dev.fajar.hris.reporting.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReportCsvPolicyTest {
    private inline fun <reified T : Any> assertIs(value: Any?): T =
        assertInstanceOf(T::class.java, value)

    private val text = ReportExportColumn("label", ReportExportColumnType.TEXT)

    @Test
    fun `golden CSV keeps unicode quotes record boundaries exact decimal scale and signed numbers`() {
        val columns =
            listOf(
                text,
                ReportExportColumn("quantity", ReportExportColumnType.DECIMAL),
                ReportExportColumn("adjustment_minutes", ReportExportColumnType.INTEGER),
                ReportExportColumn("optional", ReportExportColumnType.TEXT, true),
            )
        val part =
            encode(
                columns,
                listOf(
                    listOf(
                        ReportExportCell.Text("Nama \"Ali\", lokasi\r\nBaru 🧾"),
                        ReportExportCell.Decimal(BigDecimal("-12345678901234567890.125000000")),
                        ReportExportCell.Integer(-30),
                        ReportExportCell.Empty,
                    ),
                    listOf(
                        ReportExportCell.Text("=1+1"),
                        ReportExportCell.Decimal(BigDecimal("1E+3")),
                        ReportExportCell.Integer(Long.MAX_VALUE),
                        ReportExportCell.Text(""),
                    ),
                ),
                true,
            )
        assertEquals(2, part.rows)
        assertTrue(part.includesHeader)
        val golden =
            "\uFEFF\"label\",\"quantity\",\"adjustment_minutes\",\"optional\"\r\n" +
                "\"Nama \"\"Ali\"\", lokasi\r\nBaru 🧾\",-12345678901234567890.125000000,-30,\r\n" +
                "\"'=1+1\",1000,9223372036854775807,\"\"\r\n"
        assertArrayEquals(golden.toByteArray(Charsets.UTF_8), part.bytes())
        assertEquals(part.byteLength, golden.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun `textual formulas cannot become executable cells while numeric negatives remain numbers`() {
        for (value in
            listOf(
                "=SUM(A1)",
                "+1+2",
                "-123",
                "@SUM(A1)",
                " \t=CMD()",
                "\uFEFF=1",
                "\uDB40\uDC01=1",
                "\uFE0F=1",
                "\u0300@SUM(A1)",
                "\u200B+1",
                "\u00A0-1",
                "\ttext",
                "\rtext",
                "\ntext",
            )) {
            val part = encode(listOf(text), listOf(listOf(ReportExportCell.Text(value))), false)
            assertEquals("\"'$value\"\r\n", part.bytes().toString(Charsets.UTF_8))
        }
        for (value in listOf("Normal", "'already literal", "A=1", "2026-10-01", "", "正常 🧾")) {
            val part = encode(listOf(text), listOf(listOf(ReportExportCell.Text(value))), false)
            assertEquals("\"$value\"\r\n", part.bytes().toString(Charsets.UTF_8))
        }
        assertEquals(
            "-42\r\n",
            encode(
                    listOf(ReportExportColumn("number", ReportExportColumnType.INTEGER)),
                    listOf(listOf(ReportExportCell.Integer(-42))),
                    false,
                )
                .bytes()
                .toString(Charsets.UTF_8),
        )
    }

    @Test
    fun `independent parts concatenate with exactly one BOM and header and own their bytes`() {
        val first = encode(listOf(text), listOf(listOf(ReportExportCell.Text("first"))), true)
        val second =
            encode(
                listOf(text),
                listOf(
                    listOf(ReportExportCell.Text("second")),
                    listOf(ReportExportCell.Text("third")),
                ),
                false,
            )
        val bytes = first.bytes() + second.bytes()
        assertEquals(
            "\uFEFF\"label\"\r\n\"first\"\r\n\"second\"\r\n\"third\"\r\n",
            bytes.toString(Charsets.UTF_8),
        )
        first.bytes().fill(0)
        second.bytes().fill(0)
        assertArrayEquals(bytes, first.bytes() + second.bytes())
        val empty = encode(listOf(text), emptyList(), true)
        assertEquals(0, empty.rows)
        assertEquals("\uFEFF\"label\"\r\n", empty.bytes().toString(Charsets.UTF_8))
        assertCode(
            "invalid_report_export_rows",
            encodeReportCsvPart(listOf(text), emptyList(), false),
        )
    }

    @Test
    fun `exact one MiB ceiling consumes only complete rows while oversized input pages are rejected`() {
        val base = "x".repeat(2093)
        val initial = List(500) { listOf(ReportExportCell.Text(base)) }
        val measured = encode(listOf(text), initial, true)
        val remaining = REPORT_CSV_MAX_PART_BYTES - measured.byteLength
        assertTrue(remaining in 1..100)
        val exact =
            initial.dropLast(1) +
                listOf(listOf(ReportExportCell.Text(base + "y".repeat(remaining))))
        assertEquals(REPORT_CSV_MAX_PART_BYTES, encode(listOf(text), exact, true).byteLength)
        val split =
            encode(
                listOf(text),
                exact.dropLast(1) +
                    listOf(listOf(ReportExportCell.Text(base + "y".repeat(remaining + 1)))),
                true,
            )
        assertEquals(499, split.rows)
        assertTrue(split.byteLength < REPORT_CSV_MAX_PART_BYTES)
        assertCode(
            "invalid_report_export_rows",
            encodeReportCsvPart(listOf(text), initial + initial.take(1), true),
        )
        // The ceiling counts UTF-8 bytes, not UTF-16 characters or quoted CSV length.
        val unicode =
            encode(
                listOf(text),
                List(500) { listOf(ReportExportCell.Text("漢".repeat(1000))) },
                true,
            )
        assertTrue(unicode.rows in 1..499)
        assertEquals(12 + unicode.rows * 3004, unicode.byteLength)
    }

    @Test
    fun `repeated bounded pages terminate without skipping duplicating or splitting records`() {
        val columns = listOf(ReportExportColumn("sequence", ReportExportColumnType.INTEGER), text)
        val label = "漢".repeat(3000)
        val parts = mutableListOf<ReportCsvPart>()
        var consumed = 0
        while (consumed < 1251) {
            val page =
                (consumed until minOf(consumed + 500, 1251)).map {
                    listOf(ReportExportCell.Integer(it.toLong()), ReportExportCell.Text(label))
                }
            val part = encode(columns, page, parts.isEmpty())
            assertTrue(part.rows in 1..page.size)
            assertTrue(part.byteLength <= REPORT_CSV_MAX_PART_BYTES)
            parts.add(part)
            consumed += part.rows
            assertTrue(parts.size <= 1251)
        }
        assertTrue(parts.size > 3)
        val records =
            parts.flatMap { it.bytes().toString(Charsets.UTF_8).split("\r\n").dropLast(1) }
        assertEquals("\uFEFF\"sequence\",\"label\"", records.first())
        assertEquals(1252, records.size)
        for (number in 0 until 1251) assertEquals("$number,\"$label\"", records[number + 1])
    }

    @Test
    fun `column and cell shape limits reject ambiguous unsupported or lossy values`() {
        for (columns in
            listOf(
                emptyList(),
                listOf(text, text),
                listOf(text.copy(key = "=bad")),
                listOf(text.copy(key = "x".repeat(65))),
                List(65) { text.copy(key = "column_$it") },
            )) {
            assertCode(
                "invalid_report_export_columns",
                encodeReportCsvPart(columns, emptyList(), true),
            )
        }
        val integer = ReportExportColumn("number", ReportExportColumnType.INTEGER)
        val decimal = integer.copy(type = ReportExportColumnType.DECIMAL)
        for ((column, cell) in
            listOf(
                text to ReportExportCell.Empty,
                text to ReportExportCell.Integer(1),
                integer to ReportExportCell.Text("1"),
                integer to ReportExportCell.Decimal(BigDecimal.ONE),
                decimal to ReportExportCell.Integer(1),
                decimal to ReportExportCell.Decimal(BigDecimal("1E+100000000")),
                decimal to ReportExportCell.Decimal(BigDecimal("0.0000000001")),
                decimal to ReportExportCell.Decimal(BigDecimal("1".repeat(31))),
            )) assertCode(
            "invalid_report_export_rows",
            encodeReportCsvPart(listOf(column), listOf(listOf(cell)), true),
        )
        for (value in
            listOf(
                "x".repeat(4097),
                "null\u0000",
                "control\u0001",
                "c1\u0085",
                "\uD800",
                "\uDC00",
                "\uD800x",
            )) {
            assertCode(
                "invalid_report_export_rows",
                encodeReportCsvPart(
                    listOf(text),
                    listOf(listOf(ReportExportCell.Text(value))),
                    true,
                ),
            )
        }
        assertCode(
            "invalid_report_export_rows",
            encodeReportCsvPart(listOf(text), listOf(emptyList()), true),
        )
        assertCode(
            "invalid_report_export_rows",
            encodeReportCsvPart(
                listOf(text),
                listOf(listOf(ReportExportCell.Text("one"), ReportExportCell.Text("two"))),
                true,
            ),
        )
        assertTrue(
            encode(listOf(text), listOf(listOf(ReportExportCell.Text("x".repeat(4096)))), true)
                .byteLength > 4096
        )
        assertEquals(
            500,
            encode(
                    List(64) { text.copy(key = "field_$it") },
                    List(500) { List(64) { ReportExportCell.Text("") } },
                    true,
                )
                .rows,
        )
    }

    private fun encode(
        columns: List<ReportExportColumn>,
        rows: List<List<ReportExportCell>>,
        header: Boolean,
    ) = assertIs<Result.Success<ReportCsvPart>>(encodeReportCsvPart(columns, rows, header)).value

    private fun assertCode(code: String, result: Result<*>) {
        assertEquals(code, assertIs<Result.Failed>(result).failure.code)
    }
}
