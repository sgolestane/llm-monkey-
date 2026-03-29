package ai.llmmonkey.repository;

import ai.llmmonkey.model.SpendLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SpendLogRepository extends JpaRepository<SpendLogEntity, String> {

    List<SpendLogEntity> findByApiKeyHash(String apiKeyHash);

    List<SpendLogEntity> findByTeamId(UUID teamId);

    @Query("SELECT COALESCE(SUM(s.spend), 0) FROM SpendLogEntity s WHERE s.apiKeyHash = :apiKeyHash AND s.startTime >= :startTime")
    BigDecimal sumSpendByApiKeyHashAndStartTimeAfter(@Param("apiKeyHash") String apiKeyHash, @Param("startTime") Instant startTime);

    @Query(value = "SELECT * FROM spend_logs WHERE :tag = ANY(tags) ORDER BY created_at DESC", nativeQuery = true)
    List<SpendLogEntity> findByTag(@Param("tag") String tag);

    @Query(value = "SELECT * FROM spend_logs WHERE :tag = ANY(tags) AND created_at >= :since ORDER BY created_at DESC", nativeQuery = true)
    List<SpendLogEntity> findByTagAndCreatedAtAfter(@Param("tag") String tag, @Param("since") Instant since);

    @Query(value = "SELECT COALESCE(SUM(spend), 0) FROM spend_logs WHERE :tag = ANY(tags)", nativeQuery = true)
    BigDecimal sumSpendByTag(@Param("tag") String tag);

    @Query(value = "SELECT COALESCE(SUM(spend), 0) FROM spend_logs WHERE :tag = ANY(tags) AND created_at >= :since", nativeQuery = true)
    BigDecimal sumSpendByTagAndCreatedAtAfter(@Param("tag") String tag, @Param("since") Instant since);
}
