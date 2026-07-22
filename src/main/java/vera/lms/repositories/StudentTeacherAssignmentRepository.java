package vera.lms.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vera.lms.models.StudentTeacherAssignment;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentTeacherAssignmentRepository extends JpaRepository<StudentTeacherAssignment, Long> {
    @EntityGraph(attributePaths = {"enrollment", "enrollment.student", "enrollment.student.studentProfile", "enrollment.program", "teacher", "teacher.teacherProfile"})
    Optional<StudentTeacherAssignment> findByEnrollmentId(Long enrollmentId);

    @EntityGraph(attributePaths = {"enrollment", "enrollment.student", "enrollment.student.studentProfile", "enrollment.program", "teacher"})
    List<StudentTeacherAssignment> findByTeacherId(Long teacherId);
}
