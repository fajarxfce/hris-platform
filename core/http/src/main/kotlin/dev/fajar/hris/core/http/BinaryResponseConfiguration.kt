package dev.fajar.hris.core.http

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class BinaryResponseConfiguration {
    @Bean(destroyMethod = "close")
    fun binaryResponseWriter() = BinaryResponseWriter(BinaryResponseSettings())
}
