package ai.llmmonkey.router;

import java.util.Comparator;
import java.util.List;

public final class LeastBusyStrategy implements RoutingStrategy {

    @Override
    public DeploymentState select(List<DeploymentState> availableDeployments) {
        return availableDeployments.stream()
                .min(Comparator.comparingInt(DeploymentState::getActiveRequests))
                .orElseThrow();
    }

    @Override
    public String name() {
        return "least-busy";
    }
}
