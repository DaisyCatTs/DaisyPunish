package io.github.daisycatts.punishbridge

public enum class PunishmentOperation {
    ISSUE,
    REVOKE,
    QUERY,
    OBSERVE_EXTERNAL,
}

public enum class TargetKind {
    PLAYER,
    ADDRESS,
    PLAYER_AND_ADDRESS,
}

public enum class DurationMode {
    PERMANENT,
    TEMPORARY,
}

public enum class ScopeMode {
    CURRENT_SERVER,
    GLOBAL,
    NAMED_SERVER,
}

public enum class EventFidelity {
    AUTHORITATIVE_LOCAL,
    PARTIAL_LOCAL,
    BRIDGE_ONLY,
}

public enum class ProviderTier {
    DEDICATED,
    FALLBACK,
    SYSTEM,
}

public data class CapabilityRequest(
    public val operation: PunishmentOperation,
    public val kind: PunishmentKind,
    public val targetKind: TargetKind,
    public val durationMode: DurationMode? = null,
    public val scopeMode: ScopeMode = ScopeMode.CURRENT_SERVER,
)

public data class Capability(
    public val operation: PunishmentOperation,
    public val kind: PunishmentKind,
    public val targetKinds: Set<TargetKind>,
    public val durationModes: Set<DurationMode>,
    public val scopeModes: Set<ScopeMode>,
    public val eventFidelity: EventFidelity,
) {
    public fun supports(request: CapabilityRequest): Boolean =
        operation == request.operation &&
            kind == request.kind &&
            request.targetKind in targetKinds &&
            (request.durationMode == null || request.durationMode in durationModes) &&
            request.scopeMode in scopeModes
}

public data class ProviderCapabilities(
    public val entries: Set<Capability>,
) {
    public fun supports(request: CapabilityRequest): Boolean = entries.any { it.supports(request) }

    public companion object {
        @JvmField
        public val NONE: ProviderCapabilities = ProviderCapabilities(emptySet())
    }
}

public data class ProviderDescriptor(
    public val id: String,
    public val displayName: String,
    public val bridgeVersion: String,
    public val providerVersion: String?,
    public val tier: ProviderTier,
)

public fun PunishmentTarget.kind(): TargetKind =
    when (this) {
        is PunishmentTarget.Player -> TargetKind.PLAYER
        is PunishmentTarget.Address -> TargetKind.ADDRESS
        is PunishmentTarget.PlayerAndAddress -> TargetKind.PLAYER_AND_ADDRESS
    }

public fun PunishmentDuration.mode(): DurationMode =
    when (this) {
        PunishmentDuration.Permanent -> DurationMode.PERMANENT
        is PunishmentDuration.Temporary -> DurationMode.TEMPORARY
    }

public fun PunishmentScope.mode(): ScopeMode =
    when (this) {
        PunishmentScope.CurrentServer -> ScopeMode.CURRENT_SERVER
        PunishmentScope.Global -> ScopeMode.GLOBAL
        is PunishmentScope.NamedServer -> ScopeMode.NAMED_SERVER
    }
