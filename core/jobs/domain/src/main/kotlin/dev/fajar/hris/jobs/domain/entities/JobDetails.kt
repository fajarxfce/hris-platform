package dev.fajar.hris.jobs.domain.entities

data class JobDetails(val job: BackgroundJob, val availableActions: List<String>)
