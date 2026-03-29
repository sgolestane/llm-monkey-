package ai.llmmonkey.budget;

public record ModelPricing(
        double inputPricePerMillion,
        double outputPricePerMillion
) {
}
