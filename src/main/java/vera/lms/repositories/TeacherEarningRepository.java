package vera.lms.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vera.lms.models.TeacherEarning;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface TeacherEarningRepository extends JpaRepository<TeacherEarning, Long> {
    boolean existsByBookingId(Long bookingId);

    @EntityGraph(attributePaths = {"booking", "booking.student", "booking.student.studentProfile", "booking.lesson"})
    List<TeacherEarning> findByTeacherIdOrderByEarnedAtDesc(Long teacherId);

    @Query("""
            SELECT COALESCE(SUM(e.amount), 0)
            FROM TeacherEarning e
            WHERE e.teacher.id = :teacherId
            """)
    BigDecimal sumByTeacherId(@Param("teacherId") Long teacherId);
}
