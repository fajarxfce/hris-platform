package dev.fajar.hris.reporting.domain.entities

/** Owns bounded immutable bytes; callers receive a copy for storage I/O. */
class ReportCsvPart
internal constructor(bytes: ByteArray, val rows: Int, val includesHeader: Boolean) {
    private val content = bytes.copyOf()
    val byteLength: Int
        get() = content.size

    fun bytes(): ByteArray = content.copyOf()
}
