package vera.lms.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vera.lms.enums.FinalAssessmentSessionStatus;
import vera.lms.models.FinalAssessmentSession;

import java.util.List;
import java.util.Optional;

@Repository
public interface FinalAssessmentSessionRepository
        extends JpaRepository<FinalAssessmentSession, Long>, JpaSpecificationExecutor<FinalAssessmentSession> {
    long countByStatus(FinalAssessmentSessionStatus status);

    @EntityGraph(attributePaths = {"program", "evaluator", "evaluator.evaluatorProfile"})
    @Query("SELECT s FROM FinalAssessmentSession s WHERE s.id = :id")
    Optional<FinalAssessmentSession> findWithDetailsById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"program", "evaluator", "evaluator.evaluatorProfile"})
    List<FinalAssessmentSession> findByEvaluatorIdOrderByScheduledAtDesc(Long evaluatorId);
}
