package vera.lms.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vera.lms.models.CheckpointSession;

import java.util.List;
import java.util.Optional;

@Repository
public interface CheckpointSessionRepository extends JpaRepository<CheckpointSession, Long>, JpaSpecificationExecutor<CheckpointSession> {

    @EntityGraph(attributePaths = {"checkpoint", "checkpoint.program", "evaluator", "evaluator.evaluatorProfile"})
    @Query("SELECT s FROM CheckpointSession s WHERE s.id = :id")
    Optional<CheckpointSession> findWithCheckpointById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"checkpoint", "checkpoint.program", "evaluator", "evaluator.evaluatorProfile"})
    List<CheckpointSession> findByEvaluatorIdOrderByScheduledAtDesc(Long evaluatorId);

}
