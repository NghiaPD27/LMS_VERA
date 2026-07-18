package vera.lms.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vera.lms.enums.EnrollmentStatus;
import vera.lms.models.Enrollment;

import java.time.Instant;
import java.util.List;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByStudentId(Long studentId);
    List<Enrollment> findByProgramIdAndStatus(Long programId, EnrollmentStatus status);
    boolean existsByStudentIdAndStatus(Long studentId, EnrollmentStatus status);
    boolean existsByStudentIdAndProgramIdAndStatus(Long studentId, Long programId, EnrollmentStatus status);

    @Query("""
            SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END
            FROM Enrollment e
            WHERE e.student.id = :studentId
            AND e.program.id = :programId
            AND e.status = :status
            AND (e.expiredAt IS NULL OR e.expiredAt >= :now)
            """)
    boolean existsAccessibleEnrollment(
            @Param("studentId") Long studentId,
            @Param("programId") Long programId,
            @Param("status") EnrollmentStatus status,
            @Param("now") Instant now);

    @EntityGraph(attributePaths = {"student", "student.studentProfile", "program"})
    List<Enrollment> findByStudentIdOrderByIdDesc(Long studentId);

    @EntityGraph(attributePaths = {"student", "student.studentProfile", "program"})
    @Query("""
            SELECT e FROM Enrollment e
            WHERE (:studentId IS NULL OR e.student.id = :studentId)
            AND (:programId IS NULL OR e.program.id = :programId)
            AND (:status IS NULL OR e.status = :status)
            """)
    Page<Enrollment> searchAdminEnrollments(
            @Param("studentId") Long studentId,
            @Param("programId") Long programId,
            @Param("status") EnrollmentStatus status,
            Pageable pageable);
}
