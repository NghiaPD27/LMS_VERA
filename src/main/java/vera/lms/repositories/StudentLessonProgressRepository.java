package vera.lms.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
