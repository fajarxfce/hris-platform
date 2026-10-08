package dev.fajar.hris.mail.data.datasources

import jakarta.mail.Message
import jakarta.mail.internet.InternetAddress
import org.springframework.mail.MailParseException
import org.springframework.mail.javamail.JavaMailSender

class JakartaMailDataSource(private val sender: JavaMailSender, private val from: String) :
    MailDataSource {
    override fun send(message: MailEnvelope) {
        // Protocol and allocation constraints belong at the SDK boundary.
        if (
            message.recipient.length > 254 ||
                message.recipient.any { it < ' ' || it == '\u007f' } ||
                message.subject.length !in 1..160 ||
                message.subject.any { it < ' ' || it == '\u007f' } ||
                message.text.length > 65536 ||
                message.text.toByteArray(Charsets.UTF_8).size > 65536
        )
            throw MailParseException("Invalid outbound envelope")
        val recipient = InternetAddress(message.recipient, true)
        val origin = InternetAddress(from, true)
        if (
            recipient.isGroup ||
                origin.isGroup ||
                !recipient.address.contains('@') ||
                !origin.address.contains('@')
        )
            throw MailParseException("Invalid outbound address")
        val mime = sender.createMimeMessage()
        mime.setFrom(origin)
        mime.setRecipient(Message.RecipientType.TO, recipient)
        mime.setSubject(message.subject, Charsets.UTF_8.name())
        mime.setText(message.text, Charsets.UTF_8.name())
        mime.setHeader("Message-ID", "<${message.id}@${origin.address.substringAfter('@')}>")
        sender.send(mime)
    }
}
