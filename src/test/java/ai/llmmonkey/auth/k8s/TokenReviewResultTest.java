package ai.llmmonkey.auth.k8s;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenReviewResultTest {

    @Test
    void parsesValidServiceAccountUsername() {
        var result = TokenReviewResult.fromUsername("system:serviceaccount:billing:billing-api");

        assertTrue(result.authenticated());
        assertEquals("system:serviceaccount:billing:billing-api", result.username());
        assertEquals("billing", result.namespace());
        assertEquals("billing-api", result.serviceAccountName());
    }

    @Test
    void returnsUnauthenticatedForNullUsername() {
        var result = TokenReviewResult.fromUsername(null);
        assertFalse(result.authenticated());
    }

    @Test
    void returnsUnauthenticatedForNonServiceAccountUsername() {
        var result = TokenReviewResult.fromUsername("user:alice");
        assertFalse(result.authenticated());
    }

    @Test
    void returnsUnauthenticatedForMalformedServiceAccount() {
        var result = TokenReviewResult.fromUsername("system:serviceaccount:missingcolon");
        assertFalse(result.authenticated());
    }

    @Test
    void returnsUnauthenticatedForTrailingColon() {
        var result = TokenReviewResult.fromUsername("system:serviceaccount:ns:");
        assertFalse(result.authenticated());
    }

    @Test
    void handlesServiceAccountWithDashes() {
        var result = TokenReviewResult.fromUsername("system:serviceaccount:my-namespace:my-service-account");

        assertTrue(result.authenticated());
        assertEquals("my-namespace", result.namespace());
        assertEquals("my-service-account", result.serviceAccountName());
    }

    @Test
    void unauthenticatedFactoryMethod() {
        var result = TokenReviewResult.unauthenticated();

        assertFalse(result.authenticated());
        assertNull(result.username());
        assertNull(result.namespace());
        assertNull(result.serviceAccountName());
    }
}
