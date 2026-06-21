package io.github.daisycatts.punishbridge.paper

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks correlation IDs for bridge-issued operations so the matching provider event can be linked
 * back to the request that caused it. A resolved correlation marks an event as bridge-originated;
 * an unresolved one marks it as an external change.
 */
public class CorrelationTracker {
    private val pending: ConcurrentHashMap<String, UUID> = ConcurrentHashMap()

    /** Registers a freshly generated correlation ID under [key] and returns it. */
    public fun begin(key: String): UUID = UUID.randomUUID().also { pending[key] = it }

    /** Registers an existing [correlationId] under [key], e.g. when one ID spans a request and its event. */
    public fun register(
        key: String,
        correlationId: UUID,
    ) {
        pending[key] = correlationId
    }

    /** Consumes and returns the correlation ID for [key], or `null` if the change was external. */
    public fun resolve(key: String): UUID? = pending.remove(key)

    /** Drops a pending correlation, e.g. when the operation was rejected before it took effect. */
    public fun discard(
        key: String,
        correlationId: UUID,
    ) {
        pending.remove(key, correlationId)
    }

    /** Clears all pending correlations; call on provider shutdown. */
    public fun clear(): Unit = pending.clear()
}
