package ai.llmmonkey.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record ModelDeploymentConfig(
        String modelName,
        String provider,
        ProviderParamsConfig params
) {
    public String deploymentId() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var input = provider + ":" + params.model() + ":" + params.apiBase();
            var hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
