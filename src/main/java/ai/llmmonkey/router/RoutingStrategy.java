package ai.llmmonkey.router;

import java.util.List;

public sealed interface RoutingStrategy
        permits RoundRobinStrategy, LeastBusyStrategy, LatencyBasedStrategy,
                CostBasedStrategy, UsageBasedStrategy {

    DeploymentState select(List<DeploymentState> availableDeployments);

    String name();
}
