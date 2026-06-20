package io.github.daisycatts.punishbridge

import java.net.InetAddress
import java.time.Duration
import java.time.Instant
import java.util.UUID

public enum class PunishmentKind {
    BAN,
    MUTE,
    WARNING,
    KICK,
}

public sealed interface PunishmentTarget {
    public data class Player(
        public val uuid: UUID,
        public val username: String,
    ) : PunishmentTarget

    public data class Address(
        public val address: InetAddress,
    ) : PunishmentTarget

    public data class PlayerAndAddress(
        public val uuid: UUID,
        public val username: String,
        public val address: InetAddress,
    ) : PunishmentTarget
}

public sealed interface PunishmentActor {
    public data object Console : PunishmentActor

    public data class Player(
        public val uuid: UUID,
        public val username: String,
    ) : PunishmentActor

    public data class System(
        public val componentName: String,
    ) : PunishmentActor
}

public sealed interface PunishmentDuration {
    public data object Permanent : PunishmentDuration

    public data class Temporary(
        public val duration: Duration,
    ) : PunishmentDuration
}

public sealed interface PunishmentScope {
    public data object CurrentServer : PunishmentScope

    public data object Global : PunishmentScope

    public data class NamedServer(
        public val name: String,
    ) : PunishmentScope
}

public data class PunishmentRequest(
    public val kind: PunishmentKind,
    public val target: PunishmentTarget,
    public val actor: PunishmentActor = PunishmentActor.Console,
    public val reason: String,
    public val duration: PunishmentDuration = PunishmentDuration.Permanent,
    public val scope: PunishmentScope = PunishmentScope.CurrentServer,
)

public sealed interface RevocationSelector {
    public data class ByReference(
        public val reference: PunishmentReference,
    ) : RevocationSelector

    public data class ByTarget(
        public val target: PunishmentTarget,
        public val kind: PunishmentKind,
        public val scope: PunishmentScope = PunishmentScope.CurrentServer,
    ) : RevocationSelector
}

public data class RevocationRequest(
    public val selector: RevocationSelector,
    public val actor: PunishmentActor = PunishmentActor.Console,
    public val reason: String? = null,
)

public data class PunishmentQuery(
    public val target: PunishmentTarget,
    public val kinds: Set<PunishmentKind>,
    public val scope: PunishmentScope = PunishmentScope.CurrentServer,
)

public data class PunishmentReference(
    public val providerId: String,
    public val externalId: String,
)

public data class PunishmentRecord(
    public val providerId: String,
    public val reference: PunishmentReference?,
    public val kind: PunishmentKind,
    public val target: PunishmentTarget,
    public val actor: PunishmentActor?,
    public val reason: String?,
    public val startsAt: Instant?,
    public val expiresAt: Instant?,
    public val scope: PunishmentScope?,
    public val fidelity: DataFidelity = DataFidelity.COMPLETE,
)

public enum class DataFidelity {
    COMPLETE,
    PARTIAL,
}

public enum class ReceiptStatus {
    APPLIED,
    ACCEPTED,
}

public data class OperationReceipt(
    public val providerId: String,
    public val correlationId: UUID,
    public val status: ReceiptStatus,
    public val reference: PunishmentReference? = null,
)

public data class RevocationReceipt(
    public val providerId: String,
    public val correlationId: UUID,
    public val revokedCount: Int?,
)
