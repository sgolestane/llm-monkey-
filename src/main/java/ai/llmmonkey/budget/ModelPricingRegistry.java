package ai.llmmonkey.budget;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;

@Component
public class ModelPricingRegistry {

    private static final Map<String, ModelPricing> PRICING = Map.of(
            "gpt-4", new ModelPricing(30.0, 60.0),
            "gpt-4o", new ModelPricing(2.5, 10.0),
            "gpt-3.5-turbo", new ModelPricing(0.5, 1.5),
            "claude-sonnet", new ModelPricing(3.0, 15.0),
            "claude-opus", new ModelPricing(15.0, 75.0)
    );

    public double getInputPricePerToken(String model) {
        ModelPricing pricing = PRICING.get(model);
        if (pricing == null) {
            return 0.0;
        }
        return pricing.inputPricePerMillion() / 1_000_000.0;
    }

    public double getOutputPricePerToken(String model) {
        ModelPricing pricing = PRICING.get(model);
        if (pricing == null) {
            return 0.0;
        }
        return pricing.outputPricePerMillion() / 1_000_000.0;
    }

    public BigDecimal calculateCost(String model, int promptTokens, int completionTokens) {
        double inputCost = getInputPricePerToken(model) * promptTokens;
        double outputCost = getOutputPricePerToken(model) * completionTokens;
        return BigDecimal.valueOf(inputCost + outputCost).round(new MathContext(10));
    }
}
