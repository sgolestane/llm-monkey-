package ai.llmmonkey.router;

import java.util.Comparator;
import java.util.List;

public final class UsageBasedStrategy implements RoutingStrategy {

    @Override
    public DeploymentState select(List<DeploymentState> availableDeployments) {
        return availableDeployments.stream()
                .min(Comparator.comparingLong(DeploymentState::getTotalTokens))
                .orElseThrow();
    }

    @Override
    public String name() {
        return "usage-based";
    }
}
