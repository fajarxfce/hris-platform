package dev.fajar.hris.people.delivery.requests

data class ReviseEmploymentRequest(val version: Long, val terms: TermsRequest, val reason: String)
