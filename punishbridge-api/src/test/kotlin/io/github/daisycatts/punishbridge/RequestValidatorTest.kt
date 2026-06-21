package io.github.daisycatts.punishbridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.time.Duration
import java.util.UUID

class RequestValidatorTest {
    private val player = PunishmentTarget.Player(UUID.randomUUID(), "Daisy_123")

    @Test
    fun `accepts a valid temporary mute`() {
        val request =
            PunishmentRequest(
                PunishmentKind.MUTE,
                player,
                PunishmentActor.System("chat-filter"),
                "Repeated spam",
                PunishmentDuration.Temporary(Duration.ofMinutes(10)),
            )

        assertNull(RequestValidator.validate(request))
    }

    @Test
    fun `rejects command control characters`() {
        val request = PunishmentRequest(PunishmentKind.BAN, player, reason = "bad\ncommand")

        assertEquals("Reason contains command-control characters", RequestValidator.validate(request))
    }

    @Test
    fun `rejects temporary kicks`() {
        val request =
            PunishmentRequest(
                PunishmentKind.KICK,
                player,
                reason = "spam",
                duration = PunishmentDuration.Temporary(Duration.ofSeconds(10)),
            )

        assertEquals("Kicks cannot have a duration", RequestValidator.validate(request))
    }

    @Test
    fun `rejects invalid usernames and server names`() {
        val target = PunishmentTarget.Player(UUID.randomUUID(), "not valid!")
        val request =
            PunishmentRequest(
                PunishmentKind.BAN,
                target,
                reason = "spam",
                scope = PunishmentScope.NamedServer("bad server"),
            )

        assertEquals("Invalid Minecraft username: not valid!", RequestValidator.validate(request))
    }

    @Test
    fun `accepts canonical address targets`() {
        val target = PunishmentTarget.Address(InetAddress.getByName("192.0.2.10"))
        val request = PunishmentRequest(PunishmentKind.BAN, target, reason = "abuse")

        assertNull(RequestValidator.validate(request))
    }

    @Test
    fun `rejects a blank reason`() {
        val request = PunishmentRequest(PunishmentKind.BAN, player, reason = "   ")

        assertEquals("Reason must not be blank", RequestValidator.validate(request))
    }

    @Test
    fun `rejects an overly long reason`() {
        val request = PunishmentRequest(PunishmentKind.BAN, player, reason = "x".repeat(256))

        assertEquals("Reason must not exceed 255 characters", RequestValidator.validate(request))
    }

    @Test
    fun `rejects a non-positive temporary duration`() {
        val request =
            PunishmentRequest(
                PunishmentKind.MUTE,
                player,
                reason = "spam",
                duration = PunishmentDuration.Temporary(Duration.ZERO),
            )

        assertEquals("Temporary punishment duration must be positive", RequestValidator.validate(request))
    }

    @Test
    fun `validates queries and revocations`() {
        val emptyQuery = PunishmentQuery(player, emptySet())
        assertEquals("At least one punishment kind is required", RequestValidator.validate(emptyQuery))

        val revocation = RevocationRequest(RevocationSelector.ByTarget(player, PunishmentKind.BAN), reason = "appeal granted")
        assertNull(RequestValidator.validate(revocation))
    }
}
