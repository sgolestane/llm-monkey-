package ai.llmmonkey.api.dto;

import java.util.List;

public record EmbeddingResponse(
        String object,
        List<EmbeddingData> data,
        String model,
        UsageInfo usage
) {
    public record EmbeddingData(
            String object,
            int index,
            List<Double> embedding
    ) {}
}
