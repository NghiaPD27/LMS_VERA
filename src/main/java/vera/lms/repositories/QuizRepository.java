package vera.lms.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vera.lms.models.Quiz;

import java.util.Optional;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {
    Optional<Quiz> findByLessonId(Long lessonId);
    boolean existsByLessonId(Long lessonId);

    @Query("SELECT q FROM Quiz q WHERE q.id = :id")
    Optional<Quiz> findWithQuestionsById(@Param("id") Long id);
}
