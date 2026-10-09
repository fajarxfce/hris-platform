package dev.fajar.hris.leave.delivery.requests

data class LeaveBatchResumeRequest(val expectedVersion: Long, val reason: String)
