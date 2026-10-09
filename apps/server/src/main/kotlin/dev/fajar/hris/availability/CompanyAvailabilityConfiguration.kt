package dev.fajar.hris.availability

import dev.fajar.hris.administration.domain.usecases.CheckCompanyAvailability
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration(proxyBeanMethods = false)
class CompanyAvailabilityConfiguration {
    @Bean
    fun companyAvailability(check: CheckCompanyAvailability): WebMvcConfigurer =
        object : WebMvcConfigurer {
            override fun addArgumentResolvers(
                resolvers:
                    MutableList<
                        org.springframework.web.method.support.HandlerMethodArgumentResolver
                    >
            ) {
                resolvers.add(ClientRequestArgumentResolver())
            }

            override fun addInterceptors(registry: InterceptorRegistry) {
                registry
                    .addInterceptor(CompanyAvailabilityInterceptor(check))
                    .addPathPatterns("/api/v1/companies/{companyId}/**")
                    .order(100)
            }
        }
}
