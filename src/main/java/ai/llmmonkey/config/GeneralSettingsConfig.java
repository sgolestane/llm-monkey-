package ai.llmmonkey.config;

public record GeneralSettingsConfig(
        boolean databaseEnabled,
        boolean cacheEnabled,
        boolean rateLimitEnabled
) {
    public static GeneralSettingsConfig defaults() {
        return new GeneralSettingsConfig(true, true, true);
    }
}
