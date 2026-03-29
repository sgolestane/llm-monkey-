package ai.llmmonkey.repository;

import ai.llmmonkey.model.TeamEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamRepository extends JpaRepository<TeamEntity, UUID> {

    List<TeamEntity> findByOrganizationId(UUID organizationId);

    Optional<TeamEntity> findByTeamAlias(String teamAlias);
}
