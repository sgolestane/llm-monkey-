package ai.llmmonkey.auth;

import ai.llmmonkey.model.UserEntity;
import ai.llmmonkey.model.UserRole;
import ai.llmmonkey.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class SsoUserMapper {

    private static final Logger log = LoggerFactory.getLogger(SsoUserMapper.class);

    private final UserRepository userRepository;

    public SsoUserMapper(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserEntity mapOidcClaims(Map<String, Object> claims) {
        String subject = (String) claims.get("sub");
        String email = (String) claims.get("email");

        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("OIDC claims must contain 'sub'");
        }

        return userRepository.findByUserEmail(email)
                .orElseGet(() -> createUser(subject, email, claims));
    }

    private UserEntity createUser(String subject, String email, Map<String, Object> claims) {
        log.info("Creating new user from SSO login: sub={}, email={}", subject, email);

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID().toString());
        user.setUserEmail(email);
        user.setSsoUserId(subject);
        user.setUserRole(UserRole.TEAM_MEMBER);

        return userRepository.save(user);
    }
}
