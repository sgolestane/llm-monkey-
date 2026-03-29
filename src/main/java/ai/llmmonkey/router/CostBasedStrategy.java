package ai.llmmonkey.router;

import ai.llmmonkey.budget.ModelPricingRegistry;

import java.util.Comparator;
import java.util.List;

public final class CostBasedStrategy implements RoutingStrategy {

    private final ModelPricingRegistry pricingRegistry;

    public CostBasedStrategy(ModelPricingRegistry pricingRegistry) {
        this.pricingRegistry = pricingRegistry;
    }

    @Override
    public DeploymentState select(List<DeploymentState> availableDeployments) {
        return availableDeployments.stream()
                .min(Comparator.comparingDouble(d ->
                        pricingRegistry.getInputPricePerToken(d.getConfig().params().model())))
                .orElseThrow();
    }

    @Override
    public String name() {
        return "cost-based";
    }
}
