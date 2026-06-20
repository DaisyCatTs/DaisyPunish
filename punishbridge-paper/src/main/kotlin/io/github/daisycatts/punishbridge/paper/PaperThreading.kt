package io.github.daisycatts.punishbridge.paper

import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Server
import org.bukkit.plugin.Plugin
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun <T> Server.onServerThread(
    plugin: Plugin,
    action: () -> T,
): T {
    if (isPrimaryThread) return action()
    return suspendCancellableCoroutine { continuation ->
        scheduler.runTask(
            plugin,
            Runnable {
                if (!continuation.isActive) return@Runnable
                runCatching(action)
                    .onSuccess(continuation::resume)
                    .onFailure(continuation::resumeWithException)
            },
        )
    }
}
