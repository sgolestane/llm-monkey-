package ai.llmmonkey.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ModelListResponse(
        String object,
        List<ModelData> data
) {
    public record ModelData(
            String id,
            String object,
            long created,
            @JsonProperty("owned_by") String ownedBy
    ) {}
}
