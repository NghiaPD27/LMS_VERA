package vera.lms.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vera.lms.enums.AssessmentResult;
import vera.lms.models.CheckpointResult;

import java.util.Optional;

@Repository
public interface CheckpointResultRepository extends JpaRepository<CheckpointResult, Long> {

    boolean existsByParticipantId(Long participantId);

    Optional<CheckpointResult> findByParticipantId(Long participantId);

    @EntityGraph(attributePaths = {"participant"})
    @Query("""
            SELECT r FROM CheckpointResult r
            WHERE r.participant.id = :participantId
            """)
    Optional<CheckpointResult> findWithParticipantByParticipantId(@Param("participantId") Long participantId);

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM CheckpointResult r
            WHERE r.participant.enrollment.id = :enrollmentId
            AND r.participant.session.checkpoint.id = :checkpointId
            AND r.result = :result
            """)
    boolean existsByEnrollmentAndCheckpointAndResult(
            @Param("enrollmentId") Long enrollmentId,
            @Param("checkpointId") Long checkpointId,
            @Param("result") AssessmentResult result);
}
