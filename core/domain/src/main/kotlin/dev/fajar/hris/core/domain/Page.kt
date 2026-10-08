package dev.fajar.hris.core.domain

data class Page<T>(val items: List<T>, val nextCursor: String?)
