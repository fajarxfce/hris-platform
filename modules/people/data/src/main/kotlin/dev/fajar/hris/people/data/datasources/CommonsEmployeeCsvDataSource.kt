package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.people.data.models.EmployeeCsvDocument
import dev.fajar.hris.people.data.models.employeeCsvColumns
import java.io.StringReader
import java.io.StringWriter
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.DuplicateHeaderMode

class CommonsEmployeeCsvDataSource : EmployeeCsvDataSource {
    override fun template(): String =
        StringWriter().use { writer ->
            CSVPrinter(writer, CSVFormat.RFC4180).use { it.printRecord(employeeCsvColumns) }
            writer.toString()
        }

    override fun read(content: String): EmployeeCsvDocument {
        require(content.length <= 524288 && content.none { it == '\u0000' })
        val format =
            CSVFormat.RFC4180.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setAllowMissingColumnNames(false)
                .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
                .get()
        return StringReader(content.removePrefix("\uFEFF")).use { reader ->
            format.parse(reader).use { parser ->
                val headers = parser.headerNames.toList()
                require(headers.size in 1..employeeCsvColumns.size)
                val rows =
                    parser
                        .asSequence()
                        .take(5001)
                        .map { record ->
                            require(record.size() == headers.size)
                            record.toList().also { row -> require(row.all { it.length <= 1024 }) }
                        }
                        .toList()
                require(rows.size in 1..5000)
                EmployeeCsvDocument(headers, rows)
            }
        }
    }
}
