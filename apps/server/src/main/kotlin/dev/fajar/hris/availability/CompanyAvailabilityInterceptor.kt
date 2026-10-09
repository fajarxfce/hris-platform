package dev.fajar.hris.availability

import dev.fajar.hris.administration.domain.usecases.CheckCompanyAvailability
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.identity.delivery.security.NativeIdentity
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

class CompanyAvailabilityInterceptor(private val check: CheckCompanyAvailability) :
    HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val actor = request.getAttribute("hris.actor") as? Actor ?: return true
        if (actor.companyId == null || handler !is HandlerMethod) return true
        val feature =
            handler.beanType.packageName.removePrefix("dev.fajar.hris.").substringBefore('.')
        // Configuration, identity recovery, company administration, and job inspection stay usable.
        if (feature in companyPolicyExemptPackages) return true
        val modules =
            if (feature == "sync") requestedSyncModules(request, actor).response()
            else companyModulePackages[feature]?.let { setOf(it) } ?: emptySet()
        val version = readClientVersion(request)
        val native = SecurityContextHolder.getContext().authentication?.principal is NativeIdentity
        check.execute(actor, modules, version, native).response()
        return true
    }
}
