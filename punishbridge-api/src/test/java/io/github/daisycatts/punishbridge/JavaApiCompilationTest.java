package io.github.daisycatts.punishbridge;

import java.util.concurrent.CompletionStage;

final class JavaApiCompilationTest {
    CompletionStage<BridgeOutcome<Boolean>> query(
            JavaPunishmentBridge bridge,
            PunishmentTarget target
    ) {
        return bridge.isMuted(target);
    }

    AutoCloseable listen(JavaPunishmentBridge bridge) {
        return bridge.subscribe(event -> event.getProviderId());
    }
}
