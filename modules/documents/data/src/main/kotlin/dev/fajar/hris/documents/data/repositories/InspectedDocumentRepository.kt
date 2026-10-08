package dev.fajar.hris.documents.data.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.data.datasources.*
import dev.fajar.hris.documents.data.errors.safeDocumentInspectionCall
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.repositories.DocumentInspectionRepository
import dev.fajar.hris.storage.data.datasources.ObjectStorageDataSource
import dev.fajar.hris.storage.data.errors.safeObjectStorageCall
import java.security.MessageDigest
import java.util.HexFormat

class InspectedDocumentRepository(
    private val storage: ObjectStorageDataSource,
    private val scanner: DocumentScanDataSource,
    private val types: DocumentMediaTypeDataSource,
) : DocumentInspectionRepository {
    override fun inspect(parts: List<DocumentContentPart>): Result<DocumentInspection> =
        safeDocumentInspectionCall {
            if (
                parts.isEmpty() ||
                    parts.size > 100 ||
                    parts.any { it.size !in 1..1048576 } ||
                    parts.sumOf { it.size.toLong() } > 104857600
            )
                return@safeDocumentInspectionCall Result.Failed(
                    Failure(FailureKind.UNEXPECTED, "document_manifest_invalid")
                )
            // The acquired session never escapes the boundary, including a late interruption.
            val session = scanner.open()
            lateinit var closed: Result<Unit>
            val inspected =
                try {
                    run inspect@{
                        val digest = MessageDigest.getInstance("SHA-256")
                        var size = 0L
                        var prefix = byteArrayOf()
                        for (part in parts) {
                            if (part.offset != size)
                                return@inspect Result.Failed(
                                    Failure(FailureKind.UNEXPECTED, "document_manifest_invalid")
                                )
                            val read = safeObjectStorageCall {
                                storage.read(part.key, 0, part.size, part.etag)
                            }
                            if (read is Result.Failed) return@inspect read
                            val bytes = (read as Result.Success).value
                            if (
                                bytes.size != part.size ||
                                    HexFormat.of()
                                        .formatHex(
                                            MessageDigest.getInstance("SHA-256").digest(bytes)
                                        ) != part.sha256
                            )
                                return@inspect Result.Failed(
                                    Failure(FailureKind.CONFLICT, "document_part_integrity_failure")
                                )
                            if (size == 0L) prefix = bytes.copyOf(minOf(65536, bytes.size))
                            digest.update(bytes)
                            size += bytes.size
                            session.write(bytes)
                        }
                        val scan = session.finish()
                        val detected = types.detect(prefix)
                        Result.Success(
                            DocumentInspection(
                                size,
                                HexFormat.of().formatHex(digest.digest()),
                                detected,
                                scan.clean,
                                scan.engineVersion,
                            )
                        )
                    }
                } finally {
                    closed = safeDocumentInspectionCall {
                        session.close()
                        Result.Success(Unit)
                    }
                }
            if (inspected is Result.Failed) inspected
            else if (closed is Result.Failed) closed else inspected
        }
}
