package ai.llmmonkey.provider;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ProviderRegistry {

    private final Map<ProviderType, LlmProvider> providers = new EnumMap<>(ProviderType.class);

    public ProviderRegistry(List<LlmProvider> providerList) {
        for (var provider : providerList) {
            providers.put(provider.type(), provider);
        }
    }

    public LlmProvider getProvider(ProviderType type) {
        var provider = providers.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("No provider registered for type: " + type);
        }
        return provider;
    }

    public LlmProvider getProvider(String providerName) {
        return getProvider(ProviderType.fromString(providerName));
    }

    public boolean hasProvider(ProviderType type) {
        return providers.containsKey(type);
    }
}
