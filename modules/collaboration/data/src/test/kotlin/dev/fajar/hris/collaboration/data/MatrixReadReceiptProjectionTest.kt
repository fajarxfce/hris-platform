package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class MatrixReadReceiptProjectionTest {
    private val json = JsonMapper.builder().build()
    private val own = "@own:proof.invalid"
    private val peer = "@peer:proof.invalid"

    private fun node(value: Any) = json.readTree(json.writeValueAsBytes(value))

    private fun event(content: Any) = node(mapOf("type" to "m.receipt", "content" to content))

    private fun project(content: Any) =
        mapCollaborationReadReceipts(listOf(event(content)), setOf(own, peer), own)

    @Test
    fun privateReceiptsAreOwnOnlyAndOnlyPublicPairMetadataPasses() {
        val result =
            project(
                mapOf(
                    "\$event" to
                        mapOf(
                            "m.read" to
                                mapOf(
                                    own to mapOf("ts" to 1, "private" to "remove"),
                                    peer to mapOf("ts" to 2),
                                    "@alien:proof.invalid" to mapOf("ts" to 3),
                                ),
                            "m.read.private" to
                                mapOf(own to mapOf("ts" to 4), peer to mapOf("ts" to 5)),
                            "unknown" to mapOf(own to mapOf("secret" to true)),
                        )
                )
            )
        assertEquals(
            listOf(
                mapOf(
                    "type" to "m.receipt",
                    "content" to
                        mapOf(
                            "\$event" to
                                mapOf(
                                    "m.read" to
                                        mapOf(own to mapOf("ts" to 1L), peer to mapOf("ts" to 2L)),
                                    "m.read.private" to mapOf(own to mapOf("ts" to 4L)),
                                )
                        ),
                )
            ),
            result,
        )
    }

    @Test
    fun absentTimestampsPassButThreadsUnknownTypesAndForeignPrivateStateDoNot() {
        assertEquals(
            emptyList<Map<String, Any>>(),
            project(
                mapOf(
                    "\$event" to
                        mapOf(
                            "m.read" to mapOf(peer to mapOf("thread_id" to "main", "ts" to 1)),
                            "m.read.private" to mapOf(peer to emptyMap<String, Any>()),
                        )
                )
            ),
        )
        assertEquals(
            listOf(
                mapOf(
                    "type" to "m.receipt",
                    "content" to
                        mapOf(
                            "\$event" to
                                mapOf("m.read.private" to mapOf(own to emptyMap<String, Any>()))
                        ),
                )
            ),
            project(
                mapOf("\$event" to mapOf("m.read.private" to mapOf(own to emptyMap<String, Any>())))
            ),
        )
        assertTrue(
            mapCollaborationReadReceipts(
                    listOf(
                        node(
                            mapOf(
                                "type" to "m.typing",
                                "content" to mapOf("user_ids" to listOf(peer)),
                            )
                        )
                    ),
                    setOf(own, peer),
                    own,
                )
                .isEmpty()
        )
    }

    @Test
    fun malformedAdmittedMetadataCannotAdvanceTheSyncBatch() {
        for (value in listOf<Any>(-1, 9_007_199_254_740_992L, 1.5, "1", true)) {
            assertThrows(MatrixResponseException::class.java) {
                project(mapOf("\$event" to mapOf("m.read" to mapOf(peer to mapOf("ts" to value)))))
            }
        }
        for (body in
            listOf<Any>(
                listOf("private"),
                mapOf("not-event" to emptyMap<String, Any>()),
                mapOf("\$event" to listOf("private")),
                mapOf("\$event" to mapOf("m.read" to listOf("private"))),
                mapOf("\$event" to mapOf("m.read" to mapOf(peer to "private"))),
            )) {
            assertThrows(MatrixResponseException::class.java) { project(body) }
        }
    }

    @Test
    fun limitsBoundTheCombinedEventBudgetAndUserMaps() {
        val content =
            (1..128).associate {
                "\$event$it" to mapOf("m.read" to mapOf(peer to mapOf("ts" to it)))
            }
        assertEquals(
            2,
            mapCollaborationReadReceipts(List(2) { event(content) }, setOf(own, peer), own).size,
        )
        assertThrows(MatrixResponseException::class.java) {
            mapCollaborationReadReceipts(List(3) { event(content) }, setOf(own, peer), own)
        }
        assertThrows(MatrixResponseException::class.java) {
            mapCollaborationReadReceipts(
                List(33) { event(emptyMap<String, Any>()) },
                setOf(own, peer),
                own,
            )
        }
        assertThrows(MatrixResponseException::class.java) {
            project(
                mapOf(
                    "\$event" to
                        mapOf(
                            "m.read" to
                                (1..257).associate {
                                    "@foreign$it:proof.invalid" to emptyMap<String, Any>()
                                }
                        )
                )
            )
        }
    }
}
