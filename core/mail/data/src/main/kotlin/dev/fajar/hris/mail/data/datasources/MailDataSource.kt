package dev.fajar.hris.mail.data.datasources

interface MailDataSource {
    fun send(message: MailEnvelope)
}
