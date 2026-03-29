package ai.llmmonkey.repository;

import ai.llmmonkey.model.VerificationTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationTokenEntity, String> {

    List<VerificationTokenEntity> findByUserId(String userId);

    List<VerificationTokenEntity> findByTeamId(UUID teamId);

    List<VerificationTokenEntity> findByOrganizationId(UUID organizationId);
}
