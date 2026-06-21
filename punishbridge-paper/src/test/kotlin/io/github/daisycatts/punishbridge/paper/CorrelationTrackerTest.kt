package io.github.daisycatts.punishbridge.paper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class CorrelationTrackerTest {
    private val tracker = CorrelationTracker()

    @Test
    fun `begin then resolve returns the same id exactly once`() {
        val id = tracker.begin("key")

        assertEquals(id, tracker.resolve("key"))
        assertNull(tracker.resolve("key"))
    }

    @Test
    fun `resolving an unknown key yields null (external change)`() {
        assertNull(tracker.resolve("missing"))
    }

    @Test
    fun `register stores a provided id`() {
        val id = UUID.randomUUID()
        tracker.register("key", id)

        assertEquals(id, tracker.resolve("key"))
    }

    @Test
    fun `discard with a non-matching id keeps the entry`() {
        val id = tracker.begin("key")
        tracker.discard("key", UUID.randomUUID())

        assertEquals(id, tracker.resolve("key"))
    }

    @Test
    fun `discard with the matching id removes the entry`() {
        val id = tracker.begin("key")
        tracker.discard("key", id)

        assertNull(tracker.resolve("key"))
    }

    @Test
    fun `clear drops all pending correlations`() {
        tracker.begin("a")
        tracker.begin("b")
        tracker.clear()

        assertNull(tracker.resolve("a"))
        assertNull(tracker.resolve("b"))
    }

    @Test
    fun `begin generates distinct ids per key`() {
        assertNotEquals(tracker.begin("a"), tracker.begin("b"))
    }
}
