package dev.fajar.hris

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.leave.domain.repositories.LeaveRequestRepository
import dev.fajar.hris.leave.domain.usecases.GetLeaveAttachmentDownload
import dev.fajar.hris.leave.domain.usecases.ReadLeaveAttachmentContent
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.storage.domain.repositories.ObjectStorageRepository
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class LeaveAttachmentAssuranceTest : LeaveAttachmentApiFixture() {
    @Autowired private lateinit var leaveRequests: LeaveRequestRepository
    @Autowired private lateinit var documentRepository: DocumentRepository
    @Autowired private lateinit var peopleRepository: PeopleRepository
    @Autowired private lateinit var approvalRepository: ApprovalRepository
    @Autowired private lateinit var companyRepository: CompanyRepository
    @Autowired private lateinit var membershipRepository: MembershipRepository
    @Autowired private lateinit var identityRepository: IdentityRepository
    @Autowired private lateinit var storageRepository: ObjectStorageRepository

    @ParameterizedTest
    @ValueSource(strings = ["metadata", "content-before-storage", "content-after-storage"])
    fun expiredProofCannotReleaseMetadataOrBytesAfterWaiting(operation: String) {
        val f = preparedLeave()
        val revision = evidence(f)
        val request = UUID.randomUUID()
        body(submitEvidence(f, request, listOf(revision)))
        val (account, _) = member(f, listOf("leave.read"))
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                account,
            )
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val actor =
            Actor(
                account,
                f.company,
                setOf("leave.read"),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1),
            )
        val metadata =
            GetLeaveAttachmentDownload(
                leaveRequests,
                documentRepository,
                peopleRepository,
                approvalRepository,
                companyRepository,
                membershipRepository,
                identityRepository,
                transactions,
                clock,
                security,
            )
        val content =
            ReadLeaveAttachmentContent(
                leaveRequests,
                documentRepository,
                peopleRepository,
                approvalRepository,
                companyRepository,
                membershipRepository,
                identityRepository,
                transactions,
                clock,
                storageRepository,
                security,
            )
        val invoke: (Actor) -> Result<*> = { current ->
            if (operation == "metadata") metadata.execute(current, request, revision)
            else content.execute(current, request, revision, 0, pdf.size)
        }
        val accountBarrier = AccountLockProbe.Barrier(account)
        val storageEntered = CountDownLatch(1)
        val storageRelease = CountDownLatch(1)
        if (operation == "content-after-storage") {
            storageProbe.beforeRead = {
                storageEntered.countDown()
                check(storageRelease.await(5, TimeUnit.SECONDS))
            }
        } else accountProbe.current.set(accountBarrier)
        storageProbe.reads.set(0)
        try {
            val result =
                Executors.newSingleThreadExecutor().use { executor ->
                    val pending = executor.submit<Result<*>> { invoke(actor) }
                    try {
                        val entered =
                            if (operation == "content-after-storage") storageEntered
                            else accountBarrier.entered
                        assertTrue(entered.await(5, TimeUnit.SECONDS))
                        clock.set(clock.instant().plusSeconds(2))
                        accountBarrier.release.countDown()
                        storageRelease.countDown()
                        pending.get(10, TimeUnit.SECONDS)
                    } finally {
                        accountBarrier.release.countDown()
                        storageRelease.countDown()
                    }
                }
            assertEquals("mfa_required", (result as? Result.Failed)?.failure?.code)
            assertEquals(
                if (operation == "content-after-storage") 1 else 0,
                storageProbe.reads.get(),
            )
            accountProbe.current.set(null)
            storageProbe.beforeRead = null
            val renewed = invoke(actor.copy(mfaVerifiedAt = clock.instant()))
            assertTrue(renewed is Result.Success, renewed.toString())
            if (operation != "metadata")
                assertArrayEquals(pdf, (renewed as Result.Success).value as ByteArray)
        } finally {
            accountBarrier.release.countDown()
            storageRelease.countDown()
            accountProbe.current.set(null)
            storageProbe.beforeRead = null
        }
    }
}
