package dev.fajar.hris.documents.data.models

data class DocumentCapacityData(
    val documents: Long,
    val active: Long,
    val owned: Long,
    val unfilledBytes: Long,
    val readyBytes: Long,
)
