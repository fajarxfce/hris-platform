package dev.fajar.hris.openapi

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.identity.delivery.security.PendingMfaAllowed
import dev.fajar.hris.identity.delivery.security.SessionTransport
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.servers.Server
import java.time.YearMonth
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.customizers.OperationCustomizer
import org.springdoc.core.utils.SpringDocUtils
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.security.web.csrf.CsrfToken

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = ["springdoc.api-docs.enabled"], havingValue = "true")
class ApiDocumentationConfiguration {
    init {
        SpringDocUtils.getConfig()
            .addRequestWrapperToIgnore(
                Actor::class.java,
                CsrfToken::class.java,
                ClientRequest::class.java,
            )
            .replaceWithSchema(
                YearMonth::class.java,
                StringSchema().pattern("^[0-9]{4}-(0[1-9]|1[0-2])$").example("2026-10"),
            )
    }

    @Bean
    fun apiDescription() =
        OpenAPI()
            .info(
                Info()
                    .title("HRIS API")
                    .version("v1")
                    .description(
                        "Standalone multi-company HRIS. Codes and safe parameters are translated by clients. Money uses decimal strings; dates and opaque sync cursors retain their transport representation. Authorization is checked for each account, company, and resource."
                    )
            )
            .servers(listOf(Server().url("/")))

    @Bean
    fun apiHandlerMetadata() = OperationCustomizer { operation, handler ->
        val transport =
            handler.getMethodAnnotation(SessionTransport::class.java)
                ?: AnnotatedElementUtils.findMergedAnnotation(
                    handler.beanType,
                    SessionTransport::class.java,
                )
        operation.addExtension(
            "x-hris-session-transport",
            transport?.value?.name ?: "COOKIE_OR_NATIVE",
        )
        operation.addExtension(
            "x-hris-pending-mfa-allowed",
            handler.hasMethodAnnotation(PendingMfaAllowed::class.java),
        )
        operation.tags = listOf(handler.beanType.packageName.split('.').getOrElse(3) { "api" })
        val feature =
            handler.beanType.packageName.removePrefix("dev.fajar.hris.").substringBefore('.')
        operation.addExtension(
            "x-hris-company-admission",
            feature !in dev.fajar.hris.availability.companyPolicyExemptPackages,
        )
        operation.addExtension(
            "x-hris-group-admission",
            handler.methodParameters.any { it.parameterType == ClientRequest::class.java },
        )
        operation
    }

    @Bean fun apiContract() = OpenApiCustomizer(::completeApiContract)
}
