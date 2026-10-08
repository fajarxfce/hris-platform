package dev.fajar.hris

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@TestConfiguration(proxyBeanMethods = false)
@RestController
class AssuranceProbeConfiguration {
    @GetMapping("/api/v1/test/assurance")
    fun read(): Map<String, Boolean> = mapOf("allowed" to true)
}
