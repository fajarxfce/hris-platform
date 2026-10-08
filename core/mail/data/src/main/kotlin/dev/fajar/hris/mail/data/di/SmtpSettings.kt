package dev.fajar.hris.mail.data.di

import java.time.Duration

class SmtpSettings(
    val host: String,
    val port: Int,
    val from: String,
    val username: String,
    val password: String,
    val transport: String = "STARTTLS",
    val allowLoopbackPlaintext: Boolean = false,
    val timeout: Duration = Duration.ofSeconds(5),
) {
    init {
        require(host.isNotBlank() && host.length <= 253 && host.none { it.isWhitespace() }) {
            "Invalid SMTP host"
        }
        require(port in 1..65535) { "Invalid SMTP port" }
        require(
            from.length in 3..254 && from.contains('@') && from.none { it < ' ' || it == '\u007f' }
        ) {
            "Invalid SMTP sender"
        }
        require(username.length <= 254 && password.length <= 1024) { "Invalid SMTP credentials" }
        require(transport in setOf("STARTTLS", "TLS", "PLAINTEXT")) { "Invalid SMTP transport" }
        require(
            transport != "PLAINTEXT" ||
                (allowLoopbackPlaintext && host in setOf("127.0.0.1", "localhost", "::1"))
        ) {
            "Plain SMTP is restricted to explicit loopback development"
        }
        require(timeout.toMillis() in 1..30000) { "Invalid SMTP timeout" }
    }

    override fun toString(): String = "SmtpSettings(<redacted>)"
}
