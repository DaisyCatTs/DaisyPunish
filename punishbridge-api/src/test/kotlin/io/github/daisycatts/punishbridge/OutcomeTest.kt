package io.github.daisycatts.punishbridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

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

    @Test
    fun `map passes non-success outcomes through unchanged`() {
        val accepted: BridgeOutcome<Int> =
            BridgeOutcome.Accepted(OperationReceipt("p", UUID.randomUUID(), ReceiptStatus.ACCEPTED))
        val rejected: BridgeOutcome<Int> = BridgeOutcome.Rejected("nope")

        assertEquals(accepted, accepted.map { it * 2 })
        assertEquals(rejected, rejected.map { it * 2 })
    }
}
