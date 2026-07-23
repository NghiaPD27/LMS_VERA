package vera.lms.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vera.lms.enums.BookingStatus;
import vera.lms.models.TeacherBooking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherBookingRepository extends JpaRepository<TeacherBooking, Long> {
    boolean existsByTeacherIdAndStartAtAndStatus(Long teacherId, Instant startAt, BookingStatus status);
    boolean existsByStudentIdAndLessonIdAndStatus(Long studentId, Long lessonId, BookingStatus status);
    long countByStatus(BookingStatus status);

    @EntityGraph(attributePaths = {"student", "student.studentProfile", "teacher", "teacher.teacherProfile", "lesson", "enrollment", "enrollment.program"})
    @Query("""
            SELECT b FROM TeacherBooking b
            WHERE b.student.id = :studentId
            AND (:lessonId IS NULL OR b.lesson.id = :lessonId)
            AND (:status IS NULL OR b.status = :status)
            ORDER BY b.startAt DESC
            """)
    List<TeacherBooking> findStudentBookings(
            @Param("studentId") Long studentId,
            @Param("lessonId") Long lessonId,
            @Param("status") BookingStatus status);

    @EntityGraph(attributePaths = {"student", "student.studentProfile", "teacher", "teacher.teacherProfile", "lesson", "enrollment", "enrollment.program"})
    @Query("SELECT b FROM TeacherBooking b WHERE b.id = :id")
    Optional<TeacherBooking> findWithDetailsById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"student", "student.studentProfile", "lesson", "enrollment", "enrollment.program"})
    List<TeacherBooking> findByTeacherIdOrderByStartAtDesc(Long teacherId);

    @EntityGraph(attributePaths = {"student", "student.studentProfile", "lesson", "enrollment", "enrollment.program"})
    List<TeacherBooking> findByTeacherIdAndStatusOrderByStartAtDesc(Long teacherId, BookingStatus status);

    @Query("""
            SELECT b FROM TeacherBooking b
            WHERE b.teacher.id = :teacherId
            AND b.status = :status
            AND b.startAt >= :from
            ORDER BY b.startAt ASC
            """)
    List<TeacherBooking> findTeacherBookingsFrom(
            @Param("teacherId") Long teacherId,
            @Param("status") BookingStatus status,
            @Param("from") Instant from);

    @EntityGraph(attributePaths = {"student", "student.studentProfile", "lesson"})
    @Query("""
            SELECT b FROM TeacherBooking b
            WHERE b.teacher.id = :teacherId
            AND b.status = :status
            AND (:from IS NULL OR b.endAt > :from)
            AND (:to IS NULL OR b.startAt < :to)
            ORDER BY b.startAt ASC
            """)
    List<TeacherBooking> findTeacherBookingsBetween(
            @Param("teacherId") Long teacherId,
            @Param("status") BookingStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
