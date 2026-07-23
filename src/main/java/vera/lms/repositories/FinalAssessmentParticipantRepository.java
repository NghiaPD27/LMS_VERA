package vera.lms.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vera.lms.models.FinalAssessmentParticipant;

import java.util.List;
import java.util.Optional;

@Repository
public interface FinalAssessmentParticipantRepository extends JpaRepository<FinalAssessmentParticipant, Long> {

    @EntityGraph(attributePaths = {
            "session", "session.program", "session.evaluator", "session.evaluator.evaluatorProfile",
            "enrollment", "enrollment.program", "student", "student.studentProfile", "retakePayment"})
    @Query("SELECT p FROM FinalAssessmentParticipant p WHERE p.id = :id")
    Optional<FinalAssessmentParticipant> findWithDetailsById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"enrollment", "student", "student.studentProfile", "retakePayment"})
    List<FinalAssessmentParticipant> findBySessionIdOrderByIdAsc(Long sessionId);

    boolean existsBySessionIdAndEnrollmentId(Long sessionId, Long enrollmentId);

    long countBySessionId(Long sessionId);

    @Query("""
            SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
            FROM FinalAssessmentParticipant p
            WHERE p.enrollment.id = :enrollmentId
            AND p.session.status = vera.lms.enums.FinalAssessmentSessionStatus.PENDING
            """)
    boolean existsPendingForEnrollment(@Param("enrollmentId") Long enrollmentId);

    @Query("""
            SELECT p FROM FinalAssessmentParticipant p
            JOIN FETCH p.session s
            JOIN FETCH s.program
            JOIN FETCH s.evaluator evaluator
            LEFT JOIN FETCH evaluator.evaluatorProfile
            LEFT JOIN FETCH p.retakePayment
            WHERE p.enrollment.id = :enrollmentId
            ORDER BY p.id DESC
            """)
    List<FinalAssessmentParticipant> findEnrollmentParticipantsNewestFirst(@Param("enrollmentId") Long enrollmentId);
}
