package vera.lms.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vera.lms.enums.AssessmentResult;
import vera.lms.models.FinalAssessmentResult;

import java.util.List;
import java.util.Optional;

@Repository
public interface FinalAssessmentResultRepository extends JpaRepository<FinalAssessmentResult, Long> {

    Optional<FinalAssessmentResult> findByParticipantId(Long participantId);

    boolean existsByParticipantId(Long participantId);

    long countByParticipantSessionId(Long sessionId);

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM FinalAssessmentResult r
            WHERE r.participant.enrollment.id = :enrollmentId
            AND r.result = :result
            """)
    boolean existsByEnrollmentAndResult(
            @Param("enrollmentId") Long enrollmentId,
            @Param("result") AssessmentResult result);

    @Query("""
            SELECT r FROM FinalAssessmentResult r
            JOIN FETCH r.participant p
            JOIN FETCH p.session s
            JOIN FETCH s.program
            JOIN FETCH r.evaluator evaluator
            LEFT JOIN FETCH evaluator.evaluatorProfile
            WHERE p.enrollment.id = :enrollmentId
            ORDER BY r.evaluatedAt DESC
            """)
    List<FinalAssessmentResult> findByEnrollmentIdOrderByEvaluatedAtDesc(@Param("enrollmentId") Long enrollmentId);
}
