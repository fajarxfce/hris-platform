package dev.fajar.hris

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory

class NativeFirebaseDisabledHttpTest : ApiIntegrationTest() {
    @Autowired private lateinit var beans: ConfigurableListableBeanFactory

    @Test
    fun disabledFirebaseExposesNoProjectAndAcquiresNoProviderResources() {
        val mobile = client()
        val response = get(mobile, "/api/v1/auth/native/firebase")
        assertEquals(200, response.statusCode(), response.body())
        val config = json.readTree(response.body())
        assertFalse(config["enabled"].asBoolean())
        assertTrue(config["projectId"].isNull)
        assertEquals(0, config["providers"].size())
        val login =
            command(
                mobile,
                "/api/v1/auth/native/firebase/login",
                """{"idToken":"${"x".repeat(64)}","deviceName":"Android"}""",
                "unused",
                UUID.randomUUID(),
            )
        assertEquals(503, login.statusCode(), login.body())
        assertEquals("firebase_sign_in_unavailable", json.readTree(login.body())["code"].asString())
        assertFalse(beans.containsSingleton("firebaseHttpClient"))
        assertFalse(beans.containsSingleton("firebaseKeys"))
        assertTrue(login.headers().allValues("Set-Cookie").isEmpty())
    }
}
