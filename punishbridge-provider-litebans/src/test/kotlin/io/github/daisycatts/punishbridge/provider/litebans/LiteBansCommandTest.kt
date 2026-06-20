package io.github.daisycatts.punishbridge.provider.litebans

import io.github.daisycatts.punishbridge.PunishmentDuration
import io.github.daisycatts.punishbridge.PunishmentKind
import io.github.daisycatts.punishbridge.PunishmentRequest
import io.github.daisycatts.punishbridge.PunishmentTarget
import io.github.daisycatts.punishbridge.RequestValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.time.Duration
import java.util.UUID

class LiteBansCommandTest {
    @Test
    fun `uses a canonical IP without InetAddress slash prefix`() {
        val request =
            PunishmentRequest(
                PunishmentKind.BAN,
                PunishmentTarget.Address(InetAddress.getByName("192.0.2.25")),
                reason = "Automated abuse",
            )

        assertEquals("ipban 192.0.2.25 perm Automated abuse", request.toLiteBansCommand())
    }

    @Test
    fun `serializes temporary mute duration in seconds`() {
        val request =
            PunishmentRequest(
                PunishmentKind.MUTE,
                PunishmentTarget.Player(UUID.randomUUID(), "Daisy"),
                reason = "Spam",
                duration = PunishmentDuration.Temporary(Duration.ofMinutes(5)),
            )

        assertEquals("mute Daisy 300s Spam", request.toLiteBansCommand())
    }

    @Test
    fun `control characters are rejected before command construction`() {
        val request =
            PunishmentRequest(
                PunishmentKind.BAN,
                PunishmentTarget.Player(UUID.randomUUID(), "Daisy"),
                reason = "bad\ncommand",
            )

        assertNotNull(RequestValidator.validate(request))
    }
}
