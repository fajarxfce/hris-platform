package dev.fajar.hris

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication class HrisApplication

fun main(args: Array<String>) {
    runApplication<HrisApplication>(*args)
}
