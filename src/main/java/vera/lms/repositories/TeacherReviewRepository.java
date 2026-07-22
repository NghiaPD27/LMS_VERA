package vera.lms.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vera.lms.models.TeacherReview;

@Repository
public interface TeacherReviewRepository extends JpaRepository<TeacherReview, Long> {
    boolean existsByBookingId(Long bookingId);
}
