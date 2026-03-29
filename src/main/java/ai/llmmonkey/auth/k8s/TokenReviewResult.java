package ai.llmmonkey.auth.k8s;

/**
 * Result of a Kubernetes TokenReview API call.
 *
 * @param authenticated whether the token was valid
 * @param username      full identity, e.g. system:serviceaccount:billing:billing-api
 * @param namespace     extracted namespace from the username
 * @param serviceAccountName extracted service account name from the username
 */
public record TokenReviewResult(
        boolean authenticated,
        String username,
        String namespace,
        String serviceAccountName
) {
    private static final String SA_PREFIX = "system:serviceaccount:";

    public static TokenReviewResult unauthenticated() {
        return new TokenReviewResult(false, null, null, null);
    }

    public static TokenReviewResult fromUsername(String username) {
        if (username == null || !username.startsWith(SA_PREFIX)) {
            return unauthenticated();
        }
        String remainder = username.substring(SA_PREFIX.length());
        int colonIndex = remainder.indexOf(':');
        if (colonIndex <= 0 || colonIndex == remainder.length() - 1) {
            return unauthenticated();
        }
        String namespace = remainder.substring(0, colonIndex);
        String serviceAccountName = remainder.substring(colonIndex + 1);
        return new TokenReviewResult(true, username, namespace, serviceAccountName);
    }
}
