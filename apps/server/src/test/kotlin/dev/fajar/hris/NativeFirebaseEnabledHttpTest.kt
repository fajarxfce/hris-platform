package dev.fajar.hris

import dev.fajar.hris.identity.data.firebase.datasources.FirebaseKeyDataSource
import dev.fajar.hris.identity.data.firebase.datasources.GoogleFirebaseKeyDataSource
import dev.fajar.hris.identity.data.firebase.repositories.VerifiedFirebaseIdentityRepository
import dev.fajar.hris.identity.domain.repositories.FirebaseIdentityRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/** Validate the production enabled graph independently of the signing-key test replacement. */
class NativeFirebaseEnabledHttpTest : ApiIntegrationTest() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun firebase(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_FIREBASE_AUTH_ENABLED") { true }
            registry.add("HRIS_FIREBASE_PROJECT_ID") { "cmp-fajar" }
        }
    }

    @Autowired private lateinit var beans: ConfigurableListableBeanFactory

    @Test
    fun enabledConfigurationComposesRealVerificationAndOwnedTransportWithoutProviderIo() {
        assertInstanceOf(
            VerifiedFirebaseIdentityRepository::class.java,
            beans.getBean(FirebaseIdentityRepository::class.java),
        )
        assertInstanceOf(
            GoogleFirebaseKeyDataSource::class.java,
            beans.getBean(FirebaseKeyDataSource::class.java),
        )
        assertTrue(beans.containsSingleton("firebaseHttpClient"))
        val response = get(client(), "/api/v1/auth/native/firebase")
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("cmp-fajar", json.readTree(response.body())["projectId"].asString())
        assertTrue(response.headers().allValues("Set-Cookie").isEmpty())
    }
}
