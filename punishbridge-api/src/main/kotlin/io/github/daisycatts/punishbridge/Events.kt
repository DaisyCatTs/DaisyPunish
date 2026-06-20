package io.github.daisycatts.punishbridge

import java.time.Instant
import java.util.UUID

public enum class EventOrigin {
    BRIDGE,
    EXTERNAL_PROVIDER,
}

public sealed interface BridgeEvent {
    public val providerId: String
    public val observedAt: Instant
    public val origin: EventOrigin
    public val correlationId: UUID?
    public val fidelity: EventFidelity

    public data class PunishmentApplied(
        override val providerId: String,
        override val observedAt: Instant,
        override val origin: EventOrigin,
        override val correlationId: UUID?,
        override val fidelity: EventFidelity,
        public val punishment: PunishmentRecord,
    ) : BridgeEvent

    public data class PunishmentRevoked(
        override val providerId: String,
        override val observedAt: Instant,
        override val origin: EventOrigin,
        override val correlationId: UUID?,
        override val fidelity: EventFidelity,
        public val punishment: PunishmentRecord?,
        public val selector: RevocationSelector?,
    ) : BridgeEvent

    public data class OperationAccepted(
        override val providerId: String,
        override val observedAt: Instant,
        override val origin: EventOrigin = EventOrigin.BRIDGE,
        override val correlationId: UUID,
        override val fidelity: EventFidelity,
        public val request: PunishmentRequest,
    ) : BridgeEvent

    public data class ProviderUnavailable(
        override val providerId: String,
        override val observedAt: Instant,
        override val origin: EventOrigin = EventOrigin.BRIDGE,
        override val correlationId: UUID? = null,
        override val fidelity: EventFidelity = EventFidelity.BRIDGE_ONLY,
        public val reason: String,
    ) : BridgeEvent
}
