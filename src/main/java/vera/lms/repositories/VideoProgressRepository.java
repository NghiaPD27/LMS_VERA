package vera.lms.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vera.lms.models.VideoProgress;

import java.util.Optional;

@Repository
public interface VideoProgressRepository extends JpaRepository<VideoProgress, Long> {
    @EntityGraph(attributePaths = {"lessonVideo", "lessonVideo.lesson"})
    Optional<VideoProgress> findByStudentIdAndLessonVideoId(Long studentId, Long lessonVideoId);
}
