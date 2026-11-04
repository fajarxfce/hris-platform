package dev.fajar.hris.collaboration.delivery

import dev.fajar.hris.collaboration.delivery.matrix.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class MatrixPushNotificationTest {
    private val json = JsonMapper.builder().build()
    private val key = "hriscp1_" + "a".repeat(64)
    private val device =
        mapOf(
            "app_id" to "dev.fajar.hris.chat",
            "pushkey" to key,
            "pushkey_ts" to 1793491200L,
            "data" to mapOf("format" to "event_id_only"),
        )
    private val event =
        mapOf(
            "event_id" to "$" + "fixture",
            "room_id" to "!room:fixture.invalid",
            "prio" to "high",
            "counts" to mapOf("unread" to 1),
            "devices" to listOf(device),
        )

    private fun decode(value: Map<String, Any?>) =
        matrixPushNotification(json.writeValueAsBytes(mapOf("notification" to value)))

    @Test
    fun mixedCompanyAndGroupKeysShareOneTotalLimitAndRemainTyped() {
        val group = "hrisgp1_" + "b".repeat(64)
        val mixed =
            requireNotNull(
                decode(event + ("devices" to listOf(device, device + ("pushkey" to group))))
            )
        assertInstanceOf(MatrixPushDeviceKey.Company::class.java, mixed.keys[0])
        assertInstanceOf(MatrixPushDeviceKey.Space::class.java, mixed.keys[1])
        assertEquals(listOf(key, group), mixed.keys.map { it.value })
        assertFalse(mixed.toString().contains(group))
        assertFalse(mixed.keys[1].toString().contains(group))
        val all =
            (0 until 32).map {
                device +
                    ("pushkey" to
                        ((if (it % 2 == 0) "hriscp1_" else "hrisgp1_") +
                            it.toString(16).padStart(64, '0')))
            }
        assertEquals(32, requireNotNull(decode(event + ("devices" to all))).keys.size)
        assertNull(decode(event + ("devices" to (all + device))))
        assertNull(
            decode(
                event +
                    ("devices" to
                        listOf(
                            device,
                            device + ("pushkey" to group),
                            device + ("pushkey" to group),
                        ))
            )
        )
        assertNull(
            decode(
                event +
                    ("devices" to
                        listOf(device, device + ("pushkey" to ("hrisgp2_" + "b".repeat(64)))))
            )
        )
    }

    @Test
    fun actualSynapseEventOnlyAndLegacyBadgeShapesHaveNoPlaintextProjection() {
        val accepted = requireNotNull(decode(event))
        assertEquals("!room:fixture.invalid", accepted.event?.roomId)
        assertEquals("$" + "fixture", accepted.event?.eventId)
        assertEquals(key, accepted.keys.single().value)
        for (badge in
            listOf(
                mapOf("counts" to mapOf("unread" to 0), "devices" to listOf(device)),
                mapOf(
                    "id" to "",
                    "type" to null,
                    "sender" to "",
                    "counts" to mapOf("unread" to 0),
                    "devices" to listOf(device),
                ),
            )) {
            val hint = requireNotNull(decode(badge))
            assertNull(hint.event)
            assertEquals(key, hint.keys.single().value)
        }
        assertNotNull(decode(event - "counts" - "prio"))
        assertNotNull(
            decode(event + ("devices" to listOf(device + ("tweaks" to emptyMap<String, Any>()))))
        )
    }

    @Test
    fun plaintextNamesSenderTypesAndArbitraryDeviceDataAreRejected() {
        for (name in
            listOf(
                "content",
                "room_name",
                "sender_display_name",
                "sender",
                "type",
                "id",
            )) assertNull(decode(event + (name to "private content")))
        for (changed in
            listOf(
                device + ("data" to mapOf("url" to "https://untrusted.invalid")),
                device + ("data" to mapOf("default_payload" to mapOf("body" to "private"))),
                device + ("tweaks" to mapOf("sound" to "default")),
                device + ("app_id" to "other.app"),
                device + ("pushkey" to "real-native-token"),
            )) assertNull(decode(event + ("devices" to listOf(changed))))
        val badge = event - "event_id" - "room_id"
        assertNull(decode(badge + ("sender" to "private")))
        assertNull(decode(badge + ("type" to "m.room.encrypted")))
        assertNull(decode(badge - "counts"))
        assertNull(decode(badge + ("counts" to emptyMap<String, Any>())))
    }

    @Test
    fun duplicateJsonTrailingValuesMalformedNumbersAndDeepStructuresAreRejected() {
        val body = json.writeValueAsString(mapOf("notification" to event))
        assertNull(matrixPushNotification((body + " {}").toByteArray()))
        assertNull(
            matrixPushNotification(
                body
                    .replace("\"prio\":\"high\"", "\"prio\":\"high\",\"prio\":\"low\"")
                    .toByteArray()
            )
        )
        assertNull(
            matrixPushNotification(
                body.replace("\"unread\":1", "\"unread\":1,\"unread\":2").toByteArray()
            )
        )
        assertNull(
            matrixPushNotification(
                body
                    .replace("\"unread\":1", "\"unread\":999999999999999999999999999999999")
                    .toByteArray()
            )
        )
        assertNull(
            matrixPushNotification(
                body.replace("\"unread\":1", "\"unread\":[[[[[[[[[[0]]]]]]]]]]").toByteArray()
            )
        )
        assertNull(matrixPushNotification(("{\"notification\":" + body + "}").toByteArray()))
        assertNull(matrixPushNotification("null".toByteArray()))
        assertNull(matrixPushNotification("[]".toByteArray()))
    }

    @Test
    fun countsPrioritiesAndIdentifiersAreStrictlyBoundedAndNeverCoerced() {
        for (count in listOf<Any>(-1, 1.5, "1", true, Int.MAX_VALUE.toLong() + 1)) assertNull(
            decode(event + ("counts" to mapOf("unread" to count)))
        )
        for (prio in listOf<Any>("urgent", true, 1)) assertNull(decode(event + ("prio" to prio)))
        for (id in
            listOf("event", "$", "$" + "a".repeat(255), "$" + "control\n", "$" + "é")) assertNull(
            decode(event + ("event_id" to id))
        )
        assertNull(decode(event - "room_id"))
        assertNull(decode(event + ("room_id" to "room")))
        assertNull(decode(event + ("counts" to mapOf("private" to 1))))
        for (stamp in listOf<Any>(-1, "1", 1.5)) assertNull(
            decode(event + ("devices" to listOf(device + ("pushkey_ts" to stamp))))
        )
    }

    @Test
    fun deviceFanoutDuplicatesAndTotalBytesAreBounded() {
        val devices =
            (0 until MATRIX_PUSH_MAXIMUM_DEVICES).map {
                device + ("pushkey" to ("hriscp1_" + it.toString(16).padStart(64, '0')))
            }
        assertEquals(
            MATRIX_PUSH_MAXIMUM_DEVICES,
            requireNotNull(decode(event + ("devices" to devices))).keys.size,
        )
        assertNull(decode(event + ("devices" to (devices + device))))
        assertNull(decode(event + ("devices" to listOf(device, device))))
        assertNull(decode(event + ("devices" to emptyList<Any>())))
        val bytes = json.writeValueAsBytes(mapOf("notification" to event))
        val exact = bytes + ByteArray(MATRIX_PUSH_MAXIMUM_BYTES - bytes.size) { 32 }
        assertNotNull(matrixPushNotification(exact))
        assertNull(matrixPushNotification(exact + byteArrayOf(32)))
    }
}
