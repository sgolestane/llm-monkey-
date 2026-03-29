package ai.llmmonkey.router;

import ai.llmmonkey.config.ModelDeploymentConfig;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DeploymentPool {

    private final Map<String, List<DeploymentState>> deploymentsByModel = new ConcurrentHashMap<>();
    private final Map<String, DeploymentState> deploymentsById = new ConcurrentHashMap<>();

    public void addDeployment(ModelDeploymentConfig config) {
        var state = new DeploymentState(config);
        deploymentsById.put(config.deploymentId(), state);
        deploymentsByModel.computeIfAbsent(config.modelName(), k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                .add(state);
    }

    public List<DeploymentState> getHealthyDeployments(String modelName) {
        var deployments = deploymentsByModel.get(modelName);
        if (deployments == null || deployments.isEmpty()) return List.of();
        return deployments.stream()
                .filter(DeploymentState::isHealthy)
                .collect(Collectors.toList());
    }

    public List<DeploymentState> getAllDeployments(String modelName) {
        return deploymentsByModel.getOrDefault(modelName, List.of());
    }

    public DeploymentState getDeployment(String deploymentId) {
        return deploymentsById.get(deploymentId);
    }

    public List<String> getAvailableModels() {
        return List.copyOf(deploymentsByModel.keySet());
    }

    public boolean hasModel(String modelName) {
        return deploymentsByModel.containsKey(modelName);
    }
}
