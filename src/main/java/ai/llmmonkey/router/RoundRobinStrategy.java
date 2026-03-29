package ai.llmmonkey.router;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class RoundRobinStrategy implements RoutingStrategy {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public DeploymentState select(List<DeploymentState> availableDeployments) {
        int index = Math.abs(counter.getAndIncrement() % availableDeployments.size());
        return availableDeployments.get(index);
    }

    @Override
    public String name() {
        return "round-robin";
    }
}
