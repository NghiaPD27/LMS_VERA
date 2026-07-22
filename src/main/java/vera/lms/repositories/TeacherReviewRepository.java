package vera.lms.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vera.lms.enums.TeacherReviewResult;
import vera.lms.models.TeacherReview;

import java.util.Optional;

@Repository
public interface TeacherReviewRepository extends JpaRepository<TeacherReview, Long> {
    boolean existsByBookingId(Long bookingId);

    Optional<TeacherReview> findTopByBookingEnrollmentIdAndBookingLessonIdAndResultOrderByReviewedAtDesc(
            Long enrollmentId,
            Long lessonId,
            TeacherReviewResult result);
}
