package ai.llmmonkey.auth;

import ai.llmmonkey.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
public class JwtValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtValidator.class);

    private final SecretKey signingKey;

    public JwtValidator(@Value("${llm-monkey.jwt.secret:}") String jwtSecret) {
        if (jwtSecret != null && !jwtSecret.isBlank()) {
            this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        } else {
            this.signingKey = null;
        }
    }

    public Optional<AuthContext> validate(String token) {
        if (signingKey == null) {
            log.warn("JWT secret not configured; JWT validation is disabled");
            return Optional.empty();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String subject = claims.getSubject();
            String email = claims.get("email", String.class);
            String roleStr = claims.get("role", String.class);

            UserRole role = UserRole.TEAM_MEMBER;
            if (roleStr != null) {
                try {
                    role = UserRole.valueOf(roleStr);
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown role in JWT: {}", roleStr);
                }
            }

            AuthContext ctx = new AuthContext(
                    null,    // apiKeyHash
                    subject, // userId
                    null,    // teamId
                    null,    // organizationId
                    role,
                    null,    // maxBudget
                    null,    // tpmLimit
                    null,    // rpmLimit
                    null,    // k8sNamespace
                    null     // k8sServiceAccount
            );

            return Optional.of(ctx);
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
