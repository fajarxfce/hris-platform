package dev.fajar.hris.storage.data.di

import java.net.URI
import java.net.URISyntaxException

fun parseObjectStorageEndpoint(value: String): URI =
    try {
        URI(value)
    } catch (ignored: URISyntaxException) {
        throw IllegalArgumentException("Invalid object storage endpoint")
    }
