package vera.lms.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vera.lms.models.QuizAttempt;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    long countByQuizIdAndStudentId(Long quizId, Long studentId);

    boolean existsByQuizId(Long quizId);

    @Query("SELECT a FROM QuizAttempt a WHERE a.id = :id")
    Optional<QuizAttempt> findWithDetailsById(@Param("id") Long id);

    @Query("""
            SELECT MAX(a.scorePercent)
            FROM QuizAttempt a
            WHERE a.quiz.id = :quizId
            AND a.student.id = :studentId
            AND a.submitted = true
            """)
    Integer findBestScorePercent(@Param("quizId") Long quizId, @Param("studentId") Long studentId);

    @EntityGraph(attributePaths = {"quiz", "quiz.lesson", "student", "student.studentProfile"})
    List<QuizAttempt> findByQuizLessonIdOrderByStartedAtDesc(Long lessonId);
}
