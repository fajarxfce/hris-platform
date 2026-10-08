package dev.fajar.hris.storage.domain.entities

data class StoredObject(val key: String, val size: Long, val etag: String, val sha256: String?)
