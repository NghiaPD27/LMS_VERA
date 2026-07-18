package vera.lms.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vera.lms.enums.LessonStatus;
import vera.lms.models.Lesson;

import java.util.List;
import java.util.Optional;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, Long> {
    List<Lesson> findByProgramIdAndStatusNotOrderByLessonNumberAsc(Long programId, LessonStatus status);
    List<Lesson> findByProgramIdAndStatusOrderByLessonNumberAsc(Long programId, LessonStatus status);
    Optional<Lesson> findByIdAndStatusNot(Long id, LessonStatus status);
    boolean existsByProgramIdAndLessonNumberAndStatusNot(Long programId, int lessonNumber, LessonStatus status);
    boolean existsByProgramIdAndLessonNumberAndStatusNotAndIdNot(Long programId, int lessonNumber, LessonStatus status, Long id);
}
