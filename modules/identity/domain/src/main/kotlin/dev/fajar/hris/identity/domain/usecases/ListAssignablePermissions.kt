package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.PermissionCatalog

class ListAssignablePermissions {
    fun execute(actor: Actor): Result<List<String>> =
        actor.requirePermission("identity.manage").map { PermissionCatalog.assignable.sorted() }
}
