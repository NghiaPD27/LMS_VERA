package vera.lms.models;

import jakarta.persistence.*;
import lombok.*;
import vera.lms.enums.PurchaseStatus;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "final_assessment_retake_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinalAssessmentRetakePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "payment_code", unique = true, length = 50)
    private String paymentCode;

    @Column(name = "payment_qr_url", length = 1000)
    private String paymentQrUrl;

    @Builder.Default
    @Column(name = "payment_provider", nullable = false, length = 30)
    private String paymentProvider = "SEPAY";

    @Column(name = "provider_transaction_id", unique = true, length = 50)
    private String providerTransactionId;

    @Column(name = "provider_reference_code", length = 100)
    private String providerReferenceCode;

    @Column(name = "payment_content", length = 255)
    private String paymentContent;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private PurchaseStatus status = PurchaseStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
