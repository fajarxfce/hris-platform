package dev.fajar.hris.jobs.domain.entities

data class JobProgress(val completedItems: Int, val checkpoint: Map<String, String>)
