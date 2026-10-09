package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.schema.tables.records.LifecycleCasesRecord
import dev.fajar.hris.schema.tables.records.LifecycleTasksRecord

data class LifecycleCaseRow(val case: LifecycleCasesRecord, val tasks: List<LifecycleTasksRecord>)
