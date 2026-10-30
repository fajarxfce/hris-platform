package dev.fajar.hris

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.repositories.*
import dev.fajar.hris.administration.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class AdministrationReadCompletionTest : ClientPolicyApiFixture() {
    @Autowired private lateinit var policies: CompanyClientPolicyRepository
    @Autowired private lateinit var audits: AuditRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "settings_head",
                "settings_effective",
                "revision_head",
                "revision_named",
                "audit_page",
                "audit_cursor",
            ]
    )
    fun sensitiveReadsRequireAssuranceAfterTheirLastAcquisition(mode: String) {
        val f = fixture()
        assertEquals(200, save(f).statusCode())
        val starting = clock.instant()
        val original = Instant.now().plusSeconds(1)
        clock.set(original)
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val actor =
            f.actor.copy(
                permissions = f.actor.permissions + "audit.read",
                mfaVerifiedAt = original.minus(security.maximumMfaAge).plusSeconds(1),
            )
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.account,
            )
        var expire = false
        var acquired = 0
        fun <T> observe(value: Result<T>): Result<T> {
            assertTrue(value is Result.Success, value.toString())
            acquired++
            if (expire) clock.set(original.plusSeconds(2))
            return value
        }
        val observedPolicies =
            object : CompanyClientPolicyRepository by policies {
                override fun find(companyId: UUID, revision: Long?) =
                    policies.find(companyId, revision).let {
                        if (mode in setOf("settings_head", "revision_head", "revision_named"))
                            observe(it)
                        else it
                    }

                override fun effective(companyId: UUID, at: Instant) =
                    policies.effective(companyId, at).let {
                        if (mode == "settings_effective") observe(it) else it
                    }
            }
        val observedAudits =
            object : AuditRepository by audits {
                override fun find(companyId: UUID, id: UUID) =
                    audits.find(companyId, id).let {
                        if (mode == "audit_cursor") observe(it) else it
                    }

                override fun search(companyId: UUID, query: AuditQuery) =
                    audits.search(companyId, query).let {
                        if (mode == "audit_page") observe(it) else it
                    }
            }
        val settings =
            GetCompanyClientPolicySettings(
                observedPolicies,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
            )
        val revision =
            GetCompanyClientPolicyRevision(
                observedPolicies,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
            )
        val audit =
            SearchAuditEvents(
                observedAudits,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
            )
        try {
            val cursor =
                if (mode == "audit_cursor") {
                    val page = audit.execute(actor, AuditSearch(limit = 1))
                    assertTrue(page is Result.Success, page.toString())
                    (page as Result.Success).value.items.first().id
                } else null
            fun read(current: Actor): Result<*> =
                when (mode) {
                    "settings_head",
                    "settings_effective" -> settings.execute(current)
                    "revision_head" -> revision.execute(current)
                    "revision_named" -> revision.execute(current, 0)
                    else -> audit.execute(current, AuditSearch(cursor = cursor, limit = 1))
                }
            val admitted = read(actor)
            assertTrue(admitted is Result.Success, admitted.toString())
            acquired = 0
            expire = true
            val expired = read(actor)
            assertTrue(acquired > 0)
            assertEquals(
                "mfa_required",
                (expired as? Result.Failed)?.failure?.code,
                expired.toString(),
            )
            expire = false
            val renewed = read(actor.copy(mfaVerifiedAt = clock.instant()))
            assertTrue(renewed is Result.Success, renewed.toString())
            assertEquals(1, versions(f))
        } finally {
            database().update("update accounts set mfa_secret_encrypted=null where id=?", f.account)
            clock.set(starting)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun namedRevisionRequiresAssuranceBeforeAcquiringPrivateHistory(enrolled: Boolean) {
        val f = fixture()
        assertEquals(200, save(f).statusCode())
        if (enrolled)
            database()
                .update(
                    "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                    f.account,
                )
        var acquired = 0
        val observed =
            object : CompanyClientPolicyRepository by policies {
                override fun find(companyId: UUID, revision: Long?) =
                    policies.find(companyId, revision).also { acquired++ }
            }
        val revision =
            GetCompanyClientPolicyRevision(
                observed,
                companies,
                members,
                identities,
                transactions,
                IdentitySecurityPolicy(enforceMfa = true),
                clock,
            )
        try {
            val result = revision.execute(f.actor, 0)
            assertEquals(
                if (enrolled) "mfa_required" else "mfa_setup_required",
                (result as? Result.Failed)?.failure?.code,
                result.toString(),
            )
            assertEquals(0, acquired)
        } finally {
            database().update("update accounts set mfa_secret_encrypted=null where id=?", f.account)
        }
    }
}
