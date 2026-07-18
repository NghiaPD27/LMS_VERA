package vera.lms.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vera.lms.enums.PurchaseStatus;
import vera.lms.models.CoursePurchase;

import java.util.List;
import java.util.Optional;

@Repository
public interface CoursePurchaseRepository extends JpaRepository<CoursePurchase, Long> {

    @EntityGraph(attributePaths = {"student", "student.studentProfile", "program", "enrollment"})
    List<CoursePurchase> findByStudentIdOrderByIdDesc(Long studentId);

    @EntityGraph(attributePaths = {"student", "student.studentProfile", "program", "enrollment"})
    Optional<CoursePurchase> findByIdAndStudentId(Long id, Long studentId);

    Optional<CoursePurchase> findByPaymentCode(String paymentCode);

    boolean existsByStudentIdAndProgramIdAndStatus(Long studentId, Long programId, PurchaseStatus status);

    @EntityGraph(attributePaths = {"student", "student.studentProfile", "program", "enrollment"})
    @Query("""
            SELECT p FROM CoursePurchase p
            WHERE (:studentId IS NULL OR p.student.id = :studentId)
            AND (:programId IS NULL OR p.program.id = :programId)
            AND (:status IS NULL OR p.status = :status)
            """)
    Page<CoursePurchase> searchAdminPurchases(
            @Param("studentId") Long studentId,
            @Param("programId") Long programId,
            @Param("status") PurchaseStatus status,
            Pageable pageable);
}
