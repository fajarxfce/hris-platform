package dev.fajar.hris.people.delivery.requests

data class LifecycleCompletionRequest(val expectedVersion: Long, val reason: String)
