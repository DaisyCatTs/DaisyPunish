package io.github.daisycatts.punishbridge.provider.advancedban

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class AdvancedBanUuidTest {
    @Test
    fun `parses AdvancedBan undashed UUID`() {
        val expected = UUID.fromString("12345678-1234-5678-9abc-def012345678")

        assertEquals(expected, parseUndashedUuid("12345678123456789abcdef012345678"))
    }

    @Test
    fun `rejects malformed UUID`() {
        assertThrows(IllegalArgumentException::class.java) {
            parseUndashedUuid("not-a-uuid")
        }
    }
}
