package dev.fajar.hris.worker

import dev.fajar.hris.communications.domain.entities.InboxEmailPolicy
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.mail.domain.entities.OutboundMail
import dev.fajar.hris.mail.domain.repositories.MailRepository
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionSynchronizationManager

class InboxEmailProbe {
    val messages = ConcurrentLinkedQueue<OutboundMail>()
    val send = AtomicReference<(OutboundMail) -> Result<Unit>>({ Result.Success(Unit) })

    fun clear() {
        messages.clear()
        send.set { Result.Success(Unit) }
    }
}

@TestConfiguration(proxyBeanMethods = false)
class InboxEmailProbeConfiguration {
    @Bean fun inboxEmailProbe() = InboxEmailProbe()

    @Bean
    @Primary
    fun enabledInboxEmailPolicy() = InboxEmailPolicy(true, "https://hris.example.test", "dev")

    @Bean
    @Primary
    fun fixtureInboxMail(probe: InboxEmailProbe): MailRepository = MailRepository { message ->
        check(!TransactionSynchronizationManager.isActualTransactionActive())
        check(probe.messages.size < 100)
        probe.messages.add(message)
        probe.send.get().invoke(message)
    }
}
