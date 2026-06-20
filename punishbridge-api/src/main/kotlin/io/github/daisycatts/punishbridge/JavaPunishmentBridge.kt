package io.github.daisycatts.punishbridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import java.util.concurrent.CompletionStage
import java.util.function.Consumer

public class JavaPunishmentBridge(
    public val delegate: PunishmentBridge,
) : AutoCloseable {
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    public fun issue(request: PunishmentRequest): CompletionStage<BridgeOutcome<OperationReceipt>> =
        scope.future { delegate.issue(request) }

    public fun revoke(request: RevocationRequest): CompletionStage<BridgeOutcome<RevocationReceipt>> =
        scope.future { delegate.revoke(request) }

    public fun findActive(query: PunishmentQuery): CompletionStage<BridgeOutcome<List<PunishmentRecord>>> =
        scope.future { delegate.findActive(query) }

    public fun isMuted(target: PunishmentTarget): CompletionStage<BridgeOutcome<Boolean>> = scope.future { delegate.isMuted(target) }

    public fun isBanned(target: PunishmentTarget): CompletionStage<BridgeOutcome<Boolean>> = scope.future { delegate.isBanned(target) }

    public fun subscribe(listener: Consumer<BridgeEvent>): AutoCloseable {
        val job =
            scope.launch {
                delegate.events.collect { event -> runCatching { listener.accept(event) } }
            }
        return AutoCloseable { job.cancel() }
    }

    override fun close() {
        scope.cancel()
        delegate.close()
    }
}
