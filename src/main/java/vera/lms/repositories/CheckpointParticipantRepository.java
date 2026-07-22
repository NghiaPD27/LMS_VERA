package vera.lms.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vera.lms.models.CheckpointParticipant;

import java.util.List;
import java.util.Optional;

@Repository
public interface CheckpointParticipantRepository extends JpaRepository<CheckpointParticipant, Long> {

    @EntityGraph(attributePaths = {
            "session",
            "session.checkpoint",
            "session.checkpoint.program",
            "session.evaluator",
            "student",
            "student.studentProfile",
            "enrollment",
            "enrollment.program"
    })
    @Query("SELECT p FROM CheckpointParticipant p WHERE p.id = :id")
    Optional<CheckpointParticipant> findWithDetailsById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"student", "student.studentProfile", "enrollment", "enrollment.program"})
    List<CheckpointParticipant> findBySessionIdOrderByIdAsc(Long sessionId);

    boolean existsBySessionIdAndEnrollmentId(Long sessionId, Long enrollmentId);

    long countBySessionId(Long sessionId);

    @Query("""
            SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
            FROM CheckpointParticipant p
            WHERE p.enrollment.id = :enrollmentId
            AND p.session.checkpoint.id = :checkpointId
            AND p.session.status = vera.lms.enums.CheckpointSessionStatus.PENDING
            AND NOT EXISTS (
                SELECT r.id FROM CheckpointResult r
                WHERE r.participant.id = p.id
            )
            """)
    boolean existsPendingForEnrollmentAndCheckpoint(
            @Param("enrollmentId") Long enrollmentId,
            @Param("checkpointId") Long checkpointId);

    @EntityGraph(attributePaths = {
            "session",
            "session.checkpoint",
            "session.checkpoint.program",
            "session.evaluator",
            "student",
            "student.studentProfile",
            "enrollment",
            "enrollment.program"
    })
    @Query("""
            SELECT p FROM CheckpointParticipant p
            WHERE p.student.id = :studentId
            AND p.enrollment.id = :enrollmentId
            AND p.session.checkpoint.id = :checkpointId
            ORDER BY p.addedAt DESC
            """)
    List<CheckpointParticipant> findStudentCheckpointParticipants(
            @Param("studentId") Long studentId,
            @Param("enrollmentId") Long enrollmentId,
            @Param("checkpointId") Long checkpointId);
}
