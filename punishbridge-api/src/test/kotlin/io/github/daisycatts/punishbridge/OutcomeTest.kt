package io.github.daisycatts.punishbridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OutcomeTest {
    @Test
    fun `map transforms success`() {
        val outcome: BridgeOutcome<Int> = BridgeOutcome.Success(2)

        assertEquals(BridgeOutcome.Success(4), outcome.map { it * 2 })
    }

    @Test
    fun `map preserves failure`() {
        val outcome: BridgeOutcome<Int> = BridgeOutcome.Unavailable("offline")

        assertEquals(BridgeOutcome.Unavailable("offline"), outcome.map { it * 2 })
    }
}
