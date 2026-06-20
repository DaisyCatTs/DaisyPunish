package io.github.daisycatts.punishbridge

public sealed interface BridgeOutcome<out T> {
    public data class Success<T>(
        public val value: T,
    ) : BridgeOutcome<T>

    public data class Accepted(
        public val receipt: OperationReceipt,
    ) : BridgeOutcome<Nothing>

    public data class Unsupported(
        public val capability: CapabilityRequest,
    ) : BridgeOutcome<Nothing>

    public data class Rejected(
        public val reason: String,
    ) : BridgeOutcome<Nothing>

    public data class Unavailable(
        public val reason: String,
    ) : BridgeOutcome<Nothing>

    public data class Failed(
        public val providerId: String,
        public val message: String,
        public val cause: Throwable? = null,
    ) : BridgeOutcome<Nothing>
}

public inline fun <T, R> BridgeOutcome<T>.map(transform: (T) -> R): BridgeOutcome<R> =
    when (this) {
        is BridgeOutcome.Success -> BridgeOutcome.Success(transform(value))
        is BridgeOutcome.Accepted -> this
        is BridgeOutcome.Unsupported -> this
        is BridgeOutcome.Rejected -> this
        is BridgeOutcome.Unavailable -> this
        is BridgeOutcome.Failed -> this
    }
