package vera.lms.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vera.lms.enums.CheckpointSessionStatus;
import vera.lms.models.CheckpointSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CheckpointSessionRepository extends JpaRepository<CheckpointSession, Long> {

    @EntityGraph(attributePaths = {"checkpoint", "checkpoint.program", "evaluator", "evaluator.evaluatorProfile"})
    @Query("SELECT s FROM CheckpointSession s WHERE s.id = :id")
    Optional<CheckpointSession> findWithCheckpointById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"checkpoint", "checkpoint.program", "evaluator", "evaluator.evaluatorProfile"})
    List<CheckpointSession> findByEvaluatorIdOrderByScheduledAtDesc(Long evaluatorId);

    @EntityGraph(attributePaths = {"checkpoint", "checkpoint.program", "evaluator", "evaluator.evaluatorProfile"})
    @Query("""
            SELECT s FROM CheckpointSession s
            WHERE (:programId IS NULL OR s.checkpoint.program.id = :programId)
            AND (:blockNumber IS NULL OR s.checkpoint.blockNumber = :blockNumber)
            AND (:status IS NULL OR s.status = :status)
            AND (:weekStart IS NULL OR s.scheduledAt >= :weekStart)
            AND (:weekEnd IS NULL OR s.scheduledAt < :weekEnd)
            """)
    Page<CheckpointSession> searchAdminSessions(
            @Param("programId") Long programId,
            @Param("blockNumber") Integer blockNumber,
            @Param("status") CheckpointSessionStatus status,
            @Param("weekStart") Instant weekStart,
            @Param("weekEnd") Instant weekEnd,
            Pageable pageable);
}
