package vera.lms.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vera.lms.enums.PurchaseStatus;
import vera.lms.models.FinalAssessmentRetakePayment;

import java.util.List;
import java.util.Optional;

@Repository
public interface FinalAssessmentRetakePaymentRepository extends JpaRepository<FinalAssessmentRetakePayment, Long> {

    @EntityGraph(attributePaths = {"student", "student.studentProfile", "program", "enrollment"})
    Optional<FinalAssessmentRetakePayment> findByPaymentCode(String paymentCode);

    @EntityGraph(attributePaths = {"student", "student.studentProfile", "program", "enrollment"})
    List<FinalAssessmentRetakePayment> findByStudentIdOrderByIdDesc(Long studentId);

    @EntityGraph(attributePaths = {"student", "student.studentProfile", "program", "enrollment"})
    @Query("""
            SELECT p FROM FinalAssessmentRetakePayment p
            WHERE p.student.id = :studentId
            AND (:enrollmentId IS NULL OR p.enrollment.id = :enrollmentId)
            AND (:status IS NULL OR p.status = :status)
            ORDER BY p.id DESC
            """)
    List<FinalAssessmentRetakePayment> searchStudentPayments(
            @Param("studentId") Long studentId,
            @Param("enrollmentId") Long enrollmentId,
            @Param("status") PurchaseStatus status);

    boolean existsByEnrollmentIdAndStatus(Long enrollmentId, PurchaseStatus status);

    @EntityGraph(attributePaths = {"student", "student.studentProfile", "program", "enrollment"})
    @Query("""
            SELECT p FROM FinalAssessmentRetakePayment p
            WHERE p.enrollment.id = :enrollmentId
            AND p.status = vera.lms.enums.PurchaseStatus.PAID
            AND NOT EXISTS (
                SELECT participant.id FROM FinalAssessmentParticipant participant
                WHERE participant.retakePayment.id = p.id
            )
            ORDER BY p.paidAt ASC, p.id ASC
            """)
    List<FinalAssessmentRetakePayment> findUnusedPaidPayments(@Param("enrollmentId") Long enrollmentId);
}
