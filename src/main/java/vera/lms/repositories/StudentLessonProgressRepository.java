package vera.lms.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vera.lms.enums.LessonProgressStatus;
import vera.lms.models.StudentLessonProgress;
import vera.lms.models.StudentLessonProgressId;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentLessonProgressRepository extends JpaRepository<StudentLessonProgress, StudentLessonProgressId> {
    List<StudentLessonProgress> findByStudentId(Long studentId);
    Optional<StudentLessonProgress> findByStudentIdAndLessonId(Long studentId, Long lessonId);
    boolean existsByLessonIdAndStatus(Long lessonId, LessonProgressStatus status);
    long countByLessonId(Long lessonId);

    @Query("""
            SELECT p FROM StudentLessonProgress p
            JOIN FETCH p.student s
            LEFT JOIN FETCH s.studentProfile
            JOIN FETCH p.lesson l
            JOIN FETCH l.program program
            WHERE p.status = :status
            AND l.status = vera.lms.enums.LessonStatus.PUBLISHED
            AND MOD(l.lessonNumber, :checkpointInterval) = 0
            AND l.lessonNumber BETWEEN :minGateLesson AND :maxGateLesson
            AND (:programId IS NULL OR program.id = :programId)
            AND (:blockNumber IS NULL OR l.lessonNumber = (:blockNumber * :checkpointInterval))
            ORDER BY program.id ASC, l.lessonNumber ASC, s.id ASC
            """)
    List<StudentLessonProgress> findCheckpointReadyProgresses(
            @Param("status") LessonProgressStatus status,
            @Param("checkpointInterval") int checkpointInterval,
            @Param("minGateLesson") int minGateLesson,
            @Param("maxGateLesson") int maxGateLesson,
            @Param("programId") Long programId,
            @Param("blockNumber") Integer blockNumber);
}
