package vera.lms.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vera.lms.models.LessonVideo;

import java.util.Optional;

@Repository
public interface LessonVideoRepository extends JpaRepository<LessonVideo, Long> {
    Optional<LessonVideo> findByLessonId(Long lessonId);
    boolean existsByLessonId(Long lessonId);
}
