package vera.lms.models;

import jakarta.persistence.*;
import lombok.*;
import vera.lms.enums.SepayWebhookEventStatus;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "sepay_webhook_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SepayWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sepay_transaction_id", nullable = false, unique = true)
    private Long sepayTransactionId;

    @Column(name = "payment_code", length = 50)
    private String paymentCode;

    @Column(name = "reference_code", length = 100)
    private String referenceCode;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Column(name = "transfer_amount", precision = 12, scale = 2)
    private BigDecimal transferAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SepayWebhookEventStatus status;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }
}
