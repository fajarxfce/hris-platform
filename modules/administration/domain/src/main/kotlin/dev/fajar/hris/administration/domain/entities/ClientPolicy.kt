package dev.fajar.hris.administration.domain.entities

import java.util.Collections

class ClientPolicy(
    disabledModules: Set<CompanyModule> = emptySet(),
    val minimumBuilds: MinimumClientBuilds = MinimumClientBuilds(),
    val maintenance: MaintenanceWindow? = null,
) {
    val disabledModules: Set<CompanyModule> = Collections.unmodifiableSet(disabledModules.toSet())
}
