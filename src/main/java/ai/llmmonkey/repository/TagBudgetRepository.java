package ai.llmmonkey.repository;

import ai.llmmonkey.model.TagBudgetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagBudgetRepository extends JpaRepository<TagBudgetEntity, UUID> {

    Optional<TagBudgetEntity> findByTag(String tag);

    List<TagBudgetEntity> findByTagIn(List<String> tags);
}
