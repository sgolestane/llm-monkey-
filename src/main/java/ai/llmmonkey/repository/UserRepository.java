package ai.llmmonkey.repository;

import ai.llmmonkey.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {

    Optional<UserEntity> findByUserEmail(String userEmail);

    List<UserEntity> findByOrganizationId(UUID organizationId);
}
