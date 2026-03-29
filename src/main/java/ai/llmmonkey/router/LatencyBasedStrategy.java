package ai.llmmonkey.router;

import java.util.Comparator;
import java.util.List;

public final class LatencyBasedStrategy implements RoutingStrategy {

    @Override
    public DeploymentState select(List<DeploymentState> availableDeployments) {
        return availableDeployments.stream()
                .min(Comparator.comparingDouble(DeploymentState::getAvgLatencyMs))
                .orElseThrow();
    }

    @Override
    public String name() {
        return "latency-based";
    }
}
