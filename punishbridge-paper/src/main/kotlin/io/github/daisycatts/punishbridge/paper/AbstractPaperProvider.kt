package io.github.daisycatts.punishbridge.paper

import io.github.daisycatts.punishbridge.BridgeEvent
import io.github.daisycatts.punishbridge.BridgeOutcome
import io.github.daisycatts.punishbridge.EventFidelity
import io.github.daisycatts.punishbridge.EventOrigin
import io.github.daisycatts.punishbridge.PunishmentRecord
import io.github.daisycatts.punishbridge.RevocationSelector
import java.util.UUID

/**
 * Base class for [PaperPunishmentProvider] implementations. Centralises the plumbing every adapter
 * otherwise duplicates: failure wrapping, correlation tracking, and event emission with the correct
 * [EventOrigin]. Subclasses only supply provider-specific mapping and the [descriptor]/[capabilities].
 */
public abstract class AbstractPaperProvider(
    protected val context: PaperProviderContext,
) : PaperPunishmentProvider {
    protected val correlations: CorrelationTracker = CorrelationTracker()

    /** Runs [action], converting any thrown error into a typed [BridgeOutcome.Failed]. */
    protected suspend fun <T> runProviderOperation(
        label: String,
        action: suspend () -> BridgeOutcome<T>,
    ): BridgeOutcome<T> =
        try {
            action()
        } catch (error: Throwable) {
            BridgeOutcome.Failed(descriptor.id, "Failed to $label", error)
        }

    /** Emits a [BridgeEvent.PunishmentApplied], linking it to its originating request when [correlationKey] resolves. */
    protected fun emitApplied(
        record: PunishmentRecord,
        fidelity: EventFidelity,
        correlationKey: String? = null,
    ) {
        val correlationId = correlationKey?.let(correlations::resolve)
        context.emit(
            BridgeEvent.PunishmentApplied(
                descriptor.id,
                context.clock.instant(),
                originOf(correlationId),
                correlationId,
                fidelity,
                record,
            ),
        )
    }

    /** Emits a [BridgeEvent.PunishmentRevoked], linking it to its originating request when [correlationKey] resolves. */
    protected fun emitRevoked(
        record: PunishmentRecord?,
        fidelity: EventFidelity,
        correlationKey: String? = null,
        selector: RevocationSelector? = null,
    ) {
        val correlationId = correlationKey?.let(correlations::resolve)
        context.emit(
            BridgeEvent.PunishmentRevoked(
                descriptor.id,
                context.clock.instant(),
                originOf(correlationId),
                correlationId,
                fidelity,
                record,
                selector,
            ),
        )
    }

    protected fun originOf(correlationId: UUID?): EventOrigin =
        if (correlationId == null) EventOrigin.EXTERNAL_PROVIDER else EventOrigin.BRIDGE
}
