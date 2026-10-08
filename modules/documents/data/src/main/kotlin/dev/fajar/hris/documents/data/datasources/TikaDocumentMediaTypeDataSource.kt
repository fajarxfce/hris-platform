package dev.fajar.hris.documents.data.datasources

import org.apache.tika.detect.DefaultDetector
import org.apache.tika.metadata.Metadata

class TikaDocumentMediaTypeDataSource : DocumentMediaTypeDataSource {
    private val detector = DefaultDetector()

    override fun detect(prefix: ByteArray): String =
        org.apache.tika.io.TikaInputStream.get(prefix).use {
            detector.detect(it, Metadata(), org.apache.tika.parser.ParseContext()).toString()
        }
}
