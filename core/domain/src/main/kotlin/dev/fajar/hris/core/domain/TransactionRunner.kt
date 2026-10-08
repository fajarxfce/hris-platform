package dev.fajar.hris.core.domain

interface TransactionRunner {
    fun <T> run(actor: Actor, operation: () -> Result<T>): Result<T>
}
